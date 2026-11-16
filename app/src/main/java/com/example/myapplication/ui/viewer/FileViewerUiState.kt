package com.example.myapplication.ui.viewer

import com.example.myapplication.data.model.FileContent

data class FileViewerUiState(
    val path: String? = null,
    val name: String = "",
    val isLoading: Boolean = true,
    val content: FileContent? = null,
    val error: String? = null,
)
