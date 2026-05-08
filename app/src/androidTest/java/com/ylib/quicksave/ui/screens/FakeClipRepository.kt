package com.ylib.quicksave.ui.screens

import android.net.Uri
import com.ylib.quicksave.data.repository.ClipRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeClipRepository(
    initialUri: Uri? = Uri.parse("content://test/quicksave.txt"),
    initialCategories: List<String> = emptyList(),
    initialSelected: String? = null
) : ClipRepository {

    private val uriFlow = MutableStateFlow(initialUri)
    private val categoriesFlow = MutableStateFlow(initialCategories)
    private val selectedFlow = MutableStateFlow(initialSelected)

    val saveEntryCalls = mutableListOf<Pair<String, String?>>()
    var saveEntryResult: Result<Unit> = Result.success(Unit)
    var clearSavedFileResult: Result<Unit> = Result.success(Unit)

    override suspend fun saveEntry(text: String, category: String?): Result<Unit> {
        saveEntryCalls.add(text to category)
        return saveEntryResult
    }

    override suspend fun setTargetFile(uri: Uri) { uriFlow.value = uri }
    override fun getTargetFileUri(): Flow<Uri?> = uriFlow

    override suspend fun clearSavedFile(): Result<Unit> = clearSavedFileResult

    override fun getCategories(): Flow<List<String>> = categoriesFlow
    override suspend fun setCategories(categories: List<String>) { categoriesFlow.value = categories }

    override fun getSelectedCategory(): Flow<String?> = selectedFlow
    override suspend fun setSelectedCategory(category: String?) { selectedFlow.value = category }
}
