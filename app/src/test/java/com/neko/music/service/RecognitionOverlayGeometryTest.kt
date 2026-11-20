package com.neko.music.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionOverlayGeometryTest {
    @Test
    fun expandedPanelStaysInsideSystemBarsAndCutout() {
        val position = recognitionOverlayPlacement(1080, 2400, 0, 96, 0, 72, 912, 528, 950, -100, 24)
        assertEquals(144, position.x)
        assertEquals(120, position.y)
        assertEquals(912, position.width)
        assertEquals(528, position.height)
    }

    @Test
    fun landscapePanelShrinksToFitTheAvailableHeight() {
        val position = recognitionOverlayPlacement(2400, 800, 80, 0, 0, 60, 912, 792, 50, 600, 24)
        assertEquals(104, position.x)
        assertEquals(24, position.y)
        assertEquals(692, position.height)
        assertTrue(position.y + position.height <= 716)
    }

    @Test
    fun narrowMultiWindowDoesNotPlaceControlsOffScreen() {
        val position = recognitionOverlayPlacement(240, 400, 0, 30, 0, 24, 304, 176, 999, 999, 8)
        assertEquals(224, position.width)
        assertEquals(8, position.x)
        assertEquals(192, position.y)
    }

    @Test
    fun edgeSnappingUsesTheSameSafeBoundsAsDragging() {
        val left = recognitionOverlayPlacement(400, 800, 12, 24, 16, 24, 56, 56, Int.MIN_VALUE, 90, 8)
        val right = recognitionOverlayPlacement(400, 800, 12, 24, 16, 24, 56, 56, Int.MAX_VALUE, 90, 8)
        assertEquals(20, left.x)
        assertEquals(320, right.x)
        assertEquals(left.y, right.y)
    }

    @Test
    fun invalidOrTransientWindowMetricsStillYieldAValidRectangle() {
        val position = recognitionOverlayPlacement(0, 0, 100, 100, 100, 100, 56, 56, 99, 99, 8)
        assertEquals(RecognitionOverlayPlacement(0, 0, 1, 1), position)
    }
}
