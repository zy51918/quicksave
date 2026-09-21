package com.ylib.quicksave.overlay

import com.ylib.quicksave.ui.ClipboardSaveActivity
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayServiceTest {

    @Test
    fun `clipboard save action targets foreground clipboard save activity`() {
        assertEquals(
            ClipboardSaveActivity::class.java,
            OverlayService.clipboardSaveActivityClass()
        )
    }
}
