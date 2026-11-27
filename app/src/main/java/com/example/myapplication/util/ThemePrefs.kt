package com.example.myapplication.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { ORIGINAL, MOCHA, LATTE }

class ThemePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(load())
    val mode: StateFlow<ThemeMode> = _mode

    private fun load(): ThemeMode = runCatching {
        ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.ORIGINAL.name)!!)
    }.getOrDefault(ThemeMode.ORIGINAL)

    fun setMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _mode.value = mode
    }
}