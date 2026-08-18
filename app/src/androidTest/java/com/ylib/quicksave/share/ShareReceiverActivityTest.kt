package com.ylib.quicksave.share

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareReceiverActivityTest {

    @Test
    fun unsupportedMime_finishesShareReceiverWithoutOpeningHome() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, ShareReceiverActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/html"
            putExtra(Intent.EXTRA_TEXT, "<p>不支持</p>")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val activity = InstrumentationRegistry.getInstrumentation()
            .startActivitySync(intent)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertTrue(activity.isFinishing || activity.isDestroyed)
        assertFalse(activity.javaClass.name.contains("MainActivity"))
    }
}
