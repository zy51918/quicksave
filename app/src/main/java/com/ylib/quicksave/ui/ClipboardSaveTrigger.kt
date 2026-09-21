package com.ylib.quicksave.ui

internal object ClipboardSaveTrigger {
    fun shouldStart(hasWindowFocus: Boolean, handled: Boolean): Boolean =
        hasWindowFocus && !handled
}
