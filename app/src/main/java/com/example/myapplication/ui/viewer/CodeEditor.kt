package com.example.myapplication.ui.viewer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CodeEditor(state: TextFieldState, white: Color) {
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp, color = white)
    val lineCount by remember(state) { derivedStateOf { state.text.count { it == '\n' } + 1 } }
    val numbers = remember(lineCount) { (1..lineCount).joinToString("\n") }
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScroll),
        ) {
            Text(
                text = numbers,
                style = style.copy(color = white.copy(alpha = 0.4f), textAlign = TextAlign.End),
                modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            )
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    state = state,
                    textStyle = style,
                    cursorBrush = SolidColor(white),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                    ),
                    modifier = Modifier
                        .horizontalScroll(horizontalScroll)
                        .widthIn(min = maxWidth)
                        .heightIn(min = viewportHeight)
                        .padding(top = 8.dp, bottom = 8.dp, end = 16.dp),
                )
            }
        }
    }
}
