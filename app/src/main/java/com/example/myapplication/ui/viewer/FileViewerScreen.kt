package com.example.myapplication.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.R
import com.example.myapplication.data.model.CodeLine
import com.example.myapplication.data.model.FileContent
import com.example.myapplication.data.model.isActive
import com.example.myapplication.ui.components.MonoButton
import com.example.myapplication.ui.components.MonoDialog
import java.util.Locale

@Composable
fun FileViewerScreen(
    path: String,
    onBack: () -> Unit,
    viewModel: FileViewerViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val black = colorResource(R.color.black)
    val white = colorResource(R.color.white)

    LaunchedEffect(path) { viewModel.open(path) }

    val closeViewer = {
        viewModel.close()
        onBack()
    }

    BackHandler {
        if (viewModel.requestClose()) closeViewer()
    }

    val isDirty by remember(state.editor, state.savedText) {
        derivedStateOf { state.editor?.text?.contentEquals(state.savedText) == false }
    }

    val runState by viewModel.runState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    var panelHeight by rememberSaveable { mutableFloatStateOf(PANEL_MIN_HEIGHT.value) }
    var showAgentBar by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(black)
            .safeDrawingPadding(),
    ) {
        val density = LocalDensity.current
        val maxPanel = (maxHeight - 56.dp).coerceAtLeast(PANEL_MIN_HEIGHT)
        val panel = panelHeight.dp.coerceIn(PANEL_MIN_HEIGHT, maxPanel)
        val expandedPanel = (maxHeight * 0.4f).coerceIn(PANEL_MIN_HEIGHT, maxPanel)
        val isActive = runState.isActive

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isDirty) "${state.name} •" else state.name,
                    color = white,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp),
                )
                if (state.editor != null) {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = isDirty && !state.isSaving,
                    ) {
                        Text(
                            text = "save",
                            color = if (isDirty && !state.isSaving) white else white.copy(alpha = 0.3f),
                            fontSize = 16.sp,
                        )
                    }
                }
                TextButton(
                    onClick = {
                        if (!isActive && panel <= PANEL_MIN_HEIGHT) panelHeight = expandedPanel.value
                        viewModel.onRunClick()
                    },
                    enabled = isActive || state.projectPath != null,
                ) {
                    TextButton(
                        onClick = { showAgentBar = !showAgentBar },
                        enabled = state.projectPath != null,
                    ) {
                        Text(
                            text = if (showAgentBar) "close agent" else "agent",
                            color = if (state.projectPath != null) white else white.copy(alpha = 0.3f),
                            fontSize = 16.sp,
                        )
                    }
                    Text(
                        text = if (isActive) "stop" else "run",
                        color = if (isActive || state.projectPath != null) white else white.copy(alpha = 0.3f),
                        fontSize = 16.sp,
                    )
                }
            }

            HorizontalDivider(color = white)

            Box(modifier = Modifier.weight(1f)) {
                val content = state.content
                val editor = state.editor
                when {
                    state.isLoading -> Message("loading", white)
                    state.error != null -> Message(state.error.orEmpty(), white)
                    content is FileContent.Editable && editor != null -> CodeEditor(editor, white)
                    content is FileContent.Binary -> Message("binary file, can't be displayed", white)
                    content is FileContent.TooLarge -> Message("file too large to display (${formatSize(content.bytes)})", white)
                    content is FileContent.Text && content.lines.isEmpty() -> Message("empty file", white)
                    content is FileContent.Text -> CodeView(content.lines, white)
                }
            }
            if (showAgentBar) {
                AgentPromptBar(viewModel = viewModel)
            }

            RunPanel(
                runState = runState,
                logs = logs,
                height = panel,
                onDrag = { delta ->
                    val next = panel - with(density) { delta.toDp() }
                    panelHeight = next.coerceIn(PANEL_MIN_HEIGHT, maxPanel).value
                },
            )
        }
    }

    when (val dialog = state.dialog) {
        ViewerDialog.UnsavedChanges -> MonoDialog(title = "Unsaved changes", onDismiss = viewModel::dismissDialog) {
            Text(text = "Save changes to \"${state.name}\"?", color = white)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                MonoButton(text = "Discard", onClick = closeViewer)
                MonoButton(text = "Cancel", onClick = viewModel::dismissDialog)
                MonoButton(
                    text = "Save",
                    onClick = {
                        viewModel.dismissDialog()
                        viewModel.save(onSaved = closeViewer)
                    },
                )
            }
        }
        is ViewerDialog.SaveFailed -> MonoDialog(title = "Save failed", onDismiss = viewModel::dismissDialog) {
            Text(text = dialog.message, color = white)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                MonoButton(text = "OK", onClick = viewModel::dismissDialog)
            }
        }
        null -> Unit
    }
}

private val PANEL_MIN_HEIGHT = 48.dp

@Composable
private fun CodeView(lines: List<CodeLine>, white: Color) {
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val digits = (lines.lastOrNull { it.number != null }?.number ?: 1).toString().length
    val gutterWidth = remember(digits) {
        with(density) { measurer.measure("9".repeat(digits), style).size.width.toDp() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(lines.size) { index ->
            val line = lines[index]
            Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(
                    text = line.number?.toString().orEmpty(),
                    style = style,
                    color = white.copy(alpha = 0.4f),
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(gutterWidth),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = line.text,
                    style = style,
                    color = white,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Message(text: String, white: Color) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = white.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
