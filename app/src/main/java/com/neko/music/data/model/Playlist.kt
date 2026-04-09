package com.neko.music.data.model

data class Playlist(
    val id: Int,
    val name: String,
    val musicCount: Int,
    val userId: Int,
    val createdAt: String,
    val coverPath: String? = null,
    val description: String? = null,
    val username: String? = null,
    val creatorAvatar: String? = null
)

data class PlaylistResponse(
    val success: Boolean,
    val message: String,
    val data: List<Playlist>? = null
)