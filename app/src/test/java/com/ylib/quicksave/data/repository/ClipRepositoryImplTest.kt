package com.ylib.quicksave.data.repository

import android.net.Uri
import com.ylib.quicksave.data.source.AppDataStore
import com.ylib.quicksave.data.source.FileDataSource
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ClipRepositoryImplTest {

    private val dataStore = mock<AppDataStore>()
    private val fileDataSource = mock<FileDataSource>()
    private val repo = ClipRepositoryImpl(dataStore, fileDataSource)

    @Test
    fun `saveEntry with category prepends category tag before timestamp`() = runTest {
        whenever(dataStore.getTargetFileUri()).thenReturn(flowOf("content://test/file"))
        whenever(fileDataSource.isWritable(any())).thenReturn(true)

        repo.saveEntry("hello world", category = "工作")

        verify(fileDataSource).appendLine(any(), argThat { startsWith("[工作][") })
    }

    @Test
    fun `saveEntry without category writes timestamp-only prefix`() = runTest {
        whenever(dataStore.getTargetFileUri()).thenReturn(flowOf("content://test/file"))
        whenever(fileDataSource.isWritable(any())).thenReturn(true)

        repo.saveEntry("hello world", category = null)

        verify(fileDataSource).appendLine(any(), argThat { startsWith("[20") && !contains("][20") })
    }

    @Test
    fun `saveEntry returns failure when no target URI set`() = runTest {
        whenever(dataStore.getTargetFileUri()).thenReturn(flowOf(null))

        val result = repo.saveEntry("hello", category = null)

        assertTrue(result.isFailure)
        assertEquals("未设置目标文件", result.exceptionOrNull()?.message)
    }

    @Test
    fun `saveEntry returns failure when file not writable`() = runTest {
        whenever(dataStore.getTargetFileUri()).thenReturn(flowOf("content://test/file"))
        whenever(fileDataSource.isWritable(any())).thenReturn(false)

        val result = repo.saveEntry("hello", category = null)

        assertTrue(result.isFailure)
        assertEquals("目标文件无写入权限，请重新选择", result.exceptionOrNull()?.message)
    }

    @Test
    fun `saveEntry with category returns success`() = runTest {
        whenever(dataStore.getTargetFileUri()).thenReturn(flowOf("content://test/file"))
        whenever(fileDataSource.isWritable(any())).thenReturn(true)

        val result = repo.saveEntry("hello world", category = "工作")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `saveEntry propagates appendLine exception as failure`() = runTest {
        whenever(dataStore.getTargetFileUri()).thenReturn(flowOf("content://test/file"))
        whenever(fileDataSource.isWritable(any())).thenReturn(true)
        whenever(fileDataSource.appendLine(any(), any())).thenThrow(RuntimeException("IO error"))

        val result = repo.saveEntry("text", category = null)

        assertTrue(result.isFailure)
        assertEquals("IO error", result.exceptionOrNull()?.message)
    }
}
