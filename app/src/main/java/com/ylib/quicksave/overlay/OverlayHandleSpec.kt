package com.ylib.quicksave.overlay

/** 普通状态浮动把手的视觉参数。 */
internal object OverlayHandleSpec {
    const val WIDTH_DP = 5
    const val TOUCH_WIDTH_DP = 25
    const val NORMAL_ALPHA = 180
    const val NORMAL_RED = 175
    const val NORMAL_GREEN = 185
    const val NORMAL_BLUE = 185
    const val USE_ROUNDED_BACKGROUND = true

    fun cornerRadiusPx(widthPx: Int, heightPx: Int): Float =
        minOf(widthPx, heightPx) / 2f
}
