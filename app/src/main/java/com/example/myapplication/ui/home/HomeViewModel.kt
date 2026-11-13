package com.example.myapplication.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.FileStorage
import com.example.myapplication.data.model.FileEntry
import com.example.myapplication.data.repository.FileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FileRepository(FileStorage(application))

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var currentDir: File = repository.root
    private var loadJob: Job? = null

    init {
        load(repository.root)
    }

    fun onEntryClick(entry: FileEntry) {
        if (!entry.isDirectory) return
        load(File(entry.path))
    }

    fun onEntryLongClick(entry: FileEntry) {
        _uiState.update { it.copy(dialog = HomeDialog.Actions(entry)) }
    }

    fun navigateUp() {
        if (repository.isRoot(currentDir)) return
        load(currentDir.parentFile ?: repository.root)
    }

    fun addFolder() {
        val dir = currentDir
        viewModelScope.launch {
            repository.createFolder(dir)
            load(dir)
        }
    }

    fun showRename(entry: FileEntry) {
        _uiState.update { it.copy(dialog = HomeDialog.Rename(entry)) }
    }

    fun showDelete(entry: FileEntry) {
        _uiState.update { it.copy(dialog = HomeDialog.Delete(entry)) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialog = null) }
    }

    fun rename(entry: FileEntry, newName: String) {
        viewModelScope.launch {
            repository.rename(entry, newName).fold(
                onSuccess = {
                    dismissDialog()
                    load(currentDir)
                },
                onFailure = { error ->
                    _uiState.update { it.copy(dialog = HomeDialog.Rename(entry, error.message)) }
                },
            )
        }
    }

    fun delete(entry: FileEntry) {
        viewModelScope.launch {
            repository.delete(entry).fold(
                onSuccess = {
                    dismissDialog()
                    load(currentDir)
                },
                onFailure = { error ->
                    _uiState.update { it.copy(dialog = HomeDialog.Delete(entry, error.message)) }
                },
            )
        }
    }

    private fun load(dir: File) {
        currentDir = dir
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val entries = repository.list(dir)
            val isRoot = repository.isRoot(dir)
            _uiState.update {
                it.copy(
                    title = if (isRoot) "glass" else dir.relativeTo(repository.root).path,
                    isRoot = isRoot,
                    entries = entries,
                )
            }
        }
    }
}
