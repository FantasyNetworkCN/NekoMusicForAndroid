package com.neko.music.data.manager

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.neko.music.data.database.AppDatabase
import com.neko.music.data.database.PlaylistEntity
import com.neko.music.data.model.Music
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PlaylistManager private constructor(context: Context) {
    
    // 数据库迁移：从版本1到版本2
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 创建新表
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS playlist_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    musicId INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    album TEXT NOT NULL,
                    duration INTEGER NOT NULL,
                    filePath TEXT NOT NULL,
                    coverFilePath TEXT NOT NULL,
                    uploadUserId INTEGER NOT NULL,
                    createdAt TEXT NOT NULL,
                    addedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            
            // 复制数据
            database.execSQL(
                """
                INSERT INTO playlist_new (musicId, title, artist, album, duration, filePath, coverFilePath, uploadUserId, createdAt, addedAt)
                SELECT musicId, title, artist, album, duration, filePath, coverFilePath, uploadUserId, createdAt, addedAt
                FROM playlist
                """.trimIndent()
            )
            
            // 删除旧表
            database.execSQL("DROP TABLE IF EXISTS playlist")
            
            // 重命名新表
            database.execSQL("ALTER TABLE playlist_new RENAME TO playlist")
        }
    }
    
    private val database = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "music-playlist-db"
    )
        .addMigrations(MIGRATION_1_2)
        .fallbackToDestructiveMigration()
        .build()
    
    private val dao = database.playlistDao()
    
    val playlist: Flow<List<Music>> = dao.getAllPlaylist().map { entities ->
        android.util.Log.d("PlaylistManager", "从数据库读取 ${entities.size} 首音乐")
        entities.map { entity ->
            android.util.Log.d("PlaylistManager", "读取音乐: id=${entity.musicId}, title=${entity.title}, coverFilePath=${entity.coverFilePath}")
            Music(
                id = entity.musicId,
                title = entity.title,
                artist = entity.artist,
                album = entity.album,
                duration = entity.duration,
                filePath = entity.filePath,
                coverFilePath = entity.coverFilePath,
                uploadUserId = entity.uploadUserId,
                createdAt = entity.createdAt
            )
        }
    }
    
    suspend fun addToPlaylist(music: Music) {
        // 检查是否已存在
        val existing = dao.getMusicById(music.id)
        if (existing == null) {
            val entity = PlaylistEntity(
                musicId = music.id,
                title = music.title,
                artist = music.artist,
                album = music.album,
                duration = music.duration,
                filePath = music.filePath,
                coverFilePath = music.coverFilePath ?: "",
                uploadUserId = music.uploadUserId,
                createdAt = music.createdAt
            )
            android.util.Log.d("PlaylistManager", "添加到数据库: id=${music.id}, title=${music.title}, coverFilePath=${entity.coverFilePath}")
            dao.addToPlaylist(entity)
        } else {
            android.util.Log.d("PlaylistManager", "音乐已存在，跳过: id=${music.id}")
        }
    }
    
    suspend fun removeFromPlaylist(musicId: Int) {
        dao.removeFromPlaylist(musicId)
    }
    
    suspend fun clearPlaylist() {
        dao.clearPlaylist()
    }
    
    suspend fun getPlaylistCount(): Int {
        return dao.getPlaylistCount()
    }
    
    suspend fun isInPlaylist(musicId: Int): Boolean {
        return dao.getMusicById(musicId) != null
    }
    
    suspend fun clearPlaylistExcept(currentMusicId: Int) {
        val allMusic: List<PlaylistEntity> = kotlinx.coroutines.runBlocking {
            dao.getAllPlaylist().first()
        }
        allMusic.forEach { entity: PlaylistEntity ->
            if (entity.musicId != currentMusicId) {
                dao.removeFromPlaylist(entity.musicId)
            }
        }
    }
    
    suspend fun getLastPlayed(): Music? {
        return dao.getLastPlayed()?.let { entity ->
            Music(
                id = entity.musicId,
                title = entity.title,
                artist = entity.artist,
                album = entity.album,
                duration = entity.duration,
                filePath = entity.filePath,
                coverFilePath = entity.coverFilePath,
                uploadUserId = entity.uploadUserId,
                createdAt = entity.createdAt
            )
        }
    }
    
    suspend fun updateAddedAt(musicId: Int) {
        dao.updateAddedAt(musicId)
    }
    
    suspend fun getNextMusic(currentMusicId: Int): Music? {
        android.util.Log.d("PlaylistManager", "getNextMusic called with currentMusicId: $currentMusicId")
        val entity = dao.getNextMusic(currentMusicId)
        android.util.Log.d("PlaylistManager", "getNextMusic result: $entity")
        return entity?.let {
            Music(
                id = it.musicId,
                title = it.title,
                artist = it.artist,
                album = it.album,
                duration = it.duration,
                filePath = it.filePath,
                coverFilePath = it.coverFilePath,
                uploadUserId = it.uploadUserId,
                createdAt = it.createdAt
            )
        }
    }
    
    suspend fun getFirstMusic(): Music? {
        android.util.Log.d("PlaylistManager", "getFirstMusic called")
        val entity = dao.getFirstMusic()
        android.util.Log.d("PlaylistManager", "getFirstMusic result: $entity")
        return entity?.let {
            Music(
                id = it.musicId,
                title = it.title,
                artist = it.artist,
                album = it.album,
                duration = it.duration,
                filePath = it.filePath,
                coverFilePath = it.coverFilePath,
                uploadUserId = it.uploadUserId,
                createdAt = it.createdAt
            )
        }
    }
    
    suspend fun getPreviousMusic(currentMusicId: Int): Music? {
        return dao.getPreviousMusic(currentMusicId)?.let { entity ->
            Music(
                id = entity.musicId,
                title = entity.title,
                artist = entity.artist,
                album = entity.album,
                duration = entity.duration,
                filePath = entity.filePath,
                coverFilePath = entity.coverFilePath,
                uploadUserId = entity.uploadUserId,
                createdAt = entity.createdAt
            )
        }
    }
    
    suspend fun getAllPlaylistList(): List<Music> {
        return dao.getAllPlaylistList().map { entity ->
            Music(
                id = entity.musicId,
                title = entity.title,
                artist = entity.artist,
                album = entity.album,
                duration = entity.duration,
                filePath = entity.filePath,
                coverFilePath = entity.coverFilePath,
                uploadUserId = entity.uploadUserId,
                createdAt = entity.createdAt
            )
        }
    }
    
    suspend fun getRandomMusic(excludeMusicId: Int): Music? {
        val allMusic = getAllPlaylistList()
        val filtered = allMusic.filter { it.id != excludeMusicId }
        return if (filtered.isNotEmpty()) {
            filtered.random()
        } else {
            null
        }
    }
    
    suspend fun getPlaylistMusicById(musicId: Int): Music? {
        android.util.Log.d("PlaylistManager", "getPlaylistMusicById called with musicId: $musicId")
        val entity = dao.getMusicById(musicId)
        android.util.Log.d("PlaylistManager", "getPlaylistMusicById result: $entity")
        return entity?.let {
            Music(
                id = it.musicId,
                title = it.title,
                artist = it.artist,
                album = it.album,
                duration = it.duration,
                filePath = it.filePath,
                coverFilePath = it.coverFilePath,
                uploadUserId = it.uploadUserId,
                createdAt = it.createdAt
            )
        }
    }
    
    suspend fun getLastMusic(): Music? {
        android.util.Log.d("PlaylistManager", "getLastMusic called")
        val entity = dao.getLastMusic()
        android.util.Log.d("PlaylistManager", "getLastMusic result: $entity")
        return entity?.let {
            Music(
                id = it.musicId,
                title = it.title,
                artist = it.artist,
                album = it.album,
                duration = it.duration,
                filePath = it.filePath,
                coverFilePath = it.coverFilePath,
                uploadUserId = it.uploadUserId,
                createdAt = it.createdAt
            )
        }
    }
    
    companion object {
        @Volatile
        private var instance: PlaylistManager? = null
        
        fun getInstance(context: Context): PlaylistManager {
            return instance ?: synchronized(this) {
                instance ?: PlaylistManager(context.applicationContext).also { instance = it }
            }
        }
    }
}