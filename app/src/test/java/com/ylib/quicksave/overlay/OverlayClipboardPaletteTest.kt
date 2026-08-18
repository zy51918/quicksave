package com.ylib.quicksave.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayClipboardPaletteTest {
    @Test
    fun lightPaletteUsesLighterBorderlessPanelAndClipboardColorsForActions() {
        val palette = OverlayClipboardPalette.forNightMode(false)
        assertEquals(0xB4DCE4E4.toInt(), palette.panelColor)
        assertEquals(0, palette.panelStrokeColor)
        assertEquals(0xFF087F7B.toInt(), palette.actionColor)
        assertEquals(0xFFFFFFFF.toInt(), palette.actionContentColor)
        assertEquals(0xFF003735.toInt(), palette.panelContentColor)
    }

    @Test
    fun darkPaletteUsesLighterBorderlessPanelAndClipboardColorsForActions() {
        val palette = OverlayClipboardPalette.forNightMode(true)
        assertEquals(0xB4DCE4E4.toInt(), palette.panelColor)
        assertEquals(0, palette.panelStrokeColor)
        assertEquals(0xFFBCE9E4.toInt(), palette.actionColor)
        assertEquals(0xFF102A36.toInt(), palette.actionContentColor)
        assertEquals(0xFFBCE9E4.toInt(), palette.panelContentColor)
    }
}
