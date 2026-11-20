package com.neko.music.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreSheetDragStateTest {
    @Test
    fun shortDragReturnsToTheOpenPosition() {
        assertFalse(shouldDismissMoreSheet(40f, 800f, 200f, 1f))
    }

    @Test
    fun longDragDismissesAtAnyScreenDensity() {
        assertTrue(shouldDismissMoreSheet(125f, 800f, 0f, 1f))
        assertTrue(shouldDismissMoreSheet(375f, 2400f, 0f, 3f))
    }

    @Test
    fun shortLandscapeSheetUsesAProportionalThreshold() {
        assertTrue(shouldDismissMoreSheet(85f, 320f, 0f, 1f))
        assertFalse(shouldDismissMoreSheet(60f, 320f, 0f, 1f))
    }

    @Test
    fun fastDownwardFlingDismissesAfterADeliberateDrag() {
        assertTrue(shouldDismissMoreSheet(20f, 800f, 1000f, 1f))
        assertFalse(shouldDismissMoreSheet(2f, 800f, 1000f, 1f))
    }

    @Test
    fun upwardFlingRestoresAnAlreadyDisplacedSheet() {
        assertFalse(shouldDismissMoreSheet(200f, 800f, -1000f, 1f))
    }

    @Test
    fun scrollingWithoutMovingTheSheetDoesNotDismissIt() {
        assertFalse(shouldDismissMoreSheet(0f, 800f, 4000f, 1f))
        assertFalse(shouldDismissMoreSheet(100f, 0f, 4000f, 1f))
    }
}
