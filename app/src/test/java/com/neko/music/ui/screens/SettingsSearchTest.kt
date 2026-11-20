package com.neko.music.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSearchTest {
    private val entries = listOf(
        SettingsSearchEntry(SettingsEntryId.Lyrics, SettingsSectionId.App,
            "歌词样式", "字号、翻译与音译", "应用", "lyrics translation"),
        SettingsSearchEntry(SettingsEntryId.CacheEnabled, SettingsSectionId.Playback,
            "缓存音乐", "离线播放", "播放与存储", "cache offline"),
        SettingsSearchEntry(SettingsEntryId.CacheManagement, SettingsSectionId.Playback,
            "缓存管理", "已缓存 24 首歌曲", "播放与存储", "cache storage"),
    )

    @Test
    fun blankSearchPreservesAllEntriesAndOrder() {
        assertEquals(entries, filterSettingsEntries(entries, "  \n "))
    }

    @Test
    fun searchesLocalizedLabelsAndEnglishKeywords() {
        assertEquals(listOf(entries[0]), filterSettingsEntries(entries, "音译"))
        assertEquals(listOf(entries[0]), filterSettingsEntries(entries, "LYRICS"))
    }

    @Test
    fun multipleWordsCanMatchAcrossDifferentFields() {
        assertEquals(listOf(entries[1]), filterSettingsEntries(entries, " cache   离线 "))
        assertEquals(listOf(entries[2]), filterSettingsEntries(entries, "24 storage"))
    }

    @Test
    fun sectionNameFindsTheWholeGroup() {
        assertEquals(entries.drop(1), filterSettingsEntries(entries, "播放与存储"))
    }

    @Test
    fun unrelatedOrPartiallyMatchingQueryHasNoResults() {
        assertEquals(emptyList<SettingsSearchEntry>(), filterSettingsEntries(entries, "lyrics cache"))
    }
}
