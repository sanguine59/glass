package com.example.myapplication.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.util.ApiKeyStorage

@Composable
fun AgentPromptBar(
    viewModel: FileViewerViewModel,
    workspacePath: String? = null,
    onFilesChanged: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val bg = MaterialTheme.colorScheme.background
    val fg = MaterialTheme.colorScheme.onBackground
    val context = LocalContext.current
    val keyStorage = remember { ApiKeyStorage(context) }

    var instruction by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Ready") }
    var isRunning by remember { mutableStateOf(false) }
    var showKeyInput by remember { mutableStateOf(keyStorage.getOpenRouterKey() == null) }
    var apiKeyInput by remember { mutableStateOf(keyStorage.getOpenRouterKey().orEmpty()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bg)
            .border(1.dp, fg)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = statusText,
                color = fg,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (showKeyInput) "[HIDE KEY]" else "[API KEY]",
                color = fg,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { showKeyInput = !showKeyInput }
            )
        }

        if (showKeyInput) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = fg,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(fg),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, fg)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            if (apiKeyInput.isEmpty()) {
                                Text(
                                    text = "Paste sk-or-v1-... key",
                                    color = fg.copy(alpha = 0.5f),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(fg)
                        .clickable {
                            keyStorage.saveOpenRouterKey(apiKeyInput.trim())
                            showKeyInput = false
                            statusText = "API key saved"
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "SAVE",
                        color = bg,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = instruction,
                onValueChange = { instruction = it },
                enabled = !isRunning,
                maxLines = 3,
                textStyle = TextStyle(
                    color = fg,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                ),
                cursorBrush = SolidColor(fg),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .border(1.dp, fg)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        if (instruction.isEmpty()) {
                            Text(
                                text = "Instruct agent (e.g., fix syntax error)...",
                                color = fg.copy(alpha = 0.5f),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .background(if (isRunning) bg else fg)
                    .border(1.dp, fg)
                    .clickable(enabled = !isRunning && instruction.isNotBlank()) {
                        val key = keyStorage.getOpenRouterKey()
                        if (key.isNullOrBlank()) {
                            showKeyInput = true
                            statusText = "Set OpenRouter API key first"
                            return@clickable
                        }
                        val prompt = instruction.trim()
                        instruction = ""
                        isRunning = true

                        viewModel.runAgentInstruction(
                            instruction = prompt,
                            apiKey = key,
                            workspacePath = workspacePath,
                            onFilesChanged = onFilesChanged,
                            onStatus = { status ->
                                statusText = status
                                if (status.startsWith("Done:") || status.startsWith("Error:")) {
                                    isRunning = false
                                }
                            }
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (isRunning) "..." else "RUN",
                    color = if (isRunning) fg else bg,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}