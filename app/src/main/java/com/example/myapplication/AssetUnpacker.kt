package com.example.myapplication

import android.content.Context
import java.io.File
import java.util.zip.ZipInputStream

object AssetUnpacker {

    data class Stats(val files: Int, val bytes: Long, val executablesFixed: Int) {
        fun describe() = "$files files, ${bytes / 1_048_576} MiB, chmod +x on $executablesFixed"
    }

    fun unzip(
        context: Context,
        asset: String,
        dest: File,
        onProgress: (String) -> Unit = {},
        executable: (File) -> Boolean = { false },
    ): Stats {
        dest.mkdirs()
        var files = 0
        var bytes = 0L
        var fixed = 0

        context.assets.open(asset).use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val target = dest.resolveSafely(entry.name)
                        ?: throw SecurityException("zip entry escapes $dest: ${entry.name}")

                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().buffered().use { out -> bytes += zip.copyTo(out) }
                        files++
                        if (executable(target) && target.setExecutable(true, false)) fixed++
                        if (files % 500 == 0) onProgress("unpacked $files files")
                    }
                    zip.closeEntry()
                }
            }
        }
        return Stats(files, bytes, fixed)
    }

    fun File.resolveSafely(entryName: String): File? {
        val base = canonicalFile
        val target = File(base, entryName).canonicalFile
        return target.takeIf {
            it.path == base.path || it.path.startsWith(base.path + File.separator)
        }
    }
}
