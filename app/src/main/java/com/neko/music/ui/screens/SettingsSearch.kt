package com.neko.music.ui.screens

internal enum class SettingsSectionId {
    App, Playback, Haptics, About,
}

internal enum class SettingsEntryId {
    Appearance, Lyrics, Language, CacheEnabled, CacheManagement, AudioFocus, Haptics, Privacy, Update,
}

internal data class SettingsSearchEntry(
    val id: SettingsEntryId,
    val section: SettingsSectionId,
    val title: String,
    val subtitle: String,
    val sectionTitle: String,
    val keywords: String = "",
)

/** 同时搜索标题、说明和分组；多个关键词可以跨字段匹配，始终保持页面原有顺序。 */
internal fun filterSettingsEntries(
    entries: List<SettingsSearchEntry>,
    query: String,
): List<SettingsSearchEntry> {
    val words = query.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    if (words.isEmpty()) return entries
    return entries.filter { entry ->
        val text = "${entry.title} ${entry.subtitle} ${entry.sectionTitle} ${entry.keywords}"
        words.all { text.contains(it, ignoreCase = true) }
    }
}
