package com.ylib.quicksave.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPanelSpecTest {
    @Test
    fun actionButtonsAreCompactWithoutChangingContentSpacing() {
        assertEquals(68, OverlayPanelSpec.ACTION_WIDTH_DP)
        assertEquals(56, OverlayPanelSpec.ACTION_HEIGHT_DP)
        assertEquals(20, OverlayPanelSpec.ICON_SIZE_DP)
        assertEquals(3, OverlayPanelSpec.ICON_BOTTOM_MARGIN_DP)
    }
}
