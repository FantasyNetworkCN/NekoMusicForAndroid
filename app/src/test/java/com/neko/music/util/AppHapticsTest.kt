package com.neko.music.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AppHapticsTest {
    @Test
    fun mapsStrengthToVibrationAmplitude() {
        assertEquals(64, AppHaptics.amplitudeFor(10))
        assertEquals(148, AppHaptics.amplitudeFor(50))
        assertEquals(255, AppHaptics.amplitudeFor(100))
    }

    @Test
    fun scalesPulseDurationWithStrength() {
        assertEquals(22L, AppHaptics.durationFor(10))
        assertEquals(60L, AppHaptics.durationFor(100))
        assertEquals(38L, AppHaptics.durationFor(10, longPress = true))
    }

    @Test
    fun clampsStrengthToSupportedRange() {
        assertEquals(AppHaptics.amplitudeFor(10), AppHaptics.amplitudeFor(0))
        assertEquals(AppHaptics.amplitudeFor(100), AppHaptics.amplitudeFor(120))
    }
}
