package com.ylib.quicksave.data.repository

import com.ylib.quicksave.data.source.AppDataStore
import com.ylib.quicksave.overlay.OverlayEdge
import com.ylib.quicksave.overlay.OverlayPosition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OverlayRepositoryImplTest {

    private val dataStore = mock<AppDataStore>()
    private val repo = OverlayRepositoryImpl(dataStore)

    @Test
    fun `isEnabled passes through dataStore flow`() = runTest {
        whenever(dataStore.getOverlayEnabled()).thenReturn(flowOf(true))
        assertEquals(true, repo.isEnabled().first())
    }

    @Test
    fun `setEnabled delegates to dataStore`() = runTest {
        repo.setEnabled(true)
        verify(dataStore).saveOverlayEnabled(eq(true))
    }

    @Test
    fun `getPosition maps storage pair to OverlayPosition`() = runTest {
        whenever(dataStore.getOverlayPosition()).thenReturn(flowOf("LEFT" to 0.6f))
        val pos = repo.getPosition().first()
        assertEquals(OverlayEdge.LEFT, pos.edge)
        assertEquals(0.6f, pos.yRatio, 0.001f)
    }

    @Test
    fun `getPosition falls back to RIGHT for unknown edge string`() = runTest {
        whenever(dataStore.getOverlayPosition()).thenReturn(flowOf("garbage" to 0.1f))
        assertEquals(OverlayEdge.RIGHT, repo.getPosition().first().edge)
    }

    @Test
    fun `setPosition delegates edge name and ratio to dataStore`() = runTest {
        repo.setPosition(OverlayPosition(OverlayEdge.LEFT, 0.3f))
        verify(dataStore).saveOverlayPosition(eq("LEFT"), eq(0.3f))
    }
}
