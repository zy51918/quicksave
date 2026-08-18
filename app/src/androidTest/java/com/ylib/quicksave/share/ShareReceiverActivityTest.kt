package com.ylib.quicksave.share

import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
    fun manifestDiscoversShareReceiverForPlainTextSend() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")

        val matches = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )

        assertTrue(
            matches.any {
                it.activityInfo.packageName == context.packageName &&
                    it.activityInfo.name == ShareReceiverActivity::class.java.name
            }
        )
    }

    @Test
    fun validPlainTextWithoutTargetFinishesWithoutOpeningHome() {
        val activity = launchReceiver(
            Intent.ACTION_SEND,
            "text/plain",
            "没有目标文件"
        )

        assertFinishesWithoutOpeningHome(activity)
    }

    @Test
    fun mixedStreamPayloadFinishesUnsupportedWithoutSavingOrOpeningHome() {
        val intent = baseIntent().apply {
            putExtra(Intent.EXTRA_STREAM, Uri.parse("content://example/stream"))
        }

        val activity = launchReceiver(intent)

        assertFinishesWithoutOpeningHome(activity)
    }

    @Test
    fun mixedUriClipDataPayloadFinishesUnsupportedWithoutSavingOrOpeningHome() {
        val intent = baseIntent().apply {
            clipData = ClipData.newRawUri("shared uri", Uri.parse("content://example/clip"))
        }

        val activity = launchReceiver(intent)

        assertFinishesWithoutOpeningHome(activity)
    }

    @Test
    fun unsupportedMime_finishesShareReceiverWithoutOpeningHome() {
        val activity = launchReceiver(
            Intent.ACTION_SEND,
            "text/html",
            "<p>不支持</p>"
        )

        assertFinishesWithoutOpeningHome(activity)
    }

    private fun launchReceiver(
        action: String,
        mimeType: String,
        text: String
    ): ShareReceiverActivity = launchReceiver(
        baseIntent(action, mimeType, text)
    )

    private fun launchReceiver(intent: Intent): ShareReceiverActivity {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        return instrumentation.startActivitySync(intent) as ShareReceiverActivity
    }

    private fun baseIntent(
        action: String = Intent.ACTION_SEND,
        mimeType: String = "text/plain",
        text: String = "有效分享文本"
    ): Intent {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return Intent(context, ShareReceiverActivity::class.java).apply {
            this.action = action
            type = mimeType
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun assertFinishesWithoutOpeningHome(activity: ShareReceiverActivity) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
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
