package com.example.myapplication.data.model

sealed interface FileContent {
    data class Editable(val text: String, val format: TextFormat) : FileContent
    data class Text(val lines: List<CodeLine>) : FileContent
    data object Binary : FileContent
    data class TooLarge(val bytes: Long) : FileContent
}
