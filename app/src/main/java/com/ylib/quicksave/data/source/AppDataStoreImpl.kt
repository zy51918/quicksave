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
    }

    override suspend fun saveTargetFileUri(uri: String) {
        context.dataStore.edit { it[TARGET_FILE_URI] = uri }
    }

    override fun getTargetFileUri(): Flow<String?> =
        context.dataStore.data.map { it[TARGET_FILE_URI] }
}
