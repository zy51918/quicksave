package com.ylib.quicksave.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardSaveTriggerTest {

    @Test
    fun `clipboard read waits for window focus and only runs once`() {
        assertFalse(ClipboardSaveTrigger.shouldStart(hasWindowFocus = false, handled = false))
        assertTrue(ClipboardSaveTrigger.shouldStart(hasWindowFocus = true, handled = false))
        assertFalse(ClipboardSaveTrigger.shouldStart(hasWindowFocus = true, handled = true))
    }
}
