package com.example.myapplication.ui.home

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val black = colorResource(R.color.black)
    val white = colorResource(R.color.white)

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
            TextButton(onClick = viewModel::addFolder) {
                Text(text = "+", color = white, fontSize = 24.sp)
            }
        }

        HorizontalDivider(color = white)

        if (state.entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "empty", color = white.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.entries, key = { it.path }) { entry ->
                    EntryRow(
                        entry = entry,
                        onClick = { viewModel.onEntryClick(entry) },
                        onLongClick = { viewModel.onEntryLongClick(entry) },
                    )
                    HorizontalDivider(color = white.copy(alpha = 0.2f))
                }
            }
        }
    }

    when (val dialog = state.dialog) {
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
        null -> Unit
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
