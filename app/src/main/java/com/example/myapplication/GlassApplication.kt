package com.example.myapplication

import android.app.Application
import com.example.myapplication.di.AppContainer
import com.example.myapplication.util.ThemePrefs

class GlassApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }
    val themePrefs: ThemePrefs by lazy { ThemePrefs(this) }
}