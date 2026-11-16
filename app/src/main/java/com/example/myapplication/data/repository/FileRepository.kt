package com.example.myapplication.data.repository

import com.example.myapplication.data.local.FileStorage
import com.example.myapplication.data.model.CodeLine
import com.example.myapplication.data.model.FileContent
import com.example.myapplication.data.model.FileEntry
import com.example.myapplication.util.TextSniffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class FileRepository(private val storage: FileStorage) {

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

    suspend fun open(path: String): Result<FileContent> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            val size = storage.size(file)
            when {
                !TextSniffer.isText(storage.read(file, SAMPLE_BYTES)) -> FileContent.Binary
                size > MAX_TEXT_BYTES -> FileContent.TooLarge(size)
                else -> FileContent.Text(toLines(storage.read(file, MAX_TEXT_BYTES).decodeToString()))
            }
        }
    }

    private fun toLines(text: String): List<CodeLine> {
        val body = text.removePrefix("\uFEFF")
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
        const val MAX_ROW_CHARS = 1000
    }
}
