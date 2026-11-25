package com.example.myapplication.ui.agent

import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AgentViewModel(application: Application) : AndroidViewModel(application) {

    val autoFreeModel = LLModel(
        provider = LLMProvider.OpenRouter,
        id = "openrouter/free",
        capabilities = listOf(
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.Completion
        ),
        contextLength = 200_000L
    )

    fun sendPrompt(
        apiKey: String,
        projectDir: String,
        userMessage: String,
        onResponse: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val openRouterClient = OpenRouterLLMClient(apiKey)
            val workspace = projectDir
        }
    }
}