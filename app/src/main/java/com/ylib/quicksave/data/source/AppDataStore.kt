package com.ylib.quicksave.data.source

import kotlinx.coroutines.flow.Flow

interface AppDataStore {
    suspend fun saveTargetFileUri(uri: String)
    fun getTargetFileUri(): Flow<String?>
    suspend fun saveCategories(categories: List<String>)
    fun getCategories(): Flow<List<String>>
    suspend fun saveSelectedCategory(category: String?)
    fun getSelectedCategory(): Flow<String?>
    suspend fun saveOverlayEnabled(enabled: Boolean)
    fun getOverlayEnabled(): Flow<Boolean>
    suspend fun saveOverlayPosition(edge: String, yRatio: Float)
    fun getOverlayPosition(): Flow<Pair<String, Float>>
}
