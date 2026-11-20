package com.neko.music.util

import com.neko.music.data.model.Music
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionCoverTest {
    private fun music(path: String?) = Music(12, "歌曲", "歌手", "", 180, coverFilePath = path)

    @Test
    fun serverFilePathsUseTheCoverEndpoint() {
        assertEquals(UrlConfig.getMusicCoverUrl(12), recognitionCoverUrl(music("/covers/12.jpg")))
        assertEquals(UrlConfig.getMusicCoverUrl(12), recognitionCoverUrl(music("uploads/12.jpg")))
    }

    @Test
    fun absoluteCoverUrlsArePreserved() {
        assertEquals("https://example.com/12.jpg", recognitionCoverUrl(music(" https://example.com/12.jpg ")))
    }

    @Test
    fun missingCoverStillHasAValidRequestUrl() {
        assertEquals(UrlConfig.getMusicCoverUrl(12), recognitionCoverUrl(music(null)))
        assertEquals(UrlConfig.getMusicCoverUrl(12), recognitionCoverUrl(music("")))
    }
}
