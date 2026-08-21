package com.ylib.quicksave.notification

import com.ylib.quicksave.R
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationIconSpecTest {

    @Test
    fun `status notifications use the current launcher foreground icon`() {
        assertEquals(R.drawable.ic_launcher_foreground, NotificationIconSpec.smallIcon)
    }
}
