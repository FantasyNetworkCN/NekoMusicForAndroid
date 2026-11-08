package com.neko.music.data.model

/** 后端歌词详情：普通 LRC + 可选 Neko 字级歌词。 */
data class LyricsPayload(
    val lyrics: String,
    val richLines: List<RichLyricLine> = emptyList(),
)

data class RichLyricLine(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val translation: String = "",
    val romanization: String = "",
    val background: Boolean = false,
    val words: List<RichLyricWord> = emptyList(),
)

data class RichLyricWord(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)
