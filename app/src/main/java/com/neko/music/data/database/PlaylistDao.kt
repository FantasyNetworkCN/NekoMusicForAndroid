package com.neko.music.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlist ORDER BY id ASC")
    fun getAllPlaylist(): Flow<List<PlaylistEntity>>
    
    @Query("SELECT * FROM playlist WHERE musicId = :musicId")
    suspend fun getMusicById(musicId: Int): PlaylistEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToPlaylist(music: PlaylistEntity)
    
    @Query("DELETE FROM playlist WHERE musicId = :musicId")
    suspend fun removeFromPlaylist(musicId: Int)
    
    @Query("DELETE FROM playlist")
    suspend fun clearPlaylist()
    
    @Query("SELECT COUNT(*) FROM playlist")
    suspend fun getPlaylistCount(): Int
    
    @Query("SELECT * FROM playlist ORDER BY addedAt DESC LIMIT 1")
    suspend fun getLastPlayed(): PlaylistEntity?
    
    @Query("UPDATE playlist SET addedAt = :timestamp WHERE musicId = :musicId")
    suspend fun updateAddedAt(musicId: Int, timestamp: Long = System.currentTimeMillis())
    
    @Query("SELECT * FROM playlist WHERE id > (SELECT id FROM playlist WHERE musicId = :currentMusicId LIMIT 1) ORDER BY id ASC LIMIT 1")
    suspend fun getNextMusic(currentMusicId: Int): PlaylistEntity?
    
    @Query("SELECT * FROM playlist WHERE id < (SELECT id FROM playlist WHERE musicId = :currentMusicId LIMIT 1) ORDER BY id DESC LIMIT 1")
    suspend fun getPreviousMusic(currentMusicId: Int): PlaylistEntity?
    
    @Query("SELECT * FROM playlist ORDER BY id ASC LIMIT 1")
    suspend fun getFirstMusic(): PlaylistEntity?
    
    @Query("SELECT * FROM playlist ORDER BY id ASC")
    suspend fun getAllPlaylistList(): List<PlaylistEntity>
    
    @Query("SELECT * FROM playlist ORDER BY id DESC LIMIT 1")
    suspend fun getLastMusic(): PlaylistEntity?
}