package com.example.myapplication.data.runtime

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import com.example.myapplication.util.AssetUnpacker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PrefixInstaller(context: Context, private val node: NodeRuntime) {

    private val app = context.applicationContext
    private val libDir: File get() = File(node.prefix, "lib")
    private val binDir: File get() = File(node.prefix, "bin")
    private val npmDir: File get() = File(node.prefix, "lib/node_modules/npm")
    private val stamp: File get() = File(node.prefix, STAMP_FILE)

    val isInstalled: Boolean
        get() = stamp.isFile && stamp.readText().trim() == version() &&
            nodeLink.exists() && node.npmCli.isFile

    val nodeLink: File get() = File(binDir, "node")

    private fun version(): String =
        listOf(LIBS_ASSET, NPM_ASSET).joinToString("-") { AssetUnpacker.size(app, it) }

    suspend fun install(
        force: Boolean = false,
        onProgress: (String) -> Unit = {},
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (!force && isInstalled) return@runCatching "prefix already installed"

            node.prepare()
            if (libDir.exists()) {
                onProgress("clearing $libDir")
                libDir.deleteRecursively()
            }
            libDir.mkdirs()
            binDir.mkdirs()

            onProgress("unpacking $LIBS_ASSET")
            val stats = AssetUnpacker.unzip(
                context = app,
                asset = LIBS_ASSET,
                dest = libDir,
                onProgress = onProgress,
                executable = { it.name.contains(".so") },
            )

            onProgress("unpacking $NPM_ASSET")
            if (npmDir.exists()) npmDir.deleteRecursively()
            val npmStats = AssetUnpacker.unzip(
                context = app,
                asset = NPM_ASSET,
                dest = npmDir,
                onProgress = onProgress,
            )

            onProgress("linking $nodeLink -> ${node.binary.name}")
            linkNode()

            stamp.writeText(version())
            "prefix ready: ${stats.describe()}; npm: ${npmStats.describe()}"
        }
    }

    private fun linkNode() {
        if (nodeLink.exists() || isSymlink(nodeLink)) nodeLink.delete()
        try {
            Os.symlink(node.binary.absolutePath, nodeLink.absolutePath)
        } catch (e: ErrnoException) {
            node.binary.copyTo(nodeLink, overwrite = true)
        }
        nodeLink.setExecutable(true, false)
    }

    private fun isSymlink(f: File): Boolean =
        runCatching { f.canonicalFile != f.absoluteFile }.getOrDefault(false)

    fun diagnostics(): String = buildString {
        appendLine("prefix     ${node.prefix.absolutePath}")
        appendLine("installed  $isInstalled")
        val libs = libDir.listFiles()?.size ?: 0
        appendLine("libs       $libs in ${libDir.absolutePath}")
        appendLine("node link  ${if (nodeLink.exists()) "ok" else "missing"} (${nodeLink.absolutePath})")
        appendLine("npm        ${if (node.npmCli.isFile) "ok" else "missing"} (${node.npmCli.absolutePath})")
    }

    companion object {
        const val LIBS_ASSET = "node-libs.zip"
        const val NPM_ASSET = "npm.zip"
        private const val STAMP_FILE = ".glass-prefix-version"
    }
}
