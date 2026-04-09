package com.neko.music.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val musicId: Int,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Int,
    val filePath: String?,
    val coverFilePath: String,
    val uploadUserId: Int?,
    val createdAt: String?,
    val addedAt: Long = System.currentTimeMillis()
)