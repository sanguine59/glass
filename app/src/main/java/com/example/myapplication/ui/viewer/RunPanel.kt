package com.example.myapplication.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.data.model.RunState

@Composable
fun RunPanel(
    runState: RunState,
    logs: List<String>,
    height: Dp,
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val black = colorResource(R.color.black)
    val white = colorResource(R.color.white)
    val listState = rememberLazyListState()
    var previousSize by remember { mutableIntStateOf(0) }

    LaunchedEffect(logs.size) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        if (logs.isNotEmpty() && lastVisible >= previousSize - 2) listState.scrollToItem(logs.lastIndex)
        previousSize = logs.size
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(black),
    ) {
        HorizontalDivider(color = white)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .draggable(
                    state = rememberDraggableState(onDrag),
                    orientation = Orientation.Vertical,
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(white.copy(alpha = 0.5f)),
                )
            }
            Text(
                text = statusText(runState),
                color = white.copy(alpha = 0.7f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
            )
        }

        HorizontalDivider(color = white.copy(alpha = 0.2f))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            items(logs.size) { index ->
                Text(
                    text = logs[index],
                    color = white,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp),
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

private fun statusText(state: RunState): String = when (state) {
    RunState.Idle -> "output"
    is RunState.Running -> "${state.project} · ${state.step}"
    is RunState.Serving -> "${state.project} · ${state.lanUrl ?: state.url}"
    is RunState.Failed -> "${state.project} · failed: ${state.message}"
}
