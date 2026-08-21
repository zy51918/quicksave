package com.ylib.quicksave.ui

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ylib.quicksave.app.QuickSaveApplication
import com.ylib.quicksave.share.ShareSaveCoordinator
import kotlinx.coroutines.launch

/**
 * 前台保存 Activity：只有窗口真正获得焦点后才读取剪切板。
 */
class ClipboardSaveActivity : ComponentActivity() {

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        window.setDimAmount(0f)
        setContentView(FrameLayout(this))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (ClipboardSaveTrigger.shouldStart(hasFocus, handled)) {
            handled = true
            saveClipboard()
        }
    }

    private fun saveClipboard() {
        val text = readClipboardText()
        if (text == null) {
            finishWithToast("\u526a\u5207\u677f\u4e3a\u7a7a\uff0c\u8bf7\u5148\u590d\u5236\u6587\u5b57")
            return
        }

        val coordinator = ShareSaveCoordinator(
            (application as QuickSaveApplication).clipRepository
        )
        lifecycleScope.launch {
            val result = runCatching { coordinator.save(text).getOrThrow() }
            val message = when {
                result.isSuccess -> "\u5df2\u4fdd\u5b58\u526a\u5207\u677f\u5185\u5bb9"
                result.exceptionOrNull() is IllegalStateException ->
                    "\u8bf7\u5148\u5728\u8bbe\u7f6e\u4e2d\u9009\u62e9\u4fdd\u5b58\u6587\u4ef6"
                result.exceptionOrNull() is SecurityException ->
                    "\u6587\u4ef6\u65e0\u5199\u5165\u6743\u9650\uff0c\u8bf7\u91cd\u65b0\u9009\u62e9"
                else -> "\u4fdd\u5b58\u5931\u8d25\uff1a${result.exceptionOrNull()?.message ?: "\u672a\u77e5\u9519\u8bef"}"
            }
            Toast.makeText(
                this@ClipboardSaveActivity,
                message,
                if (result.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            ).show()
            finishWithoutAnimation()
        }
    }

    private fun readClipboardText(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return runCatching {
            clipboard.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.text
                ?.toString()
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun finishWithToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finishWithoutAnimation()
    }

    private fun finishWithoutAnimation() {
        overridePendingTransition(0, 0)
        finish()
    }
}
