package com.ylib.quicksave.data.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface ClipRepository {
    suspend fun saveEntry(text: String): Result<Unit>
    suspend fun setTargetFile(uri: Uri)
    fun getTargetFileUri(): Flow<Uri?>
    suspend fun clearSavedFile(): Result<Unit>
}
