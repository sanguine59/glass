package com.example.myapplication.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.R
import com.example.myapplication.data.model.CodeLine
import com.example.myapplication.data.model.FileContent
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

    BackHandler {
        viewModel.close()
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(black)
            .safeDrawingPadding(),
    ) {
        Text(
            text = state.name,
            color = white,
            fontSize = 20.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )

        HorizontalDivider(color = white)

        val content = state.content
        when {
            state.isLoading -> Message("loading", white)
            state.error != null -> Message(state.error.orEmpty(), white)
            content is FileContent.Binary -> Message("binary file, can't be displayed", white)
            content is FileContent.TooLarge -> Message("file too large to display (${formatSize(content.bytes)})", white)
            content is FileContent.Text && content.lines.isEmpty() -> Message("empty file", white)
            content is FileContent.Text -> CodeView(content.lines, white)
        }
    }
}

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
