package com.example.myapplication.ui.viewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.GlassApplication
import com.example.myapplication.data.model.RunState
import com.example.myapplication.data.model.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class FileViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as GlassApplication).container
    private val repository = container.fileRepository
    private val runner = container.projectRunner

    private val _uiState = MutableStateFlow(FileViewerUiState())
    val uiState: StateFlow<FileViewerUiState> = _uiState.asStateFlow()

    val runState: StateFlow<RunState> = runner.state
    val logs: StateFlow<List<String>> = runner.logs

    private var loadJob: Job? = null

    fun open(path: String) {
        if (_uiState.value.path == path) return
        loadJob?.cancel()
        _uiState.value = FileViewerUiState(path = path, name = File(path).name)
        loadJob = viewModelScope.launch {
            val project = repository.findProjectRoot(path)
            _uiState.value = _uiState.value.copy(projectPath = project?.absolutePath)
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

    fun onRunClick() {
        if (runner.state.value.isActive) {
            runner.stop()
            return
        }
        val project = _uiState.value.projectPath ?: return
        runner.run(File(project))
    }

    fun close() {
        loadJob?.cancel()
        _uiState.value = FileViewerUiState()
    }
}
