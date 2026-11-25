package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.myapplication.ui.navigation.AppNavigation
import com.example.myapplication.ui.theme.GlassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val themePrefs = (application as GlassApplication).themePrefs
        setContent {
            val mode by themePrefs.mode.collectAsState()
            GlassTheme(mode) {
                AppNavigation()
            }
        }
    }
}