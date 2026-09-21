package com.ylib.quicksave.share

import com.ylib.quicksave.data.repository.ClipRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ShareSaveCoordinatorTest {

    private val repository = mock<ClipRepository>()
    private val coordinator = ShareSaveCoordinator(repository)

    @Test
    fun `save uses currently selected category`() = runTest {
        whenever(repository.getSelectedCategory()).thenReturn(flowOf("工作"))
        whenever(repository.saveEntry("正文", "工作"))
            .thenReturn(Result.success(Unit))

        val result = coordinator.save("正文")

        assertTrue(result.isSuccess)
        verify(repository).saveEntry("正文", "工作")
    }

    @Test
    fun `save passes null when no category is selected`() = runTest {
        whenever(repository.getSelectedCategory()).thenReturn(flowOf(null))
        whenever(repository.saveEntry("正文", null))
            .thenReturn(Result.success(Unit))

        val result = coordinator.save("正文")

        assertTrue(result.isSuccess)
        verify(repository).saveEntry("正文", null)
    }

    @Test
    fun `save propagates repository failure`() = runTest {
        val failure = IllegalStateException("未设置目标文件")
        whenever(repository.getSelectedCategory()).thenReturn(flowOf(null))
        whenever(repository.saveEntry("正文", null))
            .thenReturn(Result.failure(failure))

        val result = coordinator.save("正文")

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }
}
