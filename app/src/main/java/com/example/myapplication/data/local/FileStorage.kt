package com.example.myapplication.data.local

import android.content.Context
import java.io.File

class FileStorage(context: Context) {

    val root: File = File(context.applicationContext.filesDir, "projects").canonicalFile

    fun list(dir: File): List<File> {
        requireInside(dir)
        dir.mkdirs()
        return dir.listFiles().orEmpty().toList()
    }

    fun exists(dir: File, name: String): Boolean = File(dir, name).exists()

    fun createFolder(dir: File, name: String): File {
        requireInside(dir)
        val target = File(dir, name)
        check(target.mkdirs()) { "could not create ${target.absolutePath}" }
        return target
    }

    fun rename(file: File, name: String): Boolean {
        requireInside(file)
        require(file != root) { "cannot rename the root" }
        return file.renameTo(File(file.parentFile, name))
    }

    fun delete(file: File): Boolean {
        requireInside(file)
        require(file != root) { "cannot delete the root" }
        return file.deleteRecursively()
    }

    fun isRoot(dir: File): Boolean = dir.canonicalFile == root

    private fun requireInside(file: File) {
        val path = file.canonicalPath
        require(path == root.path || path.startsWith(root.path + File.separator)) {
            "$path is outside ${root.path}"
        }
    }
}
