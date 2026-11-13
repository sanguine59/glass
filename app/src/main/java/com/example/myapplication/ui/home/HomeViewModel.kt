package com.example.myapplication.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.FolderStorage
import com.example.myapplication.data.model.Folder
import com.example.myapplication.data.repository.FolderRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FolderRepository(FolderStorage(application))

    val folders: StateFlow<List<Folder>> = repository.folders

    init {
        viewModelScope.launch { repository.refresh() }
    }

    fun addFolder() {
        viewModelScope.launch { repository.add() }
    }
}
