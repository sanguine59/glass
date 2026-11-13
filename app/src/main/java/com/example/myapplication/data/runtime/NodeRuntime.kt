package com.example.myapplication.data.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.util.concurrent.TimeUnit

class NodeRuntime(context: Context) {

    private val app = context.applicationContext

    val binary: File = File(app.applicationInfo.nativeLibraryDir, LIB_NAME)

    val prefix: File = File(app.filesDir, "usr")
    val home: File = File(app.filesDir, "home")
    val tmp: File = File(app.filesDir, "tmp")
    val workspace: File = File(app.filesDir, "workspace")

    val opensslConf: File = File(prefix, "etc/tls/openssl.cnf")

    val isPresent: Boolean get() = binary.isFile
    val isExecutable: Boolean get() = binary.canExecute()

    fun prepare() {
        listOf(prefix, home, tmp, workspace, File(home, ".npm")).forEach { it.mkdirs() }
        if (!opensslConf.isFile) {
            opensslConf.parentFile?.mkdirs()
            opensslConf.writeText(OPENSSL_CONF_CONTENTS)
        }
    }

    fun environment(): Map<String, String> = buildMap {
        put("HOME", home.absolutePath)
        put("TMPDIR", tmp.absolutePath)
        put("TMP", tmp.absolutePath)
        put("PREFIX", prefix.absolutePath)
        put(
            "PATH",
            listOf(
                File(prefix, "bin").absolutePath,
                binary.parent,
                "/system/bin",
            ).joinToString(":"),
        )
        put("LANG", "en_US.UTF-8")
        put("SHELL", ANDROID_SHELL)

        put("LD_LIBRARY_PATH", File(prefix, "lib").absolutePath)
        put("OPENSSL_CONF", opensslConf.absolutePath)
        put("npm_config_script_shell", ANDROID_SHELL)
        put("npm_config_cache", File(home, ".npm").absolutePath)
        put("npm_config_prefix", prefix.absolutePath)

        esbuildBinary().takeIf { it.isFile }?.let { put("ESBUILD_BINARY_PATH", it.absolutePath) }
    }

    val npmCli: File = File(prefix, "lib/node_modules/npm/bin/npm-cli.js")

    fun tscEntry(project: File = workspace): File =
        File(project, "node_modules/typescript/bin/tsc")

    fun viteEntry(project: File = workspace): File =
        File(project, "node_modules/vite/bin/vite.js")

    fun esbuildBinary(project: File = workspace): File =
        File(project, "node_modules/@esbuild/android-arm64/bin/esbuild")

    suspend fun npm(
        args: List<String>,
        project: File = workspace,
        timeoutSeconds: Long = 600,
    ): ExecResult {
        if (!npmCli.isFile) {
            return ExecResult(null, "npm not found at ${npmCli.absolutePath}", false)
        }
        return exec(listOf(npmCli.absolutePath) + args, cwd = project, timeoutSeconds)
    }

    suspend fun npmInstall(
        vararg packages: String,
        project: File = workspace,
        timeoutSeconds: Long = 600,
    ): ExecResult = npm(
        listOf("install") + packages + listOf("--no-audit", "--no-fund"),
        project,
        timeoutSeconds,
    )

    suspend fun typecheck(project: File = workspace, timeoutSeconds: Long = 180): ExecResult {
        val tsc = tscEntry(project)
        if (!tsc.isFile) {
            return ExecResult(null, "typescript not found at ${tsc.absolutePath}", false)
        }
        return exec(listOf(tsc.absolutePath, "--noEmit"), cwd = project, timeoutSeconds)
    }

    fun start(args: List<String>, cwd: File = workspace): Process {
        prepare()
        if (!cwd.isDirectory) cwd.mkdirs()
        return ProcessBuilder(listOf(binary.absolutePath) + args)
            .directory(cwd)
            .redirectErrorStream(true)
            .apply { environment().putAll(this@NodeRuntime.environment()) }
            .start()
    }

    suspend fun exec(
        args: List<String>,
        cwd: File = workspace,
        timeoutSeconds: Long = 30,
    ): ExecResult = withContext(Dispatchers.IO) {
        val process = try {
            start(args, cwd)
        } catch (e: Exception) {
            return@withContext ExecResult(
                exitCode = null,
                output = "failed to spawn: ${e.javaClass.simpleName}: ${e.message}",
                timedOut = false,
            )
        }

        val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return@withContext ExecResult(null, output, timedOut = true)
        }
        ExecResult(process.exitValue(), output, timedOut = false)
    }

    suspend fun eval(script: String, timeoutSeconds: Long = 30): ExecResult =
        exec(listOf("-e", script), timeoutSeconds = timeoutSeconds)

    fun diagnostics(): String = buildString {
        appendLine("binary     ${binary.absolutePath}")
        appendLine("present    $isPresent")
        appendLine("executable $isExecutable")
        if (isPresent) appendLine("size       ${binary.length() / 1_048_576} MiB")
        appendLine("abi        ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("workspace  ${workspace.absolutePath}")
        appendLine("ssl conf   ${if (opensslConf.isFile) "ok" else "missing"} (${opensslConf.absolutePath})")
    }

    data class ExecResult(
        val exitCode: Int?,
        val output: String,
        val timedOut: Boolean,
    ) {
        val ok: Boolean get() = exitCode == 0
        fun describe(): String = when {
            timedOut -> "[timed out]\n$output"
            exitCode == null -> output
            else -> "[exit $exitCode]\n$output"
        }
    }

    companion object {
        const val LIB_NAME = "libnode.so"
        const val ANDROID_SHELL = "/system/bin/sh"

        private val OPENSSL_CONF_CONTENTS = """
            openssl_conf = openssl_init

            [openssl_init]
            providers = provider_sect

            [provider_sect]
            default = default_sect

            [default_sect]
            activate = 1
        """.trimIndent() + "\n"
    }
}
