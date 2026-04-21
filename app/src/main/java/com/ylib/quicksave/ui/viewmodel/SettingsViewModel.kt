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

    fun setTargetFile(uri: Uri) {
        viewModelScope.launch { repo.setTargetFile(uri) }
    }
}
