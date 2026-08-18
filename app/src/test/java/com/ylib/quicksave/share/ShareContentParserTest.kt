package com.ylib.quicksave.share

import android.content.Intent
import org.junit.Test
import org.junit.Assert.assertEquals

class ShareContentParserTest {

    @Test
    fun `accepts a non blank ACTION_SEND plain text`() {
        val result = ShareContentParser.parse(
            action = Intent.ACTION_SEND,
            mimeType = "text/plain",
            text = "  https://example.com  "
        )

        assertEquals(
            ShareParseResult.Success("  https://example.com  "),
            result
        )
    }

    @Test
    fun `preserves line breaks and spaces`() {
        val text = "标题\n\n  code = true  "

        assertEquals(
            ShareParseResult.Success(text),
            ShareContentParser.parse(Intent.ACTION_SEND, "text/plain", text)
        )
    }

    @Test
    fun `rejects wrong action`() {
        assertEquals(
            ShareParseResult.Unsupported,
            ShareContentParser.parse(Intent.ACTION_SEND_MULTIPLE, "text/plain", "text")
        )
    }

    @Test
    fun `rejects wrong mime type`() {
        assertEquals(
            ShareParseResult.Unsupported,
            ShareContentParser.parse(Intent.ACTION_SEND, "text/html", "text")
        )
    }

    @Test
    fun `rejects missing or blank text`() {
        assertEquals(
            ShareParseResult.Empty,
            ShareContentParser.parse(Intent.ACTION_SEND, "text/plain", null)
        )
        assertEquals(
            ShareParseResult.Empty,
            ShareContentParser.parse(Intent.ACTION_SEND, "text/plain", " \n\t")
        )
    }
}
