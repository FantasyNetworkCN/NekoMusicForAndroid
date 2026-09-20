package com.neko.music.data.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Int,
    val nickname: String,
    val email: String,
    val createdAt: String
)