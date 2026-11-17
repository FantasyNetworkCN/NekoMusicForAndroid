package com.neko.music.ui.components

import com.neko.music.ui.screens.LrcLine
import com.neko.music.ui.screens.LrcSyllable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeAnimationTest {
    @Test
    fun mapsSyllablesToRenderedCharacterRanges() {
        val line = LrcLine(
            time = 1f,
            text = "逐字歌词",
            syllables = listOf(
                LrcSyllable(1f, "逐字", endTime = 2f),
                LrcSyllable(2f, "歌词", endTime = 3f),
            ),
        )

        val timings = buildKaraokeSyllableTimings(line, lineEndTime = 3f)

        assertEquals(2, timings.size)
        assertEquals(0, timings[0].startChar)
        assertEquals(2, timings[0].endChar)
        assertEquals(1f, timings[0].startSeconds)
        assertEquals(2f, timings[0].endSeconds)
        assertEquals(2, timings[1].startChar)
        assertEquals(4, timings[1].endChar)
    }

    @Test
    fun revealProgressIsClampedAndSmooth() {
        val timing = KaraokeSyllableTiming(0, 2, 1f, 2f)

        assertEquals(0f, karaokeRevealProgress(timing, 0.9f))
        val halfway = karaokeRevealProgress(timing, 1.5f)
        assertTrue(halfway in 0f..1f)
        assertEquals(1f, karaokeRevealProgress(timing, 2.1f))
    }

    @Test
    fun leavesLineOnlyLyricsWithoutSyntheticWordTiming() {
        val line = LrcLine(time = 4f, text = "整行歌词")

        val timings = buildKaraokeSyllableTimings(line, lineEndTime = 6f)

        assertTrue(timings.isEmpty())
    }
}
