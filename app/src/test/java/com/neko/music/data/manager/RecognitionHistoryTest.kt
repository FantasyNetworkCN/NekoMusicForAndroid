package com.neko.music.data.manager

import com.neko.music.data.model.Music
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionHistoryTest {
    private fun entry(id: Int, time: Long) =
        RecognitionHistoryEntry(Music(id, "歌曲$id", "歌手", "专辑", 180, coverFilePath = "/covers/$id.jpg"), .91, time)

    @Test
    fun sameSongMovesToTheFrontWithoutDuplicating() {
        val result = mergeRecognitionHistory(listOf(entry(2, 20), entry(1, 10)), entry(1, 30))
        assertEquals(listOf(1, 2), result.map { it.music.id })
        assertEquals(30L, result.first().recognizedAt)
    }

    @Test
    fun historyKeepsTheMostRecentHundredSongs() {
        val result = mergeRecognitionHistory((1..100).map { entry(it, it.toLong()) }, entry(101, 101))
        assertEquals(100, result.size)
        assertEquals(101, result.first().music.id)
        assertEquals(2, result.last().music.id)
    }

    @Test
    fun persistedHistoryKeepsMetadataAndArtwork() {
        val entries = listOf(entry(1, 123456))
        val decoded = Json.decodeFromString<List<RecognitionHistoryEntry>>(Json.encodeToString(entries))
        assertEquals(entries, decoded)
        assertEquals("/covers/1.jpg", decoded.first().toMatch().music.coverFilePath)
    }
}
