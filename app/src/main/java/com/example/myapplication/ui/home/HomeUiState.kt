package com.example.myapplication.ui.home

import com.example.myapplication.data.model.FileEntry

data class HomeUiState(
    val title: String = "glass",
    val isRoot: Boolean = true,
    val entries: List<FileEntry> = emptyList(),
    val isImporting: Boolean = false,
    val dialog: HomeDialog? = null,
)

sealed interface HomeDialog {
    data object Create : HomeDialog
    data class Actions(val entry: FileEntry) : HomeDialog
    data class Rename(val entry: FileEntry, val error: String? = null) : HomeDialog
    data class Delete(val entry: FileEntry, val error: String? = null) : HomeDialog
    data class Error(val title: String, val message: String) : HomeDialog
}
