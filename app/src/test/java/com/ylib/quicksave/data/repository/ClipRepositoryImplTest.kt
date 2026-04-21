package com.ylib.quicksave.data.repository

import android.net.Uri
import com.ylib.quicksave.data.model.ClipEntry
import com.ylib.quicksave.data.source.AppDataStore
import com.ylib.quicksave.data.source.FileDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ---------------------------------------------------------------------------
// Fakes
// ---------------------------------------------------------------------------

/**
 * 内存版 AppDataStore，实现接口，不依赖任何 Android 框架类。
 */
class FakeAppDataStore : AppDataStore {

    private var storedUri: String? = null
    private var storedEntries: List<ClipEntry> = emptyList()

    override fun getTargetFileUri(): Flow<String?> = flowOf(storedUri)

    override fun getRecentEntries(): Flow<List<ClipEntry>> = flowOf(storedEntries)

    override suspend fun saveTargetFileUri(uri: String) {
        storedUri = uri
    }

    override suspend fun saveRecentEntries(entries: List<ClipEntry>) {
        storedEntries = entries
    }

    // --- 测试辅助 ---
    fun setUri(uri: String?) {
        storedUri = uri
    }

    fun setEntries(entries: List<ClipEntry>) {
        storedEntries = entries
    }

    fun getEntries(): List<ClipEntry> = storedEntries
}

/**
 * 内存版 FileDataSource。
 *
 * - [writableResult]：控制 isWritable 返回值（默认 true）
 * - [appendLineException]：若非 null，appendLine 抛出该异常
 * - [appendedLines]：记录所有 appendLine 的调用参数
 */
class FakeFileDataSource : FileDataSource {
    var writableResult: Boolean = true
    var appendLineException: Exception? = null
    val appendedLines = mutableListOf<Pair<Uri, String>>()

    override fun isWritable(uri: Uri): Boolean = writableResult

    override suspend fun appendLine(uri: Uri, line: String) {
        appendLineException?.let { throw it }
        appendedLines.add(uri to line)
    }
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class ClipRepositoryImplTest {

    private lateinit var fakeDataStore: FakeAppDataStore
    private lateinit var fakeFileDataSource: FakeFileDataSource
    private lateinit var repository: ClipRepositoryImpl

    @Before
    fun setUp() {
        fakeDataStore = FakeAppDataStore()
        fakeFileDataSource = FakeFileDataSource()
        repository = ClipRepositoryImpl(fakeDataStore, fakeFileDataSource)
    }

    // -----------------------------------------------------------------------
    // saveEntry 测试
    // -----------------------------------------------------------------------

    @Test
    fun saveEntry_whenNoTargetUri_returnsFailure() = runTest {
        fakeDataStore.setUri(null)

        val result = repository.saveEntry("hello")

        assertFalse("应返回 failure", result.isSuccess)
        assertTrue(
            "异常类型应为 IllegalStateException",
            result.exceptionOrNull() is IllegalStateException
        )
    }

    @Test
    fun saveEntry_whenNotWritable_returnsFailure() = runTest {
        fakeDataStore.setUri("content://com.example/file")
        fakeFileDataSource.writableResult = false

        val result = repository.saveEntry("hello")

        assertFalse("应返回 failure", result.isSuccess)
        assertTrue(
            "异常类型应为 SecurityException",
            result.exceptionOrNull() is SecurityException
        )
    }

    @Test
    fun saveEntry_whenWritable_appendsLineAndUpdatesCache() = runTest {
        val uriString = "content://com.example/file"
        fakeDataStore.setUri(uriString)
        fakeFileDataSource.writableResult = true

        val result = repository.saveEntry("clipboard text")

        // 返回成功
        assertTrue("应返回 success", result.isSuccess)

        // appendLine 被调用一次，且包含原始文本
        assertEquals("appendLine 应只调用一次", 1, fakeFileDataSource.appendedLines.size)
        val (calledUri, calledLine) = fakeFileDataSource.appendedLines.first()
        assertEquals(Uri.parse(uriString), calledUri)
        assertTrue("行内容应包含原始文本", calledLine.contains("clipboard text"))

        // 缓存最新一条文本正确
        val entries = fakeDataStore.getEntries()
        assertFalse("缓存不应为空", entries.isEmpty())
        assertEquals("缓存第一条文本应匹配", "clipboard text", entries.first().text)
    }

    @Test
    fun saveEntry_catchesException_returnsFailure() = runTest {
        fakeDataStore.setUri("content://com.example/file")
        fakeFileDataSource.writableResult = true
        fakeFileDataSource.appendLineException = RuntimeException("IO error")

        val result = repository.saveEntry("text")

        assertFalse("应返回 failure", result.isSuccess)
        assertNotNull("应包含异常", result.exceptionOrNull())
        assertEquals("IO error", result.exceptionOrNull()?.message)
    }

    // -----------------------------------------------------------------------
    // getRecentEntries 测试
    // -----------------------------------------------------------------------

    @Test
    fun getRecentEntries_limitsResults() = runTest {
        // 预置 5 条记录
        val allEntries = (1..5).map { ClipEntry(text = "entry$it", savedAt = it.toLong()) }
        fakeDataStore.setEntries(allEntries)

        val collected = mutableListOf<List<ClipEntry>>()
        repository.getRecentEntries(limit = 3).collect { collected.add(it) }

        assertEquals("Flow 应发射一次", 1, collected.size)
        assertEquals("应限制为 3 条", 3, collected.first().size)
        assertEquals("第一条应为 entry1", "entry1", collected.first().first().text)
    }
}
