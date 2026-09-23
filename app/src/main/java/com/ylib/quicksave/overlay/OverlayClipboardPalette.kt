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
    /**
     * 浅色主题：动作按钮用品牌浅绿填充（[Lime]），其上的图标/文字用深橄榄 [LimeInk]。
     * 深色主题：动作按钮改用浅色容器 [LimePale]，避开浅绿在深底上过亮造成的刺眼。
     *
     * 面板在两种主题下都是半透明浅灰，因此 [panelContentColor] 两套都用深色，否则不可读。
     */
    fun forNightMode(isNight: Boolean): OverlayClipboardColors = if (isNight) {
        OverlayClipboardColors(
            panelColor = 0xB4DCE4E4.toInt(),
            panelContentColor = 0xFF465511.toInt(),
            panelStrokeColor = 0,
            actionColor = 0xFFEDF6D0.toInt(),
            actionContentColor = 0xFF102A36.toInt()
        )
    } else {
        OverlayClipboardColors(
            panelColor = 0xB4DCE4E4.toInt(),
            panelContentColor = 0xFF465511.toInt(),
            panelStrokeColor = 0,
            actionColor = 0xFFC5E166.toInt(),
            actionContentColor = 0xFF2D370B.toInt()
        )
    }
}