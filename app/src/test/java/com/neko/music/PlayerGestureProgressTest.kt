package com.neko.music

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerGestureProgressTest {
    @Test
    fun `播放页下滑增加收起进度`() {
        assertEquals(
            0.5f,
            playerGestureDeltaFraction(
                deltaY = 500f,
                dragOriginMini = false,
                travelPx = 1_000f,
            ),
            0.0001f,
        )
    }

    @Test
    fun `迷你栏上滑增加展开进度`() {
        assertEquals(
            0.5f,
            playerGestureDeltaFraction(
                deltaY = -500f,
                dragOriginMini = true,
                travelPx = 1_000f,
            ),
            0.0001f,
        )
    }

    @Test
    fun `反向拖动减少对应进度`() {
        assertEquals(
            -0.25f,
            playerGestureDeltaFraction(
                deltaY = -250f,
                dragOriginMini = false,
                travelPx = 1_000f,
            ),
            0.0001f,
        )
    }
}
