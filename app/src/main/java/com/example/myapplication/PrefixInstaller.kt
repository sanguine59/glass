package com.example.myapplication

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PrefixInstaller(context: Context, private val node: NodeRuntime) {

    private val app = context.applicationContext
    private val libDir: File get() = File(node.prefix, "lib")
    private val binDir: File get() = File(node.prefix, "bin")
    private val stamp: File get() = File(node.prefix, STAMP_FILE)

    val isInstalled: Boolean
        get() = stamp.isFile && stamp.readText().trim() == version() && nodeLink.exists()

    val nodeLink: File get() = File(binDir, "node")

    private fun version(): String = runCatching {
        app.assets.open(LIBS_ASSET).use { input ->
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

            onProgress("linking $nodeLink -> ${node.binary.name}")
            linkNode()

            stamp.writeText(version())
            "prefix ready: ${stats.describe()}"
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
    }

    companion object {
        const val LIBS_ASSET = "node-libs.zip"
        private const val STAMP_FILE = ".glass-prefix-version"
    }
}
