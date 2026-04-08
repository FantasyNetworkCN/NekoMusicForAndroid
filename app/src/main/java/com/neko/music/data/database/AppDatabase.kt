package com.neko.music.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PlaylistEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
}