package com.ylib.quicksave.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayHandleSpecTest {
    @Test
    fun normalHandleUsesExpandedTouchWidthAndDarkerGrayStyle() {
        assertEquals(5, OverlayHandleSpec.WIDTH_DP)
        assertEquals(25, OverlayHandleSpec.TOUCH_WIDTH_DP)
        assertEquals(175, OverlayHandleSpec.NORMAL_RED)
        assertEquals(185, OverlayHandleSpec.NORMAL_GREEN)
        assertEquals(185, OverlayHandleSpec.NORMAL_BLUE)
        assertEquals(180, OverlayHandleSpec.NORMAL_ALPHA)
        assertTrue("把手应使用圆角矩形背景", OverlayHandleSpec.USE_ROUNDED_BACKGROUND)
    }

    @Test
    fun cornerRadiusTracksCurrentWidth() {
        assertEquals(2.5f, OverlayHandleSpec.cornerRadiusPx(5, 64), 0f)
        assertEquals(12.5f, OverlayHandleSpec.cornerRadiusPx(25, 64), 0f)
    }
}
