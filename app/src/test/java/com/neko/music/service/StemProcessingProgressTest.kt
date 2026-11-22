package com.neko.music.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StemProcessingProgressTest {
    @Test
    fun fractionTracksCompletedServerChunks() {
        assertNull(StemProcessingProgress().fraction)
        val fraction = StemProcessingProgress(completedChunks = 3, totalChunks = 6).fraction
        assertEquals(
            0.5f,
            fraction!!,
            0f,
        )
    }
}
