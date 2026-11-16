package com.example.myapplication.data.repository

import android.net.Uri
import com.example.myapplication.data.local.FileStorage
import com.example.myapplication.data.local.ZipImporter
import com.example.myapplication.data.model.CodeLine
import com.example.myapplication.data.model.FileContent
import com.example.myapplication.data.model.FileEntry
import com.example.myapplication.data.model.TextFormat
import com.example.myapplication.util.TextSniffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class FileRepository(
    private val storage: FileStorage,
    private val zipImporter: ZipImporter,
) {

    private val writeLock = Mutex()

    val root: File get() = storage.root

    fun isRoot(dir: File): Boolean = storage.isRoot(dir)

    suspend fun list(dir: File): List<FileEntry> = withContext(Dispatchers.IO) {
        storage.list(dir)
            .map { FileEntry(name = it.name, path = it.absolutePath, isDirectory = it.isDirectory) }
            .sortedWith(compareBy<FileEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    suspend fun createFolder(dir: File): Unit = writeLock.withLock {
        withContext(Dispatchers.IO) {
            val name = generateSequence(1) { it + 1 }
                .map { "Folder $it" }
                .first { !storage.exists(dir, it) }
            storage.createFolder(dir, name)
        }
    }

    suspend fun rename(entry: FileEntry, newName: String): Result<Unit> = writeLock.withLock {
        withContext(Dispatchers.IO) {
            val name = newName.trim()
            val file = File(entry.path)
            when {
                name.isEmpty() -> Result.failure(IllegalArgumentException("Name can't be empty"))
                name == "." || name == ".." -> Result.failure(IllegalArgumentException("Invalid name"))
                name.contains('/') || name.contains('\u0000') ->
                    Result.failure(IllegalArgumentException("Name can't contain /"))
                name == entry.name -> Result.success(Unit)
                storage.exists(file.parentFile!!, name) ->
                    Result.failure(IllegalArgumentException("\"$name\" already exists"))
                !storage.rename(file, name) -> Result.failure(IllegalStateException("Rename failed"))
                else -> Result.success(Unit)
            }
        }
    }

    suspend fun delete(entry: FileEntry): Result<Unit> = writeLock.withLock {
        withContext(Dispatchers.IO) {
            if (storage.delete(File(entry.path))) Result.success(Unit)
            else Result.failure(IllegalStateException("Delete failed"))
        }
    }

    suspend fun importZip(uri: Uri, dir: File): Result<String> = writeLock.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val staging = storage.newStagingDir()
                try {
                    zipImporter.extract(uri, staging)
                    val content = staging.listFiles().orEmpty().singleOrNull()?.takeIf { it.isDirectory } ?: staging
                    val name = uniqueName(dir, importName(zipImporter.displayName(uri)))
                    storage.moveInto(content, dir, name)
                    name
                } finally {
                    staging.deleteRecursively()
                }
            }
        }
    }

    private fun importName(displayName: String?): String {
        val base = displayName.orEmpty()
            .let { if (it.endsWith(".zip", ignoreCase = true)) it.dropLast(4) else it }
            .replace('/', '_')
            .replace('\u0000', '_')
            .trim()
        return if (base.isEmpty() || base == "." || base == "..") "imported" else base
    }

    private fun uniqueName(dir: File, base: String): String =
        generateSequence(1) { it + 1 }
            .map { if (it == 1) base else "$base ($it)" }
            .first { !storage.exists(dir, it) }

    suspend fun findProjectRoot(path: String): File? = withContext(Dispatchers.IO) {
        generateSequence(File(path).parentFile) { dir -> dir.parentFile.takeUnless { storage.isRoot(dir) } }
            .takeWhile { storage.contains(it) }
            .filterNot { dir -> "node_modules" in dir.relativeTo(storage.root).invariantSeparatorsPath.split('/') }
            .firstOrNull { File(it, "package.json").isFile }
    }

    suspend fun open(path: String): Result<FileContent> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            val size = storage.size(file)
            when {
                !TextSniffer.isText(storage.read(file, SAMPLE_BYTES)) -> FileContent.Binary
                size > MAX_TEXT_BYTES -> FileContent.TooLarge(size)
                size > MAX_EDITABLE_BYTES -> FileContent.Text(toLines(storage.read(file, MAX_TEXT_BYTES).decodeToString()))
                else -> editable(storage.read(file, MAX_EDITABLE_BYTES).decodeToString())
            }
        }
    }

    suspend fun save(path: String, text: String, format: TextFormat): Result<Unit> = writeLock.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val body = if (format.crlf) text.replace("\n", "\r\n") else text
                storage.write(File(path), ((if (format.bom) BOM else "") + body).toByteArray())
            }
        }
    }

    private fun editable(raw: String): FileContent.Editable {
        val bom = raw.startsWith(BOM)
        val body = raw.removePrefix(BOM)
        val crlf = "\r\n" in body
        val text = if (crlf) body.replace("\r\n", "\n") else body
        return FileContent.Editable(text, TextFormat(crlf = crlf, bom = bom))
    }

    private fun toLines(text: String): List<CodeLine> {
        val body = text.removePrefix(BOM)
        if (body.isEmpty()) return emptyList()
        val lines = body.split('\n').toMutableList()
        if (lines.size > 1 && lines.last().isEmpty()) lines.removeAt(lines.lastIndex)
        return lines.flatMapIndexed { index, raw ->
            val line = raw.removeSuffix("\r").replace("\t", "    ")
            if (line.length <= MAX_ROW_CHARS) {
                listOf(CodeLine(index + 1, line))
            } else {
                line.chunked(MAX_ROW_CHARS).mapIndexed { chunk, part ->
                    CodeLine(if (chunk == 0) index + 1 else null, part)
                }
            }
        }
    }

    private companion object {
        const val SAMPLE_BYTES = 8 * 1024
        const val MAX_TEXT_BYTES = 2 * 1024 * 1024
        const val MAX_EDITABLE_BYTES = 512 * 1024
        const val BOM = "\uFEFF"
        const val MAX_ROW_CHARS = 1000
    }
}
