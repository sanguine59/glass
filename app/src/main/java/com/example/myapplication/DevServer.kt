package com.example.myapplication

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

class DevServer(
    private val node: NodeRuntime,
    private val scope: CoroutineScope,
    private val onLog: (String) -> Unit,
) {
    private var process: Process? = null
    private var pump: Job? = null

    val isRunning: Boolean get() = process?.isAlive == true

    val url: String get() = "http://$HOST:$PORT"

    suspend fun start(project: File = node.workspace): Result<String> {
        if (isRunning) return Result.success("already running at $url")

        val entry = node.viteEntry(project)
        if (!entry.isFile) {
            return Result.failure(IllegalStateException("vite not found at ${entry.absolutePath}"))
        }

        val esbuild = node.esbuildBinary(project)
        if (esbuild.isFile && !esbuild.canExecute()) {
            return Result.failure(
                IllegalStateException("esbuild is present but not executable: ${esbuild.absolutePath}")
            )
        }

        val p = try {
            node.start(listOf(entry.absolutePath, "--host", HOST, "--port", PORT.toString()), project)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        process = p

        pump = scope.launch(Dispatchers.IO) {
            runCatching {
                p.inputStream.bufferedReader().forEachLine { onLog(it) }
            }
            onLog("[vite exited: ${runCatching { p.exitValue() }.getOrNull()}]")
        }

        return awaitReady(p)
    }

    private suspend fun awaitReady(p: Process, timeoutMs: Long = 90_000): Result<String> {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!p.isAlive) {
                return Result.failure(
                    IllegalStateException("vite exited with ${runCatching { p.exitValue() }.getOrNull()} before binding; see log")
                )
            }
            if (canConnect()) return Result.success("listening on $url")
            delay(300)
        }
        return Result.failure(IllegalStateException("timed out waiting for $url"))
    }

    private suspend fun canConnect(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { it.connect(InetSocketAddress(HOST, PORT), 500) }
            true
        }.getOrDefault(false)
    }

    fun stop() {
        process?.destroy()
        scope.launch(Dispatchers.IO) {
            delay(2_000)
            process?.takeIf { it.isAlive }?.destroyForcibly()
        }
        pump?.cancel()
        process = null
        pump = null
    }

    companion object {
        const val HOST = "127.0.0.1"
        const val PORT = 5173
    }
}
