package com.ylib.quicksave.data.source

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "quicksave_prefs")

class AppDataStoreImpl(private val context: Context) : AppDataStore {

    companion object {
        private val TARGET_FILE_URI = stringPreferencesKey("target_file_uri")
        private val CATEGORIES = stringPreferencesKey("categories")
        private val SELECTED_CATEGORY = stringPreferencesKey("selected_category")
    }

    override suspend fun saveTargetFileUri(uri: String) {
        context.dataStore.edit { it[TARGET_FILE_URI] = uri }
    }

    override fun getTargetFileUri(): Flow<String?> =
        context.dataStore.data.map { it[TARGET_FILE_URI] }

    override suspend fun saveCategories(categories: List<String>) {
        context.dataStore.edit { it[CATEGORIES] = categories.joinToString("\n") }
    }

    override fun getCategories(): Flow<List<String>> =
        context.dataStore.data.map {
            it[CATEGORIES]?.split("\n")?.filter { c -> c.isNotBlank() } ?: emptyList()
        }

    override suspend fun saveSelectedCategory(category: String?) {
        context.dataStore.edit { prefs ->
            if (category != null) prefs[SELECTED_CATEGORY] = category
            else prefs.remove(SELECTED_CATEGORY)
        }
    }

    override fun getSelectedCategory(): Flow<String?> =
        context.dataStore.data.map { it[SELECTED_CATEGORY] }
}
