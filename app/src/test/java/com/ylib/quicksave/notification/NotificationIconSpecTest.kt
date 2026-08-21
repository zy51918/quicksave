package com.ylib.quicksave.notification

import com.ylib.quicksave.R
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationIconSpecTest {

    @Test
    fun `status notifications use the dedicated notification icon`() {
        assertEquals(R.drawable.ic_notification_quicksave, NotificationIconSpec.smallIcon)
    }
}
