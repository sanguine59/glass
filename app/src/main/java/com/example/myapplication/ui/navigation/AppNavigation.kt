package com.example.myapplication.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import com.example.myapplication.ui.home.HomeScreen
import com.example.myapplication.ui.home.SettingScreen
import com.example.myapplication.ui.viewer.FileViewerScreen

@Composable
fun AppNavigation() {
    val stateHolder = rememberSaveableStateHolder()
    var openFile by rememberSaveable { mutableStateOf<String?>(null) }
    var openSettings by rememberSaveable { mutableStateOf(false)}
    val path = openFile
    if (path == null) {
        stateHolder.SaveableStateProvider(HOME) {
            HomeScreen(
                onOpenFile = { openFile = it },
                onSettingsClick = {openSettings = true}
            )
        }
    } else if(openSettings) {
        stateHolder.SaveableStateProvider(SETTINGS) {
            SettingScreen (
                onBack = {openSettings = false}
            )
        }
    } else {
        FileViewerScreen(path = path, onBack = { openFile = null })
    }
}

private const val HOME = "home"
private const val SETTINGS = "settings"
