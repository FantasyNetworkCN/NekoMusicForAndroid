package com.neko.music.service

import org.junit.Assert.assertEquals
import org.junit.Test

class StemCachedSamplesTest {
    @Test
    fun floatSamplesAreStoredAsPcm16AndCanBeSliced() {
        val source = StemCachedSamples.fromFloats(
            floatArrayOf(
                0.25f, -0.5f,
                0.75f, -0.25f,
                0.5f, 0f,
            ),
        )

        assertEquals(3, source.frames)
        val slice = source.slice(startFrame = 1, frameCount = 2)
        assertEquals(2, slice.frames)
        assertEquals(0.75f, slice.sample(0, 0), 0.0001f)
        assertEquals(-0.25f, slice.sample(0, 1), 0.0001f)
        assertEquals(0.5f, slice.sample(1, 0), 0.0001f)
        assertEquals(0f, slice.sample(1, 1), 0.0001f)
    }
}
