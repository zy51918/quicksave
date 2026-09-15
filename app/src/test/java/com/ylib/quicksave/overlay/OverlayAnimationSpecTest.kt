package com.ylib.quicksave.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayAnimationSpecTest {
    @Test
    fun pressedHandleUsesThreeTimesScale() {
        assertEquals(3f, OverlayAnimationSpec.PRESS_SCALE, 0f)
        assertEquals(OverlayAnimationSpec.RELEASE_SCALE, 1f, 0f)
    }

    @Test
    fun holdStateGrowsBiggerThanPressStateButFitsTouchWindow() {
        assertTrue("长按蓄力应比按压更大", OverlayAnimationSpec.HOLD_SCALE > OverlayAnimationSpec.PRESS_SCALE)
        assertEquals(5f, OverlayAnimationSpec.HOLD_SCALE, 0f)
        assertEquals(150L, OverlayAnimationSpec.HOLD_DURATION_MS)
        // 蓄力宽度不得超出 25dp 触控窗（5dp×HOLD_SCALE），否则被窗口裁剪
        assertTrue(
            OverlayAnimationSpec.HOLD_SCALE * OverlayHandleSpec.WIDTH_DP <= OverlayHandleSpec.TOUCH_WIDTH_DP
        )
    }
}
