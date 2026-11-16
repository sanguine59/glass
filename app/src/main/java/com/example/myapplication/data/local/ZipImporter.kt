package com.example.myapplication.data.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.myapplication.util.AssetUnpacker
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

class ZipImporter(context: Context) {

    private val app = context.applicationContext

    fun displayName(uri: Uri): String? =
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    fun extract(uri: Uri, dest: File): Int {
        val input = app.contentResolver.openInputStream(uri) ?: error("Could not open the file")
        var entries = 0
        var files = 0
        var bytes = 0L

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                if (isSkipped(entry.name)) {
                    zip.closeEntry()
                    continue
                }
                val target = with(AssetUnpacker) { dest.resolveSafely(entry.name) }
                    ?: throw IllegalArgumentException("Zip entry escapes the folder: ${entry.name}")

                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().buffered().use { out ->
                        bytes += copyLimited(zip, out, MAX_EXTRACTED_BYTES - bytes)
                    }
                    files++
                }
                zip.closeEntry()
            }
        }

        require(entries > 0) { "Not a zip file, or the zip is empty" }
        return files
    }

    private fun copyLimited(input: InputStream, output: OutputStream, remaining: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return copied
            copied += read
            if (copied > remaining) {
                throw IllegalStateException("Zip is larger than ${MAX_EXTRACTED_BYTES / 1024 / 1024} MB when extracted")
            }
            output.write(buffer, 0, read)
        }
    }

    private fun isSkipped(name: String): Boolean {
        val segments = name.split('/').filter { it.isNotEmpty() }
        return segments.firstOrNull() == "__MACOSX" ||
            segments.lastOrNull() == ".DS_Store" ||
            "node_modules" in segments
    }

    private companion object {
        const val MAX_EXTRACTED_BYTES = 512L * 1024 * 1024
    }
}
