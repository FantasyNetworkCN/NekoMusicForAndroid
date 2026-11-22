package com.neko.music.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun originalPlaybackDoesNotWaitForStemPreparation() {
        val track = StemTrackCache("music:1:hq")
        val source = StemTrackInference(track)
        assertTrue(source.cachePrepared)

        track.put(
            44_100,
            StemCachedChunk(
                startFrame = 0,
                vocals = StemCachedSamples.fromFloats(FloatArray(2)),
                accompaniment = StemCachedSamples.fromFloats(FloatArray(2)),
            ),
        )
        assertTrue(source.cachePrepared)
    }
}
