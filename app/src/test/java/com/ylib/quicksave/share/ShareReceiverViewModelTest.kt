package com.ylib.quicksave.share

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShareReceiverViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `first save invokes action once and publishes success`() = runTest {
        var calls = 0
        val viewModel = ShareReceiverViewModel {
            calls++
            Result.success(Unit)
        }

        viewModel.save("first")
        advanceUntilIdle()

        assertEquals(1, calls)
        assertEquals(
            ShareReceiverState.Completed(Result.success(Unit)),
            viewModel.state.value
        )
    }

    @Test
    fun `second save while first is pending does not invoke action again`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val savedTexts = mutableListOf<String>()
        val viewModel = ShareReceiverViewModel { text ->
            savedTexts += text
            gate.await()
            Result.success(Unit)
        }

        viewModel.save("first")
        advanceUntilIdle()
        viewModel.save("second")
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("first"), savedTexts)
    }

    @Test
    fun `completed failure result remains available`() = runTest {
        val failure = IllegalStateException("保存失败")
        val viewModel = ShareReceiverViewModel {
            Result.failure(failure)
        }

        viewModel.save("first")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state is ShareReceiverState.Completed)
        assertEquals(Result.failure<Unit>(failure), (state as ShareReceiverState.Completed).result)
        assertEquals(state, viewModel.state.value)
    }

    @Test
    fun `unexpected exception becomes failure`() = runTest {
        val failure = IllegalStateException("unexpected")
        val viewModel = ShareReceiverViewModel {
            throw failure
        }

        viewModel.save("first")
        advanceUntilIdle()

        val state = viewModel.state.value as ShareReceiverState.Completed
        assertEquals(Result.failure<Unit>(failure), state.result)
    }
}
