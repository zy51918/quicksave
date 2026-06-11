package com.ylib.quicksave.app

import android.app.Application
import com.ylib.quicksave.data.repository.ClipRepository
import com.ylib.quicksave.data.repository.ClipRepositoryImpl
import com.ylib.quicksave.data.repository.OverlayRepository
import com.ylib.quicksave.data.repository.OverlayRepositoryImpl
import com.ylib.quicksave.data.source.AppDataStore
import com.ylib.quicksave.data.source.AppDataStoreImpl
import com.ylib.quicksave.data.source.FileDataSource
import com.ylib.quicksave.data.source.SafFileDataSource

class QuickSaveApplication : Application() {
    val dataStore: AppDataStore by lazy { AppDataStoreImpl(this) }
    val fileDataSource: FileDataSource by lazy { SafFileDataSource(contentResolver) }
    val clipRepository: ClipRepository by lazy { ClipRepositoryImpl(dataStore, fileDataSource) }
    val overlayRepository: OverlayRepository by lazy { OverlayRepositoryImpl(dataStore) }
}
