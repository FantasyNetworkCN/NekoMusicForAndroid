package com.neko.music.data.manager

import android.content.Context
import android.content.SharedPreferences
import com.neko.music.data.model.SearchHistory
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SearchHistoryManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val maxHistorySize = 20
    
    fun saveSearch(query: String) {
        val history = getSearchHistory().toMutableList()
        
        // 移除已存在的相同查询
        history.removeAll { it.query == query && it.type == "keyword" }
        
        // 添加新查询到开头
        history.add(0, SearchHistory(query, System.currentTimeMillis(), "keyword"))
        
        // 限制历史记录数量
        if (history.size > maxHistorySize) {
            history.removeAt(history.size - 1)
        }
        
        saveHistory(history)
    }
    
    fun addSearchHistory(musicName: String, searchQuery: String) {
        val history = getSearchHistory().toMutableList()
        
        // 移除已存在的相同单曲
        history.removeAll { it.query == musicName && it.type == "music" }
        
        // 添加单曲名称到开头
        history.add(0, SearchHistory(musicName, System.currentTimeMillis(), "music"))
        
        // 限制历史记录数量
        if (history.size > maxHistorySize) {
            history.removeAt(history.size - 1)
        }
        
        saveHistory(history)
    }
    
    fun getSearchHistory(): List<SearchHistory> {
        val historyJson = prefs.getString("history", "[]") ?: "[]"
        return try {
            json.decodeFromString<List<SearchHistory>>(historyJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun clearHistory() {
        prefs.edit().remove("history").apply()
    }
    
    private fun saveHistory(history: List<SearchHistory>) {
        val historyJson = json.encodeToString(history)
        prefs.edit().putString("history", historyJson).apply()
    }
    
    fun deleteSearch(query: String) {
        val history = getSearchHistory().toMutableList()
        history.removeAll { it.query == query }
        saveHistory(history)
    }
}