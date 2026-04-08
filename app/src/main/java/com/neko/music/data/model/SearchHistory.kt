package com.neko.music.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchHistory(
    val query: String,
    val timestamp: Long,
    val type: String = "keyword" // "keyword" 表示搜索关键词，"music" 表示单曲名称
)