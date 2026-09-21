package com.ylib.quicksave.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPositionCalculatorTest {

    @Test
    fun `nearestEdge centerX in left half returns LEFT`() {
        assertEquals(OverlayEdge.LEFT, OverlayPositionCalculator.nearestEdge(100, 1080))
    }

    @Test
    fun `nearestEdge centerX in right half returns RIGHT`() {
        assertEquals(OverlayEdge.RIGHT, OverlayPositionCalculator.nearestEdge(900, 1080))
    }

    @Test
    fun `nearestEdge at exact midpoint returns RIGHT`() {
        assertEquals(OverlayEdge.RIGHT, OverlayPositionCalculator.nearestEdge(540, 1080))
    }

    @Test
    fun `clampX keeps dragged window within screen`() {
        assertEquals(0, OverlayPositionCalculator.clampX(-50, viewWidth = 100, screenWidth = 1080))
        assertEquals(980, OverlayPositionCalculator.clampX(1200, viewWidth = 100, screenWidth = 1080))
        assertEquals(500, OverlayPositionCalculator.clampX(500, viewWidth = 100, screenWidth = 1080))
    }

    @Test
    fun `windowLeftForPointer preserves finger offset while dragging`() {
        assertEquals(380, OverlayPositionCalculator.windowLeftForPointer(500, 120, 1080, 100))
        assertEquals(0, OverlayPositionCalculator.windowLeftForPointer(50, 120, 1080, 100))
        assertEquals(980, OverlayPositionCalculator.windowLeftForPointer(1200, 120, 1080, 100))
    }

    @Test
    fun `edgeWindowLeft returns physical left coordinate for edge`() {
        assertEquals(0, OverlayPositionCalculator.edgeWindowLeft(OverlayEdge.LEFT, 1080, 100))
        assertEquals(980, OverlayPositionCalculator.edgeWindowLeft(OverlayEdge.RIGHT, 1080, 100))
    }

    @Test
    fun `clampY below zero returns zero`() {
        assertEquals(0, OverlayPositionCalculator.clampY(-50, viewHeight = 100, screenHeight = 1920))
    }

    @Test
    fun `clampY above max returns screenHeight minus viewHeight`() {
        assertEquals(1820, OverlayPositionCalculator.clampY(5000, viewHeight = 100, screenHeight = 1920))
    }

    @Test
    fun `clampY within range is unchanged`() {
        assertEquals(500, OverlayPositionCalculator.clampY(500, viewHeight = 100, screenHeight = 1920))
    }

    @Test
    fun `yToRatio converts pixel to fraction`() {
        assertEquals(0.25f, OverlayPositionCalculator.yToRatio(480, 1920), 0.001f)
    }

    @Test
    fun `ratioToY converts fraction to pixels`() {
        assertEquals(480, OverlayPositionCalculator.ratioToY(0.25f, 1920))
    }

    @Test
    fun `yToRatio clamps above range to 1`() {
        assertEquals(1f, OverlayPositionCalculator.yToRatio(5000, 1920), 0.001f)
    }

    @Test
    fun `yToRatio clamps below range to 0`() {
        assertEquals(0f, OverlayPositionCalculator.yToRatio(-100, 1920), 0.001f)
    }

    @Test
    fun `yToRatio with zero screenHeight returns zero`() {
        assertEquals(0f, OverlayPositionCalculator.yToRatio(100, 0), 0.001f)
    }

    @Test
    fun `ratioToY with zero screenHeight returns zero`() {
        assertEquals(0, OverlayPositionCalculator.ratioToY(0.5f, 0))
    }

    @Test
    fun `ratioToY restores equivalent position across orientation change`() {
        val ratio = OverlayPositionCalculator.yToRatio(1200, 2400)
        assertEquals(540, OverlayPositionCalculator.ratioToY(ratio, 1080))
    }

    @Test
    fun `ratioToY and yToRatio round-trip preserves ratio within new bounds`() {
        val ratio = OverlayPositionCalculator.yToRatio(800, 1920)
        val restoredRatio = OverlayPositionCalculator.yToRatio(
            OverlayPositionCalculator.ratioToY(ratio, 1080), 1080
        )
        assertEquals(ratio, restoredRatio, 0.01f)
    }
}
