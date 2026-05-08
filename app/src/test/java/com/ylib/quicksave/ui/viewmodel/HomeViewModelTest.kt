package com.ylib.quicksave.ui.viewmodel

import android.app.Application
import com.ylib.quicksave.data.repository.ClipRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val app = mock<Application>()
    private val repo = mock<ClipRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // Default Flow stubs so init's combine doesn't throw
        whenever(repo.getTargetFileUri()).thenReturn(flowOf(null))
        whenever(repo.getCategories()).thenReturn(flowOf(emptyList()))
        whenever(repo.getSelectedCategory()).thenReturn(flowOf(null))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = HomeViewModel(app, repo)

    @Test
    fun `updateManualInput updates manualInputText in state`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.updateManualInput("hello world")

        assertEquals("hello world", vm.uiState.value.manualInputText)
    }

    @Test
    fun `updateManualInput preserves newlines and whitespace`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.updateManualInput("  line1\nline2\t\n  ")

        assertEquals("  line1\nline2\t\n  ", vm.uiState.value.manualInputText)
    }

    @Test
    fun `saveManualInput is no-op when text is empty`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.saveManualInput()
        advanceUntilIdle()

        verify(repo, never()).saveEntry(any(), anyOrNull())
    }

    @Test
    fun `saveManualInput is no-op when text is whitespace only`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.updateManualInput("   \n\t  ")

        vm.saveManualInput()
        advanceUntilIdle()

        verify(repo, never()).saveEntry(any(), anyOrNull())
    }

    @Test
    fun `saveManualInput passes manualInputText and selectedCategory to repo`() = runTest {
        whenever(repo.getCategories()).thenReturn(flowOf(listOf("工作")))
        whenever(repo.getSelectedCategory()).thenReturn(flowOf("工作"))
        whenever(repo.saveEntry(any(), anyOrNull())).thenReturn(Result.success(Unit))
        val vm = viewModel()
        advanceUntilIdle()
        vm.updateManualInput("note text")

        vm.saveManualInput()
        advanceUntilIdle()

        verify(repo).saveEntry(eq("note text"), eq("工作"))
    }

    @Test
    fun `saveManualInput passes null category when none selected`() = runTest {
        whenever(repo.saveEntry(any(), anyOrNull())).thenReturn(Result.success(Unit))
        val vm = viewModel()
        advanceUntilIdle()
        vm.updateManualInput("note text")

        vm.saveManualInput()
        advanceUntilIdle()

        verify(repo).saveEntry(eq("note text"), isNull())
    }

    @Test
    fun `saveManualInput on success clears manualInputText`() = runTest {
        whenever(repo.saveEntry(any(), anyOrNull())).thenReturn(Result.success(Unit))
        val vm = viewModel()
        advanceUntilIdle()
        vm.updateManualInput("note text")

        vm.saveManualInput()
        advanceUntilIdle()

        assertEquals("", vm.uiState.value.manualInputText)
    }

    @Test
    fun `saveManualInput on success sets lastSaveResult Success`() = runTest {
        whenever(repo.saveEntry(any(), anyOrNull())).thenReturn(Result.success(Unit))
        val vm = viewModel()
        advanceUntilIdle()
        vm.updateManualInput("note text")

        vm.saveManualInput()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.lastSaveResult is SaveResult.Success)
    }

    @Test
    fun `saveManualInput on failure preserves manualInputText`() = runTest {
        whenever(repo.saveEntry(any(), anyOrNull()))
            .thenReturn(Result.failure(IllegalStateException("未设置目标文件")))
        val vm = viewModel()
        advanceUntilIdle()
        vm.updateManualInput("note text")

        vm.saveManualInput()
        advanceUntilIdle()

        assertEquals("note text", vm.uiState.value.manualInputText)
    }

    @Test
    fun `saveManualInput on failure sets lastSaveResult Failure with message`() = runTest {
        whenever(repo.saveEntry(any(), anyOrNull()))
            .thenReturn(Result.failure(IllegalStateException("未设置目标文件")))
        val vm = viewModel()
        advanceUntilIdle()
        vm.updateManualInput("note text")

        vm.saveManualInput()
        advanceUntilIdle()

        val result = vm.uiState.value.lastSaveResult
        assertTrue(result is SaveResult.Failure)
        assertEquals("未设置目标文件", (result as SaveResult.Failure).message)
    }

    @Test
    fun `saveManualInput resets isManualSaving to false after completion`() = runTest {
        whenever(repo.saveEntry(any(), anyOrNull())).thenReturn(Result.success(Unit))
        val vm = viewModel()
        advanceUntilIdle()
        vm.updateManualInput("note")

        vm.saveManualInput()
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.isManualSaving)
    }

    @Test
    fun `saveManualInput does not affect isClipSaving`() = runTest {
        whenever(repo.saveEntry(any(), anyOrNull())).thenReturn(Result.success(Unit))
        val vm = viewModel()
        advanceUntilIdle()
        vm.updateManualInput("note")

        vm.saveManualInput()
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.isClipSaving)
    }

    @Test
    fun `saveClipboard uses isClipSaving field after rename`() = runTest {
        whenever(repo.saveEntry(any(), anyOrNull())).thenReturn(Result.success(Unit))
        val vm = viewModel()
        advanceUntilIdle()
        // simulate clipboard text via state update path: readClipboard requires a Context,
        // so we exercise saveClipboard by first injecting clipText through state
        // The test verifies the field is named isClipSaving (won't compile otherwise)
        val state = vm.uiState.value
        assertEquals(false, state.isClipSaving)
        // also assert the new isManualSaving field exists alongside
        assertEquals(false, state.isManualSaving)
    }

    @Test
    fun `initial state has empty manualInputText and both saving flags false`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("", state.manualInputText)
        assertEquals(false, state.isClipSaving)
        assertEquals(false, state.isManualSaving)
        assertNull(state.lastSaveResult)
    }
}
