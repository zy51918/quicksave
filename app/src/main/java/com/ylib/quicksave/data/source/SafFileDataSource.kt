package com.ylib.quicksave.data.source

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SafFileDataSource(private val contentResolver: ContentResolver) : FileDataSource {

    override suspend fun appendLine(uri: Uri, line: String) {
        withContext(Dispatchers.IO) {
            contentResolver.openOutputStream(uri, "wa")?.use {
                it.write("$line\n".toByteArray(Charsets.UTF_8))
            }
        }
    }

    override suspend fun clearFile(uri: Uri) {
        withContext(Dispatchers.IO) {
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(ByteArray(0)) }
        }
    }

    override fun isWritable(uri: Uri): Boolean =
        contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
}
