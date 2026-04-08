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
    val playCount: Int? = null
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
    val query: String
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