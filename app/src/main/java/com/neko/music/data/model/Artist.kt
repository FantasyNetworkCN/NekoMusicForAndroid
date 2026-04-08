package com.neko.music.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Artist(
    val name: String,
    val musicCount: Int,
    val coverPath: String?
)