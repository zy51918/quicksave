package com.ylib.quicksave.share

import android.content.Intent

sealed interface ShareParseResult {
    data class Success(val text: String) : ShareParseResult
    data object Unsupported : ShareParseResult
    data object Empty : ShareParseResult
}

object ShareContentParser {
    fun parse(
        action: String?,
        mimeType: String?,
        text: CharSequence?
    ): ShareParseResult {
        if (action != Intent.ACTION_SEND || mimeType != "text/plain") {
            return ShareParseResult.Unsupported
        }

        if (text == null || text.isBlank()) {
            return ShareParseResult.Empty
        }

        return ShareParseResult.Success(text.toString())
    }
}
