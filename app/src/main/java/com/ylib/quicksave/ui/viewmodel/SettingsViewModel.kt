package com.ylib.quicksave.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ylib.quicksave.app.QuickSaveApplication
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as QuickSaveApplication).clipRepository

    val targetFileUri: StateFlow<Uri?> = repo.getTargetFileUri()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val categories: StateFlow<List<String>> = repo.getCategories()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setTargetFile(uri: Uri) {
        viewModelScope.launch { repo.setTargetFile(uri) }
    }

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || categories.value.any { it == trimmed }) return
        viewModelScope.launch { repo.setCategories(categories.value + trimmed) }
    }

    fun renameCategory(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || categories.value.any { it == trimmed }) return
        viewModelScope.launch {
            repo.setCategories(categories.value.map { if (it == oldName) trimmed else it })
        }
    }

    fun deleteCategory(name: String) {
        viewModelScope.launch {
            repo.setCategories(categories.value.filter { it != name })
        }
    }

    fun reorderCategories(reordered: List<String>) {
        viewModelScope.launch { repo.setCategories(reordered) }
    }
}
