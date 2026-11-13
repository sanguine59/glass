package com.example.myapplication.data.local

import android.content.Context
import java.io.File

class FolderStorage(context: Context) {

    val root: File = File(context.applicationContext.filesDir, "projects")

    fun list(): List<File> {
        root.mkdirs()
        return root.listFiles { file -> file.isDirectory }
            .orEmpty()
            .sortedBy { it.name.lowercase() }
    }

    fun create(name: String): File {
        val dir = File(root, name)
        check(dir.mkdirs()) { "could not create ${dir.absolutePath}" }
        return dir
    }

    fun exists(name: String): Boolean = File(root, name).exists()
}
