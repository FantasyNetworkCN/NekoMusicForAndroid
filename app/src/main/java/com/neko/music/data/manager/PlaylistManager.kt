package com.neko.music.data.manager

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.neko.music.data.database.AppDatabase
import com.neko.music.data.database.PlaylistEntity
import com.neko.music.data.model.Music
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
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

    private val _events = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<Unit> = _events
    
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
            _events.tryEmit(Unit)
        } else {
            android.util.Log.d("PlaylistManager", "音乐已存在，跳过: id=${music.id}")
        }
    }
    
    suspend fun removeFromPlaylist(musicId: Int) {
        dao.removeFromPlaylist(musicId)
        _events.tryEmit(Unit)
    }

    /**
     * 把 [music] 加入队列，并直接排在 [afterMusicId] 之后（「下一首播放」）。
     *
     * [afterMusicId] 为空、等于自身或已不在队列里时，退化为普通追加。
     * @return true 表示已插到 [afterMusicId] 之后。
     */
    suspend fun addNextAfter(music: Music, afterMusicId: Int?): Boolean {
        if (afterMusicId == null || afterMusicId == music.id) return false
        addToPlaylist(music)
        val all = dao.getAllPlaylistList()
        if (all.none { it.musicId == music.id }) return false
        val order = all.map { it.musicId }.toMutableList()
        order.remove(music.id)
        val anchorIndex = order.indexOf(afterMusicId)
        if (anchorIndex < 0) return false
        order.add(anchorIndex + 1, music.id)
        applyQueueOrder(order)
        android.util.Log.d(
            "PlaylistManager",
            "下一首播放插队: ${music.title}(${music.id}) 排到 $afterMusicId 之后"
        )
        return true
    }

    /**
     * 按 [musicIds] 的顺序重写待播队列（与列表 UI 一致）。
     */
    suspend fun applyQueueOrder(musicIds: List<Int>) {
        val db = dao.getAllPlaylistList()
        if (musicIds.isEmpty() || db.size != musicIds.size) return
        if (musicIds.toSet().size != musicIds.size) return
        if (db.map { it.musicId }.toSet() != musicIds.toSet()) {
            android.util.Log.w("PlaylistManager", "applyQueueOrder: 与数据库曲目集合不一致，已忽略")
            return
        }
        val map = db.associateBy { it.musicId }
        val ordered = musicIds.map { map.getValue(it) }
        dao.replacePlaylistOrdered(ordered.map { it.copy(id = 0L) })
        _events.tryEmit(Unit)
    }

    suspend fun replacePlaylist(musicList: List<Music>) {
        val uniqueMusic = musicList.distinctBy { it.id }
        val entities = uniqueMusic.map { music ->
            PlaylistEntity(
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
        }
        dao.replacePlaylistOrdered(entities)
        _events.tryEmit(Unit)
    }
    
    suspend fun clearPlaylist() {
        dao.clearPlaylist()
        _events.tryEmit(Unit)
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
        var changed = false
        allMusic.forEach { entity: PlaylistEntity ->
            if (entity.musicId != currentMusicId) {
                dao.removeFromPlaylist(entity.musicId)
                changed = true
            }
        }
        if (changed) {
            _events.tryEmit(Unit)
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
    
    /**
     * 清理 [getAllPlaylistList] 里的重复行：同一个 musicId 只保留最后写入的一条。
     *
     * playlist 表以自增 id 为主键、musicId 无唯一约束，历史版本可能在队列里留下多行同一首歌，
     * 会让"随机播放"在行上均匀取样时偏向重复的那几首（用户体感：40 首只有四五首在循环）。
     */
    suspend fun dedupePlaylist(): Int {
        val removed = dao.removeDuplicateRows()
        if (removed > 0) {
            android.util.Log.w("PlaylistManager", "清理重复曲目 $removed 行")
            _events.tryEmit(Unit)
        }
        return removed
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
