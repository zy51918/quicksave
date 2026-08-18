package com.ylib.quicksave.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayAnimationSpecTest {
    @Test
    fun pressedHandleUsesFiveTimesScale() {
        assertEquals(5f, OverlayAnimationSpec.PRESS_SCALE, 0f)
        assertEquals(OverlayAnimationSpec.RELEASE_SCALE, 1f, 0f)
    }
}
