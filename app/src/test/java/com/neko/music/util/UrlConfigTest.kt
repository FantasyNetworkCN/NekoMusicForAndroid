package com.neko.music.util

import org.junit.Assert.assertEquals
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
}
