package com.example.myapplication

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WorkspaceInstaller(context: Context, private val node: NodeRuntime) {

    private val app = context.applicationContext
    private val stamp: File get() = File(node.workspace, STAMP_FILE)

    val isInstalled: Boolean
        get() = stamp.isFile && stamp.readText().trim() == templateVersion()

    private fun templateVersion(): String =
        runCatching {
            app.assets.openFd(TEMPLATE_ASSET).use { it.length.toString() }
        }.getOrElse {
            runCatching {
                app.assets.open(TEMPLATE_ASSET).use { input ->
                    var total = 0L
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                    }
                    total.toString()
                }
            }.getOrDefault("unknown")
        }

    suspend fun install(
        force: Boolean = false,
        onProgress: (String) -> Unit = {},
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!force && isInstalled) return@runCatching "already installed"

            node.prepare()
            if (node.workspace.exists()) {
                onProgress("clearing old workspace")
                node.workspace.deleteRecursively()
            }
            node.workspace.mkdirs()

            onProgress("unpacking $TEMPLATE_ASSET")
            val stats = AssetUnpacker.unzip(
                context = app,
                asset = TEMPLATE_ASSET,
                dest = node.workspace,
                onProgress = onProgress,
                executable = { f ->
                    val p = f.absolutePath
                    p.contains("/node_modules/@esbuild/") || p.contains("/node_modules/.bin/")
                },
            )

            stamp.writeText(templateVersion())
            "workspace ready: ${stats.describe()}"
        }
    }

    companion object {
        const val TEMPLATE_ASSET = "template.zip"
        private const val STAMP_FILE = ".glass-template-version"
    }
}
