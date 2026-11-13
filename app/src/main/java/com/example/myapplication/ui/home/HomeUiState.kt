package com.example.myapplication.ui.home

import com.example.myapplication.data.model.FileEntry

data class HomeUiState(
    val title: String = "glass",
    val isRoot: Boolean = true,
    val entries: List<FileEntry> = emptyList(),
    val dialog: HomeDialog? = null,
)

sealed interface HomeDialog {
    val entry: FileEntry

    data class Actions(override val entry: FileEntry) : HomeDialog
    data class Rename(override val entry: FileEntry, val error: String? = null) : HomeDialog
    data class Delete(override val entry: FileEntry, val error: String? = null) : HomeDialog
}
