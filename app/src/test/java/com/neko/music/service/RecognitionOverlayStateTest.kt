package com.neko.music.service

import com.neko.music.data.api.MusicApi
import com.neko.music.data.model.Music
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionOverlayStateTest {
    private val match = MusicApi.RecognitionMatch(Music(12, "歌曲", "歌手", "", 180), .95, 0.0, 4.0)

    @Test
    fun startAndStopAutomaticallyCollapse() {
        val state = RecognitionOverlayState()
        state.expand()
        state.start()
        assertEquals(RecognitionOverlayMode.Bubble, state.mode)
        assertTrue(state.recording)
        state.expand()
        assertEquals(RecognitionOverlayMode.Panel, state.mode)
        state.stop()
        assertEquals(RecognitionOverlayMode.Bubble, state.mode)
        assertFalse(state.recording)
    }

    @Test
    fun collapsingAndExpandingKeepsTheIdentifiedSong() {
        val state = RecognitionOverlayState()
        state.match(match)
        state.collapse()
        state.expand()
        assertEquals(RecognitionOverlayMode.Result, state.mode)
        assertSame(match, state.lastMatch)
    }

    @Test
    fun stoppingAnotherAttemptRestoresThePreviousResult() {
        val state = RecognitionOverlayState()
        state.match(match)
        state.start()
        state.stop()
        state.expand()
        assertEquals(RecognitionOverlayMode.Result, state.mode)
        assertSame(match, state.lastMatch)
    }

    @Test
    fun errorDoesNotEraseThePreviousSong() {
        val state = RecognitionOverlayState()
        state.match(match)
        state.message()
        state.collapse()
        assertSame(match, state.lastMatch)
        state.stop()
        state.expand()
        assertEquals(RecognitionOverlayMode.Result, state.mode)
    }
}
