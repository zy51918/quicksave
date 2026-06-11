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
    fun `ratioToY converts fraction to pixel`() {
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
}
