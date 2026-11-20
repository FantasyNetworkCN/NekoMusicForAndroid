package com.neko.music.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AppHapticsTest {
    @Test
    fun mapsStrengthToVibrationAmplitude() {
        assertEquals(26, AppHaptics.amplitudeFor(10))
        assertEquals(128, AppHaptics.amplitudeFor(50))
        assertEquals(255, AppHaptics.amplitudeFor(100))
    }

    @Test
    fun clampsStrengthToSupportedRange() {
        assertEquals(AppHaptics.amplitudeFor(10), AppHaptics.amplitudeFor(0))
        assertEquals(AppHaptics.amplitudeFor(100), AppHaptics.amplitudeFor(120))
    }
}
