package com.neko.music.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Music(
    val id: Int,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int,
    val filePath: String? = null,
    val coverFilePath: String? = null,
    val uploadUserId: Int? = null,
    val createdAt: String? = null,
    val playCount: Int? = null,
    /** 是否有有效歌词（单条 query 搜索响应字段） */
    val lrc: Boolean = false
) {
    val coverUrl: String
        get() = if (coverFilePath.isNullOrEmpty()) {
            "/api/defaultIcon"
        } else {
            coverFilePath
        }
}

@Serializable
data class SearchRequest(
    val query: String,
)

@Serializable
data class SearchItem(
    val title: String,
    val artist: String = "",
)

@Serializable
data class BatchSearchRequest(
    val items: List<SearchItem>,
)

@Serializable
data class SearchResponse(
    val success: Boolean,
    val message: String,
    val results: List<Music>?
)

@Serializable
data class ErrorResponse(
    val error: String
)