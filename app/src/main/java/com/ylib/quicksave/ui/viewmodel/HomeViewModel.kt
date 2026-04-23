package com.ylib.quicksave.ui.viewmodel

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ylib.quicksave.app.QuickSaveApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val targetFileUri: String? = null,
    val clipText: String? = null,
    val isSaving: Boolean = false,
    val showClearDialog: Boolean = false,
    val lastSaveResult: SaveResult? = null,
    val categories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val showAddCategoryDialog: Boolean = false
)

sealed class SaveResult {
    data object Success : SaveResult()
    data object ClearSuccess : SaveResult()
    data class Failure(val message: String) : SaveResult()
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as QuickSaveApplication).clipRepository
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repo.getTargetFileUri(),
                repo.getCategories(),
                repo.getSelectedCategory()
            ) { uri, categories, selected ->
                Triple(uri, categories, selected?.takeIf { it in categories })
            }.collect { (uri, categories, selected) ->
                _uiState.update {
                    it.copy(
                        targetFileUri = uri?.toString(),
                        categories = categories,
                        selectedCategory = selected
                    )
                }
            }
        }
    }

    fun readClipboard(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = runCatching { cm.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
        _uiState.update { it.copy(clipText = text?.takeIf { t -> t.isNotBlank() }) }
    }

    fun saveClipboard() {
        val text = _uiState.value.clipText ?: return
        val category = _uiState.value.selectedCategory
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = repo.saveEntry(text, category)
            _uiState.update {
                it.copy(
                    isSaving = false,
                    lastSaveResult = if (result.isSuccess) SaveResult.Success
                    else SaveResult.Failure(result.exceptionOrNull()?.message ?: "保存失败")
                )
            }
        }
    }

    fun selectCategory(category: String?) {
        viewModelScope.launch { repo.setSelectedCategory(category) }
    }

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || _uiState.value.categories.any { it == trimmed }) return
        viewModelScope.launch {
            repo.setCategories(_uiState.value.categories + trimmed)
            repo.setSelectedCategory(trimmed)
        }
    }

    fun showAddCategoryDialog() = _uiState.update { it.copy(showAddCategoryDialog = true) }
    fun dismissAddCategoryDialog() = _uiState.update { it.copy(showAddCategoryDialog = false) }
    fun showClearDialog() = _uiState.update { it.copy(showClearDialog = true) }
    fun dismissClearDialog() = _uiState.update { it.copy(showClearDialog = false) }
    fun clearLastSaveResult() = _uiState.update { it.copy(lastSaveResult = null) }

    fun clearSavedFile() {
        viewModelScope.launch {
            val result = repo.clearSavedFile()
            _uiState.update {
                it.copy(
                    showClearDialog = false,
                    lastSaveResult = if (result.isSuccess) SaveResult.ClearSuccess
                    else SaveResult.Failure(result.exceptionOrNull()?.message ?: "清空失败")
                )
            }
        }
    }
}
