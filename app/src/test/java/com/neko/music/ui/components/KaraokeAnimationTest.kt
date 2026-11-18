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

    @Test
    fun splitsMultiCharacterSyllablesIntoIndependentRevealClocks() {
        val line = LrcLine(1f, "你好", syllables = listOf(LrcSyllable(1f, "你好", 3f)))

        val timings = buildAppleGlyphTimings(line, 3f)

        assertEquals(2, timings.size)
        assertEquals(1000f, timings[0].startMs)
        assertEquals(2000f, timings[1].startMs)
        assertEquals(0f, appleGlyphVisual(timings[1], 1900f).reveal)
        assertEquals(1f, appleGlyphVisual(timings[0], 2100f).reveal)
    }

    @Test
    fun longToneLateInLineUsesLocalCharacterIndex() {
        val line = LrcLine(
            1f, "前面啊",
            syllables = listOf(LrcSyllable(1f, "前面", 2f), LrcSyllable(2f, "啊", 4f)),
        )

        val tone = buildAppleGlyphTimings(line, 4f).last()
        val peak = appleGlyphVisual(tone, 3000f)

        assertEquals(0, tone.toneCharacterIndex)
        assertEquals(1, tone.toneCharacterCount)
        assertTrue(peak.scale > 1f)
        assertTrue(peak.glow > 0f)
        assertEquals(1f, appleGlyphVisual(tone, 4100f).scale)
    }

    @Test
    fun ordinaryCharactersLiftWithoutDefaultBounceOrGlow() {
        val line = LrcLine(1f, "字", syllables = listOf(LrcSyllable(1f, "字", 1.5f)))
        val timing = buildAppleGlyphTimings(line, 1.5f).single()

        val visual = appleGlyphVisual(timing, 1250f)

        assertTrue(visual.lift in 0f..1f)
        assertTrue(visual.reveal in 0f..1f)
        assertEquals(1f, visual.scale)
        assertEquals(0f, visual.glow)
        assertEquals(1f, appleGlyphVisual(timing, 1900f).lift)
    }

    @Test
    fun latinWordSharesLongToneWhileRevealingEachCharacterSeparately() {
        val line = LrcLine(1f, "love", syllables = listOf(LrcSyllable(1f, "love", 3f)))

        val timings = buildAppleGlyphTimings(line, 3f)

        assertTrue(timings.all { it.isLongTone })
        assertTrue(timings.all { it.toneStartMs == 1000f && it.toneDurationMs == 2000f })
        assertEquals(0f, appleGlyphVisual(timings.last(), 1500f).reveal)
        assertTrue(appleGlyphVisual(timings.first(), 1500f).reveal > 0f)
    }

    @Test
    fun supplementaryAndCombiningCharactersKeepTheirGlyphRanges() {
        val line = LrcLine(1f, "🎵e\u0301", syllables = listOf(LrcSyllable(1f, "🎵e\u0301", 3f)))

        val timings = buildAppleGlyphTimings(line, 3f)

        assertEquals(listOf(0 until 2, 2 until 4), appleCharacterRanges(line.text))
        assertEquals(listOf(0, 2), timings.map { it.startChar })
        assertEquals(listOf(2, 4), timings.map { it.endChar })
    }

    @Test
    fun interludesReserveSpaceOnlyForKnownLongGaps() {
        val lines = listOf(
            LrcLine(5f, "第一句", endTime = 7f),
            LrcLine(12f, "第二句"),
            LrcLine(20f, "第三句"),
        )

        val display = appleDisplayLyrics(lines)

        assertEquals(listOf(0f, 7f), display.filter { it.text.isBlank() }.map { it.time })
        assertEquals(listOf(4.75f, 11.75f), display.filter { it.text.isBlank() }.map { it.endTime })
        assertEquals(lines, display.filter { it.text.isNotBlank() })
    }

    @Test
    fun overlappingTimedLinesRemainActiveUntilTheirOwnEnd() {
        val lines = listOf(
            LrcLine(1f, "主唱", syllables = listOf(LrcSyllable(1f, "主唱", 4f))),
            LrcLine(2f, "和声", syllables = listOf(LrcSyllable(2f, "和声", 3f))),
            LrcLine(5f, "下一句"),
        )

        assertEquals(emptySet<Int>(), appleActiveLineIndexes(lines, 0f))
        assertEquals(setOf(0, 1), appleActiveLineIndexes(lines, 2.5f))
        assertEquals(setOf(0), appleActiveLineIndexes(lines, 3.5f))
        assertEquals(setOf(1), appleActiveLineIndexes(lines, 4.5f))
        assertEquals(setOf(2), appleActiveLineIndexes(lines, 5.1f))
    }

    @Test
    fun shortCuesLimitCascadeDurationAndKeepFollowingRowsCatchingUp() {
        val timings = appleCascadeTimings(6, 300f)

        assertEquals(0f, timings.first().delayMs)
        assertEquals(300f, timings.first().durationMs)
        assertTrue(timings.zipWithNext().all { (first, next) -> first.delayMs < next.delayMs })
        assertTrue(timings.drop(1).all { it.durationMs >= 150f })
        assertTrue(timings[1].delayMs + timings[1].durationMs <= 300f)
        assertTrue(appleCascadeBounce(0, 6) < appleCascadeBounce(6, 6))
    }

    @Test
    fun cascadeSpringIsContinuousAndSettlesAtItsDestination() {
        val easing = AppleLyricsSpringEasing(AppleLyricMotion.CascadeBounce)

        assertEquals(0f, easing.transform(0f), 0.00001f)
        assertEquals(1f, easing.transform(1f), 0.00001f)
        assertTrue((0..1000).all { easing.transform(it / 1000f).isFinite() })
        assertTrue(absDifference(easing.transform(0.499f), easing.transform(0.5f)) < 0.01f)
    }

    private fun absDifference(first: Float, second: Float): Float = kotlin.math.abs(first - second)
}
