package com.example.myapplication.di

import android.content.Context
import com.example.myapplication.data.local.FileStorage
import com.example.myapplication.data.local.ZipImporter
import com.example.myapplication.data.repository.FileRepository
import com.example.myapplication.data.runtime.NodeRuntime
import com.example.myapplication.data.runtime.PrefixInstaller
import com.example.myapplication.data.runtime.ProjectRunner
import com.example.myapplication.data.runtime.ShebangFixer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {

    private val app = context.applicationContext

    private val appScope = CoroutineScope(SupervisorJob())

    val fileRepository: FileRepository by lazy {
        FileRepository(FileStorage(app), ZipImporter(app))
    }

    private val nodeRuntime: NodeRuntime by lazy { NodeRuntime(app) }

    val projectRunner: ProjectRunner by lazy {
        ProjectRunner(nodeRuntime, PrefixInstaller(app, nodeRuntime), ShebangFixer(), appScope)
    }
}
