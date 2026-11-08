package com.neko.music.ui.screens

import com.neko.music.data.model.LyricsPayload
import com.neko.music.data.model.RichLyricLine
import com.neko.music.data.model.RichLyricWord
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsPayloadParseTest {
    @Test
    fun usesRichWordTimestampsAndFallsBackToLrc() {
        val payload = LyricsPayload(
            lyrics = "[00:09.00]fallback",
            richLines = listOf(
                RichLyricLine(
                    startMs = 1_000,
                    endMs = 2_200,
                    text = "逐字",
                    translation = "word by word",
                    words = listOf(
                        RichLyricWord(1_000, 1_500, "逐"),
                        RichLyricWord(1_500, 2_200, "字"),
                    ),
                )
            ),
        )

        val parsed = parseLyricsPayload(payload)

        assertEquals(1, parsed.size)
        assertEquals(1f, parsed[0].time)
        assertEquals(2.2f, parsed[0].endTime!!)
        assertEquals(2, parsed[0].syllables.size)
        assertEquals(1.5f, parsed[0].syllables[1].time)

        val fallback = parseLyricsPayload(LyricsPayload("[00:09.00]fallback"))
        assertEquals(1, fallback.size)
        assertEquals(9f, fallback[0].time)
    }
}
