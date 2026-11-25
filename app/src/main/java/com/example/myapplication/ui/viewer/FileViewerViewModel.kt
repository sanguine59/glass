package com.example.myapplication.ui.viewer

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.http.client.ktor.fromKtorClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import android.app.Application
import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.GlassApplication
import com.example.myapplication.data.model.FileContent
import com.example.myapplication.data.model.RunState
import com.example.myapplication.data.model.isActive
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttp
import org.slf4j.LoggerFactory
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

    fun reload() {
        val currentPath = _uiState.value.path ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            repository.open(currentPath).onSuccess { content ->
                if (content is FileContent.Editable) {
                    _uiState.update {
                        it.copy(
                            content = content,
                            editor = TextFieldState(content.text),
                            savedText = content.text,
                            format = content.format
                        )
                    }
                }
            }
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


    fun runAgentInstruction(
        instruction: String,
        apiKey: String,
        onStatus: (String) -> Unit
    ) {
        val state = _uiState.value
        val rootPath = state.projectPath ?: state.path?.let { File(it).parent } ?: return
        val workspaceDir = File(rootPath)

        save {
            viewModelScope.launch(Dispatchers.IO) {
                onStatus("Starting agent...")

                val workspaceTools = IdeWorkspaceTools(
                    workspaceDir = workspaceDir,
                    onFileModified = { modifiedFile ->
                        val activePath = _uiState.value.path
                        if (activePath != null && modifiedFile.canonicalPath == File(activePath).canonicalPath) {
                            viewModelScope.launch(Dispatchers.Main) {
                                reload()
                            }
                        }
                    }
                )

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
                val openRouterClient = OpenRouterLLMClient(
                    apiKey = apiKey,
                    httpClientFactory = KtorKoogHttpClient.Factory()
                )

                val agent = AIAgent(
                    promptExecutor = MultiLLMPromptExecutor(openRouterClient),
                    llmModel = autoFreeModel,
                    systemPrompt = """
                    You are an embedded coding agent inside an Android IDE.
                    Project root: ${workspaceDir.name}
                    Currently open file: ${state.path?.let { File(it).relativeTo(workspaceDir).path } ?: "none"}
                    Use your tools to inspect and edit files directly.
                """.trimIndent(),
                    toolRegistry = ToolRegistry { tools(workspaceTools) }
                ) {
                    handleEvents {
                        onToolCallStarting { ctx ->
                            onStatus("Running ${ctx.toolName}...")
                        }
                        onAgentCompleted { ctx ->
                            onStatus("Done: ${ctx.result}")
                        }
                        onAgentExecutionFailed { ctx ->
                            onStatus("Error: ${ctx.error.message}")
                        }
                    }
                }

                agent.run(instruction)
            }
        }
    }

    private fun tools(workspaceTools: Any) {}
}
