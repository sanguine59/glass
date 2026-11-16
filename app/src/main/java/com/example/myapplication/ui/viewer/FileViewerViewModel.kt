package com.example.myapplication.ui.viewer

import android.app.Application
import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.GlassApplication
import com.example.myapplication.data.model.FileContent
import com.example.myapplication.data.model.RunState
import com.example.myapplication.data.model.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
            _uiState.update { it.copy(projectPath = project?.absolutePath) }
            repository.open(path).fold(
                onSuccess = { content ->
                    _uiState.update {
                        if (content is FileContent.Editable) {
                            it.copy(
                                isLoading = false,
                                content = content,
                                editor = TextFieldState(content.text),
                                savedText = content.text,
                                format = content.format,
                            )
                        } else {
                            it.copy(isLoading = false, content = content)
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "Could not open file") }
                },
            )
        }
    }

    fun save(onSaved: () -> Unit = {}) {
        val state = _uiState.value
        if (state.isSaving) return
        val path = state.path
        val editor = state.editor
        val format = state.format
        if (path == null || editor == null || format == null) {
            onSaved()
            return
        }
        val text = editor.text.toString()
        if (text == state.savedText) {
            onSaved()
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            repository.save(path, text, format).fold(
                onSuccess = {
                    _uiState.update { it.copy(isSaving = false, savedText = text) }
                    onSaved()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isSaving = false, dialog = ViewerDialog.SaveFailed(error.message ?: "Could not save"))
                    }
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
        save { runner.run(File(project)) }
    }

    fun requestClose(): Boolean {
        val state = _uiState.value
        val dirty = state.editor?.text?.contentEquals(state.savedText) == false
        if (dirty) _uiState.update { it.copy(dialog = ViewerDialog.UnsavedChanges) }
        return !dirty
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialog = null) }
    }

    fun close() {
        loadJob?.cancel()
        _uiState.value = FileViewerUiState()
    }
}
