package com.example.myapplication.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.myapplication.util.ThemeMode

private val MochaScheme = darkColorScheme(
    primary = Mocha.Mauve,
    onPrimary = Mocha.Crust,
    secondary = Mocha.Blue,
    onSecondary = Mocha.Crust,
    background = Mocha.Base,
    onBackground = Mocha.Text,
    surface = Mocha.Base,
    onSurface = Mocha.Text,
    surfaceVariant = Mocha.Surface0,
    onSurfaceVariant = Mocha.Text,
    outline = Mocha.Overlay0,
    error = Mocha.Red,
    onError = Mocha.Crust,
)

private val LatteScheme = lightColorScheme(
    primary = Latte.Mauve,
    onPrimary = Latte.Base,
    secondary = Latte.Blue,
    onSecondary = Latte.Base,
    background = Latte.Base,
    onBackground = Latte.Text,
    surface = Latte.Base,
    onSurface = Latte.Text,
    surfaceVariant = Latte.Surface0,
    onSurfaceVariant = Latte.Text,
    outline = Latte.Overlay0,
    error = Latte.Red,
    onError = Latte.Base,
)

@Composable
fun GlassTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit
) {
    val scheme = when (mode) {
        ThemeMode.MOCHA -> MochaScheme
        ThemeMode.LATTE -> LatteScheme
        ThemeMode.ORIGINAL -> darkColorScheme(
            background = Color.Black,
            surface = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}