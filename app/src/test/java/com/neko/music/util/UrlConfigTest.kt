package com.neko.music.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlConfigTest {
    @Test
    fun recognitionUsesSecureWebSocketForHttpsSite() {
        assertEquals(
            "wss://music.nekocore.cn/api/music/recognize/ws",
            UrlConfig.getMusicRecognitionWsUrl(),
        )
    }

    @Test
    fun togetherUsesSecureWebSocketAndEncodesTicket() {
        assertEquals(
            "wss://music.nekocore.cn/api/listen/ws?ticket=a%2Bb%2Fc",
            UrlConfig.getTogetherWsUrl("a+b/c"),
        )
    }

    @Test
    fun localPlaybackUrlDoesNotInvokeRemoteResolution() {
        val local = "file:///cache/music_42_hq.mp3"
        assertEquals(local, MusicUrlResolver.resolveBlocking(local))
    }

    @Test
    fun customServerBaseNormalizesSchemeAndTrailingSlashes() {
        assertEquals(
            "https://music.example.com/self-hosted",
            UrlConfig.normalizeBaseUrl(" music.example.com/self-hosted/// "),
        )
        assertEquals(
            "https://music.example.com",
            UrlConfig.normalizeBaseUrl("https://music.example.com/"),
        )
    }

    @Test
    fun remotePlainHttpAndAmbiguousUrlsAreRejected() {
        assertNull(UrlConfig.normalizeBaseUrl("http://music.example.com"))
        assertNull(UrlConfig.normalizeBaseUrl("https://user:pass@music.example.com"))
        assertNull(UrlConfig.normalizeBaseUrl("https://music.example.com?next=1"))
        assertNull(UrlConfig.normalizeBaseUrl("https://music.example.com/#fragment"))
    }

    @Test
    fun loopbackMayUseHttpForLocalDevelopment() {
        assertEquals("http://localhost:65535", UrlConfig.normalizeBaseUrl("http://localhost:65535"))
        assertEquals("http://127.0.0.1:65535", UrlConfig.normalizeBaseUrl("http://127.0.0.1:65535/"))
    }

    @Test
    fun sourceManifestUsesCurrentBasePath() {
        assertEquals(
            "https://music.example.com/mirror/api/music/source",
            UrlConfig.getSourceManifestUrl("https://music.example.com/mirror/"),
        )
    }
}
