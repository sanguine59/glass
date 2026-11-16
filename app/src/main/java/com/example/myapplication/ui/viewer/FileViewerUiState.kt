package com.example.myapplication.ui.viewer

import androidx.compose.foundation.text.input.TextFieldState
import com.example.myapplication.data.model.FileContent
import com.example.myapplication.data.model.TextFormat

data class FileViewerUiState(
    val path: String? = null,
    val name: String = "",
    val isLoading: Boolean = true,
    val content: FileContent? = null,
    val error: String? = null,
    val projectPath: String? = null,
    val editor: TextFieldState? = null,
    val savedText: String = "",
    val format: TextFormat? = null,
    val isSaving: Boolean = false,
    val dialog: ViewerDialog? = null,
)

sealed interface ViewerDialog {
    data object UnsavedChanges : ViewerDialog
    data class SaveFailed(val message: String) : ViewerDialog
}
