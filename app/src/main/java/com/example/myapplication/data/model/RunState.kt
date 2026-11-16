package com.example.myapplication.data.model

sealed interface RunState {
    data object Idle : RunState
    data class Running(val project: String, val step: String) : RunState
    data class Serving(val project: String, val url: String, val lanUrl: String?) : RunState
    data class Failed(val project: String, val message: String) : RunState
}

val RunState.isActive: Boolean
    get() = this is RunState.Running || this is RunState.Serving
