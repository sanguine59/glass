package com.example.myapplication.data.runtime

import android.content.Context
import com.example.myapplication.util.AssetUnpacker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WorkspaceInstaller(context: Context, private val node: NodeRuntime) {

    private val app = context.applicationContext
    private val stamp: File get() = File(node.workspace, STAMP_FILE)

    val isInstalled: Boolean
        get() = stamp.isFile && stamp.readText().trim() == templateVersion()

    private fun templateVersion(): String = AssetUnpacker.size(app, TEMPLATE_ASSET)

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
