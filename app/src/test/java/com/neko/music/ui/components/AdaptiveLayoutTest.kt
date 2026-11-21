package com.neko.music.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveLayoutTest {
    @Test
    fun gridColumnsFollowWidthBreakpoints() {
        assertEquals(2, adaptiveGridColumns(599))
        assertEquals(2, adaptiveGridColumns(839))
        assertEquals(3, adaptiveGridColumns(840))
        assertEquals(3, adaptiveGridColumns(1199))
        assertEquals(4, adaptiveGridColumns(1200))
    }

    @Test
    fun coverAspectRatioAvoidsOversizedWideCards() {
        assertEquals(1f, adaptiveCoverAspectRatio(200.dp), 0f)
        assertEquals(1.15f, adaptiveCoverAspectRatio(280.dp), 0f)
        assertEquals(1.5f, adaptiveCoverAspectRatio(420.dp), 0f)
    }
}
