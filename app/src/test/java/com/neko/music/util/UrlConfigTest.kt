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
}
