package com.neko.music.ui.icons

import com.neko.music.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NekoSymbolTest {
    @Test
    fun everyFontSymbolHasAPrivateUseCodePoint() {
        val vectors = setOf(NekoSymbol.Star, NekoSymbol.Circle)
        NekoSymbol.entries.filterNot { it in vectors }.forEach { symbol ->
            assertNotNull(symbol.name, symbol.codePoint)
            assertTrue(symbol.name, requireNotNull(symbol.codePoint) in 0xF0000..0x10FFFD)
        }
    }

    @Test
    fun onlyFourMissingSymbolsUseTheBackupFont() {
        assertEquals(
            setOf(NekoSymbol.Walk, NekoSymbol.Queue, NekoSymbol.Book, NekoSymbol.Landscape),
            NekoSymbol.entries.filter { it.usesSubsetFont }.toSet(),
        )
        assertFalse(NekoSymbol.Play.usesSubsetFont)
        assertFalse(NekoSymbol.Lyrics.usesSubsetFont)
    }

    @Test
    fun playbackIconsKeepTheirStateAndMode() {
        assertEquals(NekoSymbol.Play, nekoDrawableIconSpec(R.drawable.play)?.symbol)
        assertEquals(NekoSymbol.Pause, nekoDrawableIconSpec(R.drawable.pause)?.symbol)
        assertEquals(NekoSymbol.Previous, nekoDrawableIconSpec(R.drawable.previous_song)?.symbol)
        assertEquals(NekoSymbol.Next, nekoDrawableIconSpec(R.drawable.next_song)?.symbol)
        assertEquals(NekoSymbol.Repeat, nekoDrawableIconSpec(R.drawable.list_loop)?.symbol)
        assertEquals(NekoSymbol.RepeatOne, nekoDrawableIconSpec(R.drawable.single_loop)?.symbol)
        assertEquals(NekoSymbol.Shuffle, nekoDrawableIconSpec(R.drawable.shuffle)?.symbol)
        assertEquals(NekoSymbol.Queue, nekoDrawableIconSpec(R.drawable.playlist)?.symbol)
    }

    @Test
    fun favoriteVariantsAndBackDirectionArePreserved() {
        assertEquals(NekoSymbol.Heart, nekoIconSpec("Filled.Favorite")?.symbol)
        assertEquals(NekoSymbol.Heart, nekoIconSpec("Filled.FavoriteBorder")?.symbol)
        assertEquals(NekoSymbolVariant.Fill, nekoIconSpec("Filled.Favorite")?.variant)
        assertEquals(NekoSymbolVariant.Regular, nekoIconSpec("Filled.FavoriteBorder")?.variant)
        assertEquals(
            NekoIconSpec(NekoSymbol.Heart, NekoSymbolVariant.Fill),
            nekoDrawableIconSpec(R.drawable.ic_favorite_filled),
        )
        assertEquals(
            NekoIconSpec(NekoSymbol.Heart),
            nekoDrawableIconSpec(R.drawable.ic_favorite_border),
        )
        assertEquals(NekoSymbol.ChevronLeft, nekoIconSpec("AutoMirrored.Filled.ArrowBack")?.symbol)
        assertEquals(NekoSymbol.ChevronRight, nekoIconSpec("AutoMirrored.Filled.KeyboardArrowRight")?.symbol)
        assertEquals(-90f, nekoIconSpec("Filled.KeyboardArrowDown")?.rotation)
    }

    @Test
    fun unmappedAssetsKeepTheirOriginalRendering() {
        assertEquals(null, nekoDrawableIconSpec(R.drawable.qq))
        assertEquals(null, nekoDrawableIconSpec(R.drawable.ic_gear_play_button_bg))
        assertEquals(null, nekoIconSpec("Filled.Lock"))
        assertEquals(null, nekoIconSpec("Filled.Warning"))
    }
}
