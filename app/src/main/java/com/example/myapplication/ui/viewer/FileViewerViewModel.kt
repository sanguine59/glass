package com.example.myapplication.ui.viewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.FileStorage
import com.example.myapplication.data.repository.FileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class FileViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FileRepository(FileStorage(application))

    private val _uiState = MutableStateFlow(FileViewerUiState())
    val uiState: StateFlow<FileViewerUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun open(path: String) {
        if (_uiState.value.path == path) return
        loadJob?.cancel()
        _uiState.value = FileViewerUiState(path = path, name = File(path).name)
        loadJob = viewModelScope.launch {
            repository.open(path).fold(
                onSuccess = { content ->
                    _uiState.value = _uiState.value.copy(isLoading = false, content = content)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message ?: "Could not open file")
                },
            )
        }
    }

    fun close() {
        loadJob?.cancel()
        _uiState.value = FileViewerUiState()
    }
}
