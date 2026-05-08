package com.ylib.quicksave.data.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface ClipRepository {
    suspend fun saveEntry(text: String, category: String? = null): Result<Unit>
    suspend fun setTargetFile(uri: Uri)
    fun getTargetFileUri(): Flow<Uri?>
    suspend fun clearSavedFile(): Result<Unit>
    fun getCategories(): Flow<List<String>>
    suspend fun setCategories(categories: List<String>)
    fun getSelectedCategory(): Flow<String?>
    suspend fun setSelectedCategory(category: String?)
}
