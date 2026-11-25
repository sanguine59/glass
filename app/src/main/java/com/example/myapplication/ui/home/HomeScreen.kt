package com.example.myapplication.ui.home

import ai.koog.prompt.text.text
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.R
import com.example.myapplication.data.model.FileEntry
import com.example.myapplication.ui.components.MonoButton
import com.example.myapplication.ui.components.MonoDialog
import com.example.myapplication.util.ApiKeyStorage

@Composable
fun HomeScreen(
    onOpenFile: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(),
    onSettingsClick : () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val black = colorResource(R.color.black)
    val white = colorResource(R.color.white)

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importZip(uri)
    }

    BackHandler(enabled = !state.isRoot) { viewModel.navigateUp() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(black)
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.title,
                color = white,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSettingsClick) {
                Text(text = "=", color = white, fontSize = 24.sp)
            }
            TextButton(onClick = viewModel::showCreate) {
                Text(text = "+", color = white, fontSize = 24.sp)
            }
        }

        if (state.isImporting) {
            LinearProgressIndicator(
                color = white,
                trackColor = white.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            HorizontalDivider(color = white)
        }

        if (state.entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "empty", color = white.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.entries, key = { it.path }) { entry ->
                    EntryRow(
                        entry = entry,
                        onClick = {
                            if (entry.isDirectory) viewModel.onEntryClick(entry) else onOpenFile(entry.path)
                        },
                        onLongClick = { viewModel.onEntryLongClick(entry) },
                    )
                    HorizontalDivider(color = white.copy(alpha = 0.2f))
                }
            }
        }
    }

    when (val dialog = state.dialog) {
        HomeDialog.Create -> CreateDialog(
            onNewFolder = viewModel::addFolder,
            onImportZip = {
                viewModel.dismissDialog()
                zipPicker.launch(ZIP_MIME_TYPES)
            },
            onDismiss = viewModel::dismissDialog,
        )
        is HomeDialog.Actions -> ActionsDialog(
            entry = dialog.entry,
            onRename = { viewModel.showRename(dialog.entry) },
            onDelete = { viewModel.showDelete(dialog.entry) },
            onDismiss = viewModel::dismissDialog,
        )
        is HomeDialog.Rename -> RenameDialog(
            entry = dialog.entry,
            error = dialog.error,
            onConfirm = { viewModel.rename(dialog.entry, it) },
            onDismiss = viewModel::dismissDialog,
        )
        is HomeDialog.Delete -> DeleteDialog(
            entry = dialog.entry,
            error = dialog.error,
            onConfirm = { viewModel.delete(dialog.entry) },
            onDismiss = viewModel::dismissDialog,
        )
        is HomeDialog.Error -> ErrorDialog(
            title = dialog.title,
            message = dialog.message,
            onDismiss = viewModel::dismissDialog,
        )
        null -> Unit
    }
}

private val ZIP_MIME_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/x-zip")

@Composable
fun SettingScreen(onBack: () -> Unit){
    BackHandler(onBack = onBack)
    val black = colorResource(R.color.black)
    val white = colorResource(R.color.white)
    val context = LocalContext.current
    val apiKeyStorage = remember { ApiKeyStorage(context) }
    var userInput by remember { mutableStateOf(apiKeyStorage.getOpenRouterKey().orEmpty()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(black)
            .safeDrawingPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { apiKeyStorage.saveOpenRouterKey(
                apiKey = userInput.trim())
                onBack()
            }) {
                Text(text = "Save", color = white, fontSize = 24.sp)
            }
        }
        TextArea(text = userInput, onTextChange = {userInput = it})
    }
}

@Composable
fun TextArea(
    text: String,
    onTextChange: (String) -> Unit
) {
    val white = colorResource(R.color.white)
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = "secret key", color = white)
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = {Text("Enter Secret")},
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CreateDialog(
    onNewFolder: () -> Unit,
    onImportZip: () -> Unit,
    onDismiss: () -> Unit,
) {
    MonoDialog(title = "New", onDismiss = onDismiss) {
        MonoButton(text = "New folder", onClick = onNewFolder, modifier = Modifier.fillMaxWidth())
        MonoButton(text = "Import .zip", onClick = onImportZip, modifier = Modifier.fillMaxWidth())
        MonoButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ErrorDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    MonoDialog(title = title, onDismiss = onDismiss) {
        Text(text = message, color = colorResource(R.color.white))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            MonoButton(text = "OK", onClick = onDismiss)
        }
    }
}

@Composable
private fun EntryRow(
    entry: FileEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Text(
        text = if (entry.isDirectory) "${entry.name}/" else entry.name,
        color = colorResource(R.color.white),
        fontSize = 16.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = "Rename or delete",
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
private fun ActionsDialog(
    entry: FileEntry,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    MonoDialog(title = entry.name, onDismiss = onDismiss) {
        MonoButton(text = "Rename", onClick = onRename, modifier = Modifier.fillMaxWidth())
        MonoButton(text = "Delete", onClick = onDelete, modifier = Modifier.fillMaxWidth())
        MonoButton(text = "Cancel", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RenameDialog(
    entry: FileEntry,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val white = colorResource(R.color.white)
    var name by remember(entry.path) { mutableStateOf(entry.name) }

    MonoDialog(title = "Rename", onDismiss = onDismiss) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            isError = error != null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = white,
                unfocusedTextColor = white,
                cursorColor = white,
                focusedBorderColor = white,
                unfocusedBorderColor = white.copy(alpha = 0.5f),
                errorBorderColor = white,
                errorTextColor = white,
                errorCursorColor = white,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(text = error, color = white, fontSize = 14.sp)
        }
        DialogButtons(confirm = "Save", onConfirm = { onConfirm(name) }, onDismiss = onDismiss)
    }
}

@Composable
private fun DeleteDialog(
    entry: FileEntry,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val white = colorResource(R.color.white)

    MonoDialog(title = "Delete", onDismiss = onDismiss) {
        Text(
            text = if (entry.isDirectory) {
                "Delete \"${entry.name}\" and everything inside it?"
            } else {
                "Delete \"${entry.name}\"?"
            },
            color = white,
        )
        if (error != null) {
            Text(text = error, color = white, fontSize = 14.sp)
        }
        DialogButtons(confirm = "Delete", onConfirm = onConfirm, onDismiss = onDismiss)
    }
}

@Composable
private fun DialogButtons(
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        MonoButton(text = "Cancel", onClick = onDismiss)
        MonoButton(text = confirm, onClick = onConfirm)
    }
}
