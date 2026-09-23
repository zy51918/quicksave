package com.ylib.quicksave.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayClipboardPaletteTest {
    @Test
    fun lightPaletteUsesLimeActionWithDarkOliveContent() {
        val palette = OverlayClipboardPalette.forNightMode(false)
        assertEquals(0xB4DCE4E4.toInt(), palette.panelColor)
        assertEquals(0, palette.panelStrokeColor)
        assertEquals(0xFFC5E166.toInt(), palette.actionColor)
        assertEquals(0xFF2D370B.toInt(), palette.actionContentColor)
        assertEquals(0xFF465511.toInt(), palette.panelContentColor)
    }

    @Test
    fun darkPaletteUsesPaleLimeActionForLegibility() {
        val palette = OverlayClipboardPalette.forNightMode(true)
        assertEquals(0xB4DCE4E4.toInt(), palette.panelColor)
        assertEquals(0, palette.panelStrokeColor)
        assertEquals(0xFFEDF6D0.toInt(), palette.actionColor)
        assertEquals(0xFF102A36.toInt(), palette.actionContentColor)
        assertEquals(0xFF465511.toInt(), palette.panelContentColor)
    }

    @Test
    fun panelContentStaysDarkOnTheTranslucentLightPanelInBothThemes() {
        // 面板在两种主题下都是半透明浅灰，浅色内容在其上不可读，故两套都必须用深色。
        val light = OverlayClipboardPalette.forNightMode(false)
        val dark = OverlayClipboardPalette.forNightMode(true)
        assertEquals(light.panelContentColor, dark.panelContentColor)
    }
}