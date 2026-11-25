package com.example.myapplication

import android.app.Application
import com.example.myapplication.di.AppContainer

class GlassApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }
}
