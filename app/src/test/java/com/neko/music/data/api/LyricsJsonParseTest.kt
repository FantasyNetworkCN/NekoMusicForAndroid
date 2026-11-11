package com.neko.music.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsJsonParseTest {
    @Test
    fun jsonNullAndLiteralNullDoNotReachPlaybackPage() {
        val row = Json.parseToJsonElement(
            """{"translation":null,"romanization":null,"text":"null","value":"歌词"}"""
        ) as JsonObject

        assertEquals("", jsonLyricText(row["translation"]))
        assertEquals("", jsonLyricText(row["romanization"]))
        assertEquals("", jsonLyricText(row["text"]))
        assertEquals("歌词", jsonLyricText(row["value"]))
    }
}
