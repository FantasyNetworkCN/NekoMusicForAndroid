package com.neko.music.data.manager

import android.content.Context
import android.content.SharedPreferences
import com.neko.music.data.model.Music
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * 最近播放管理器
 * 负责管理最近播放列表的永久本地存储
 */
class RecentPlayManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        private const val PREFS_NAME = "recent_play_prefs"
        private const val KEY_RECENT_PLAYS = "recent_plays"
        private const val MAX_RECENT_PLAYS = 20
    }
    
    /**
     * 添加到最近播放列表
     */
    fun addRecentPlay(music: Music) {
        val recentPlays = getRecentPlays().toMutableList()
        
        // 如果已存在，先移除
        recentPlays.removeAll { it.id == music.id }
        
        // 添加到最前面
        recentPlays.add(0, music)
        
        // 限制数量
        if (recentPlays.size > MAX_RECENT_PLAYS) {
            recentPlays.removeAt(recentPlays.size - 1)
        }
        
        saveRecentPlays(recentPlays)
    }
    
    /**
     * 获取最近播放列表
     */
    fun getRecentPlays(): List<Music> {
        val jsonString = prefs.getString(KEY_RECENT_PLAYS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<Music>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 清空最近播放列表
     */
    fun clearRecentPlays() {
        prefs.edit().remove(KEY_RECENT_PLAYS).apply()
    }
    
    /**
     * 保存最近播放列表
     */
    private fun saveRecentPlays(musicList: List<Music>) {
        val jsonString = json.encodeToString(musicList)
        prefs.edit().putString(KEY_RECENT_PLAYS, jsonString).apply()
    }
}