package com.example.myapplication.util

import android.content.Context
import android.content.SharedPreferences

class ApiKeyStorage(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        "ide_agent_prefs",
        Context.MODE_PRIVATE
    )

    fun saveOpenRouterKey(apiKey: String) {
        prefs.edit().putString(KEY_OPENROUTER, apiKey.trim()).apply()
    }

    fun getOpenRouterKey(): String? {
        return prefs.getString(KEY_OPENROUTER, null)?.takeIf { it.isNotBlank() }
    }

    fun clearOpenRouterKey() {
        prefs.edit().remove(KEY_OPENROUTER).apply()
    }

    companion object {
        private const val KEY_OPENROUTER = "openrouter_api_key"
    }
}