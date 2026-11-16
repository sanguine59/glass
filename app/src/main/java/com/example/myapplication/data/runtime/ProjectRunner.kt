package com.example.myapplication.data.runtime

import com.example.myapplication.data.model.RunState
import com.example.myapplication.data.model.isActive
import com.example.myapplication.util.DeviceNet
import com.example.myapplication.util.ProcessTree
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

class ProjectRunner(
    private val node: NodeRuntime,
    private val prefix: PrefixInstaller,
    private val shebangFixer: ShebangFixer,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<RunState>(RunState.Idle)
    val state: StateFlow<RunState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var job: Job? = null

    @Volatile private var process: Process? = null
    @Volatile private var stopping = false

    fun run(project: File) {
        if (job?.isActive == true) return
        stopping = false
        _logs.value = emptyList()
        job = scope.launch(Dispatchers.IO) {
            try {
                pipeline(project)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!stopping) fail(project, e.message ?: e.javaClass.simpleName)
            } finally {
                if (stopping) {
                    log("[stopped]")
                    _state.value = RunState.Idle
                }
            }
        }
    }

    fun stop() {
        if (!_state.value.isActive) return
        stopping = true
        val current = _state.value
        if (current is RunState.Running) _state.value = current.copy(step = "stopping")
        if (current is RunState.Serving) _state.value = RunState.Running(current.project, "stopping")
        val p = process ?: return
        scope.launch(Dispatchers.IO) {
            val pids = ProcessTree.pidOf(p)?.let(ProcessTree::withDescendants).orEmpty()
            if (pids.isEmpty()) p.destroy() else ProcessTree.signal(pids, ProcessTree.SIGTERM)
            delay(1_500)
            if (pids.isEmpty()) p.destroyForcibly() else ProcessTree.signal(pids, ProcessTree.SIGKILL)
        }
    }

    private suspend fun pipeline(project: File) {
        val name = project.name

        if (!prefix.isInstalled) {
            step(name, "setting up node")
            log("> installing the node runtime (first run only)")
            prefix.install { log(it) }.getOrThrow()
            if (stopping) return
        }

        val scripts = readScripts(project)
        val script = listOf("dev", "start").firstOrNull { it in scripts }
            ?: return fail(project, "package.json has no \"dev\" or \"start\" script")

        step(name, "installing")
        val hadModules = File(project, "node_modules").isDirectory
        val install = exec(project, listOf(node.npmCli.absolutePath, "install", "--no-audit", "--no-fund"), "$ npm install")
        if (stopping) return
        if (install != 0) {
            if (!hadModules) return fail(project, "npm install failed (exit $install)")
            log("npm install failed (exit $install), continuing with the existing node_modules")
        }
        val fixed = shebangFixer.fix(project, prefix.nodeLink)
        if (fixed > 0) log("patched $fixed script(s) in node_modules/.bin to use the bundled node")

        val tsc = node.tscEntry(project)
        if (File(project, "tsconfig.json").isFile && tsc.isFile) {
            step(name, "typechecking")
            val code = exec(project, listOf(tsc.absolutePath, "--noEmit", "--pretty", "false"), "$ tsc --noEmit")
            if (stopping) return
            if (code != 0) return fail(project, "typecheck failed (exit $code)")
            log("typecheck passed")
        } else {
            log("skipping typecheck (no tsconfig.json or typescript)")
        }

        step(name, "running")
        val command = scripts.getValue(script)
        val extra = if ("vite" in command) listOf("--", "--host", "0.0.0.0") else emptyList()
        val code = exec(project, listOf(node.npmCli.absolutePath, "run", script) + extra, "$ npm run $script") { line ->
            detectServer(name, line)
        }
        if (!stopping) fail(project, "npm run $script exited (exit $code)")
    }

    private fun exec(
        project: File,
        args: List<String>,
        header: String,
        onLine: (String) -> Unit = {},
    ): Int {
        if (stopping) return -1
        log(header)
        val p = node.start(args, project)
        process = p
        if (stopping) p.destroy()
        try {
            p.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    val line = clean(raw)
                    log(line)
                    onLine(line)
                }
            }
            return p.waitFor()
        } finally {
            process = null
        }
    }

    private fun detectServer(project: String, line: String) {
        if (_state.value is RunState.Serving) return
        val url = LOCAL_URL.find(line)?.groupValues?.get(1) ?: return
        val port = PORT.find(url)?.groupValues?.get(1)
        val lanUrl = port?.let { p -> DeviceNet.primary()?.let { "http://$it:$p/" } }
        _state.value = RunState.Serving(project, url, lanUrl)
        lanUrl?.let { log("lan: $it") }
    }

    private fun readScripts(project: File): Map<String, String> {
        val pkg = File(project, "package.json")
        require(pkg.isFile) { "no package.json in ${project.name}" }
        val scripts = JSONObject(pkg.readText()).optJSONObject("scripts") ?: return emptyMap()
        return scripts.keys().asSequence().associateWith { scripts.optString(it) }
    }

    private fun step(project: String, step: String) {
        _state.value = RunState.Running(project, step)
    }

    private fun fail(project: File, message: String) {
        log("error: $message")
        _state.value = RunState.Failed(project.name, message)
    }

    private fun log(line: String) {
        _logs.update { current ->
            val next = current + line
            if (next.size > MAX_LINES) next.takeLast(MAX_LINES) else next
        }
    }

    private fun clean(raw: String): String =
        ANSI.replace(raw, "").let { line -> line.substringAfterLast('\r').ifEmpty { line.trimEnd('\r') } }

    private companion object {
        const val MAX_LINES = 3_000
        val ANSI = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")
        val LOCAL_URL = Regex("""Local:\s+(https?://\S+)""")
        val PORT = Regex(""":(\d+)""")
    }
}
