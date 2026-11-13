package com.example.myapplication.data.repository

import com.example.myapplication.data.local.FolderStorage
import com.example.myapplication.data.model.Folder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class FolderRepository(private val storage: FolderStorage) {

    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    private val writeLock = Mutex()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _folders.value = storage.list().map { Folder(name = it.name, path = it.absolutePath) }
    }

    suspend fun add() = writeLock.withLock {
        withContext(Dispatchers.IO) {
            storage.create(nextName())
        }
        refresh()
    }

    private fun nextName(): String =
        generateSequence(1) { it + 1 }
            .map { "Folder $it" }
            .first { !storage.exists(it) }
}
