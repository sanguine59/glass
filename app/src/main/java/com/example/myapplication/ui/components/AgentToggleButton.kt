package com.example.myapplication.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp

@Composable
fun AgentToggleButton(showing: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = if (showing) "close agent" else "agent",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
        )
    }
}