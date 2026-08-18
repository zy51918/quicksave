package com.ylib.quicksave.share

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.ylib.quicksave.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareReceiverActivityTest {

    @Test
    fun unsupportedMime_finishesShareReceiverWithoutOpeningHome() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val intent = Intent(context, ShareReceiverActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/html"
            putExtra(Intent.EXTRA_TEXT, "<p>不支持</p>")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val activity = instrumentation.startActivitySync(intent)
        instrumentation.waitForIdleSync()

        var hasResumedMainActivity = false
        instrumentation.runOnMainSync {
            hasResumedMainActivity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .any { it is MainActivity }
        }

        assertTrue(activity.isFinishing || activity.isDestroyed)
        assertFalse(hasResumedMainActivity)
    }
}
