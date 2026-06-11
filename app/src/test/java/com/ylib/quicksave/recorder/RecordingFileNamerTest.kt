package com.ylib.quicksave.recorder

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class RecordingFileNamerTest {

    @Test
    fun `name matches QS timestamp m4a pattern`() {
        val result = RecordingFileNamer.name(Date(0L))
        assertTrue(
            "应形如 QS_yyyyMMdd_HHmmss.m4a，实际：$result",
            Regex("""QS_\d{8}_\d{6}\.m4a""").matches(result)
        )
    }

    @Test
    fun `name starts with QS_ and ends with m4a`() {
        val result = RecordingFileNamer.name(Date(1_700_000_000_000L))
        assertTrue(result.startsWith("QS_"))
        assertTrue(result.endsWith(".m4a"))
    }

    @Test
    fun `different times produce timestamped names of fixed length`() {
        // "QS_"(3) + 8 + "_"(1) + 6 + ".m4a"(4) = 22
        assertTrue(RecordingFileNamer.name(Date(0L)).length == 22)
    }
}
