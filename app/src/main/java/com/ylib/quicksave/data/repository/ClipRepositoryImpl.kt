package com.ylib.quicksave.data.repository

import android.net.Uri
import com.ylib.quicksave.data.source.AppDataStore
import com.ylib.quicksave.data.source.FileDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipRepositoryImpl(
    private val dataStore: AppDataStore,
    private val fileDataSource: FileDataSource
) : ClipRepository {

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override suspend fun saveEntry(text: String): Result<Unit> = runCatching {
        val uriString = dataStore.getTargetFileUri().first()
            ?: throw IllegalStateException("未设置目标文件")
        val uri = Uri.parse(uriString)
        if (!fileDataSource.isWritable(uri)) throw SecurityException("目标文件无写入权限，请重新选择")
        fileDataSource.appendLine(uri, "[${dateFormatter.format(Date())}] $text")
    }

    override suspend fun setTargetFile(uri: Uri) {
        dataStore.saveTargetFileUri(uri.toString())
    }

    override fun getTargetFileUri(): Flow<Uri?> =
        dataStore.getTargetFileUri().map { it?.let(Uri::parse) }

    override suspend fun clearSavedFile(): Result<Unit> = runCatching {
        val uriString = dataStore.getTargetFileUri().first()
            ?: throw IllegalStateException("未设置目标文件")
        val uri = Uri.parse(uriString)
        if (!fileDataSource.isWritable(uri)) throw SecurityException("目标文件无写入权限，请重新选择")
        fileDataSource.clearFile(uri)
    }
}
