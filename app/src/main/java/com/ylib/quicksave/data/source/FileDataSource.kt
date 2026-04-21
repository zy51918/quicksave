package com.ylib.quicksave.data.source

import android.net.Uri

interface FileDataSource {
    suspend fun appendLine(uri: Uri, line: String)
    suspend fun clearFile(uri: Uri)
    fun isWritable(uri: Uri): Boolean
}
