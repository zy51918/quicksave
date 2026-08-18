package com.ylib.quicksave.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ylib.quicksave.R
import com.ylib.quicksave.app.QuickSaveApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class ShareReceiverActivity : ComponentActivity() {

    private var handled = false
    private var resultShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntentOnce(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentOnce(intent)
    }

    private fun handleIntentOnce(intent: Intent) {
        if (handled) return
        handled = true

        try {
            when (
                val result = ShareContentParser.parse(
                    action = intent.action,
                    mimeType = intent.type,
                    text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT),
                    hasExtraStream = intent.hasExtra(Intent.EXTRA_STREAM),
                    hasUriClipData = intent.clipData?.let { clipData ->
                        (0 until clipData.itemCount).any { index ->
                            clipData.getItemAt(index).uri != null
                        }
                    } == true,
                    hasMultipleClipItems = intent.clipData?.itemCount?.let { it > 1 } == true
                )
            ) {
                is ShareParseResult.Success -> save(result.text)
                ShareParseResult.Empty -> finishWithToast(
                    ShareMessage.Resource(R.string.share_error_empty_content)
                )
                ShareParseResult.Unsupported -> finishWithToast(
                    ShareMessage.Resource(R.string.share_error_unsupported_content)
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            finishWithToast(ShareMessage.Resource(R.string.share_error_generic))
        }
    }

    private fun save(text: String) {
        val coordinator = ShareSaveCoordinator(
            (application as QuickSaveApplication).clipRepository
        )
        val viewModel = ViewModelProvider(
            this,
            ShareReceiverViewModel.factory(coordinator::save)
        )[ShareReceiverViewModel::class.java]

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    if (state is ShareReceiverState.Completed && !resultShown) {
                        resultShown = true
                        showSaveResult(state.result)
                    }
                }
            }
        }
        viewModel.save(text)
    }

    private fun showSaveResult(result: Result<Unit>) {
        result.fold(
            onSuccess = {
                finishWithToast(ShareMessage.Resource(R.string.share_saved))
            },
            onFailure = { error ->
                finishWithToast(errorMessage(error))
            }
        )
    }

    private fun errorMessage(error: Throwable?): ShareMessage {
        val message = error?.message
        return when {
            message == "未设置目标文件" -> {
                ShareMessage.Resource(R.string.share_error_no_target_file)
            }
            message.isNullOrBlank() -> {
                ShareMessage.Resource(R.string.share_error_generic)
            }
            else -> ShareMessage.Text(message)
        }
    }

    private fun finishWithToast(message: ShareMessage) {
        val text = when (message) {
            is ShareMessage.Resource -> getString(message.id)
            is ShareMessage.Text -> message.value
        }
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        finish()
    }

    private sealed interface ShareMessage {
        data class Resource(@StringRes val id: Int) : ShareMessage
        data class Text(val value: String) : ShareMessage
    }
}
