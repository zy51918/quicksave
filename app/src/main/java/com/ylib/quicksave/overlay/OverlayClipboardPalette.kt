package com.ylib.quicksave.overlay

/** 与主页面剪切板卡片一致的悬浮框配色。 */
data class OverlayClipboardColors(
    val panelColor: Int,
    val panelContentColor: Int,
    val panelStrokeColor: Int,
    val actionColor: Int,
    val actionContentColor: Int
)

internal object OverlayClipboardPalette {
    fun forNightMode(isNight: Boolean): OverlayClipboardColors = if (isNight) {
        OverlayClipboardColors(
            panelColor = 0xB4DCE4E4.toInt(),
            panelContentColor = 0xFFBCE9E4.toInt(),
            panelStrokeColor = 0,
            actionColor = 0xFFBCE9E4.toInt(),
            actionContentColor = 0xFF102A36.toInt()
        )
    } else {
        OverlayClipboardColors(
            panelColor = 0xB4DCE4E4.toInt(),
            panelContentColor = 0xFF003735.toInt(),
            panelStrokeColor = 0,
            actionColor = 0xFF087F7B.toInt(),
            actionContentColor = 0xFFFFFFFF.toInt()
        )
    }
}
