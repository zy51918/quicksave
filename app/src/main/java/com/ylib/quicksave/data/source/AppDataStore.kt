package com.ylib.quicksave.data.source

import kotlinx.coroutines.flow.Flow

interface AppDataStore {
    suspend fun saveTargetFileUri(uri: String)
    fun getTargetFileUri(): Flow<String?>
}
