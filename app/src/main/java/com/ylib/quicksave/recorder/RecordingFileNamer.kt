package com.ylib.quicksave.recorder

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 录音文件名生成：QS_yyyyMMdd_HHmmss.m4a（本地时区）。纯逻辑，便于单测。 */
object RecordingFileNamer {
    private const val PREFIX = "QS_"
    private const val EXTENSION = ".m4a"

    fun name(date: Date): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(date)
        return "$PREFIX$stamp$EXTENSION"
    }
}
