package com.neko.music.util

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 歌词滚动状态管理器
 * 用于在播放页面、灵动岛和桌面歌词之间同步歌词滚动状态
 */
object LyricScrollManager {
    
    // 当前歌词索引
    private val _currentLyricIndex = MutableStateFlow(0)
    val currentLyricIndex: StateFlow<Int> = _currentLyricIndex.asStateFlow()
    
    // 播放页面的滚动状态
    private val _playerPageScrollState = MutableStateFlow<LazyListState?>(null)
    val playerPageScrollState: StateFlow<LazyListState?> = _playerPageScrollState.asStateFlow()
    
    // 是否在播放页面滚动（防止循环更新）
    private var isPlayerPageScrolling = false
    
    /**
     * 设置当前歌词索引
     * @param index 歌词索引
     * @param source 来源：player_page（播放页面）或其他
     */
    fun setCurrentLyricIndex(index: Int, source: String = "other") {
        if (index < 0) return
        
        android.util.Log.d("LyricScrollManager", "setCurrentLyricIndex: index=$index, source=$source")
        
        _currentLyricIndex.value = index
        
        // 如果不是播放页面触发的，则同步滚动播放页面
        if (source != "player_page" && !isPlayerPageScrolling) {
            playerPageScrollState.value?.let { state ->
                try {
                    isPlayerPageScrolling = true
                    // 使用 scrollToItem 而不是 animateScrollToItem，避免需要 MonotonicFrameClock
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        state.scrollToItem(index, 0)
                        kotlinx.coroutines.delay(300)
                        isPlayerPageScrolling = false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LyricScrollManager", "Failed to sync player page scroll", e)
                    isPlayerPageScrolling = false
                }
            }
        }
    }
    
    /**
     * 设置播放页面的滚动状态
     * @param state LazyListState实例
     */
    fun setPlayerPageScrollState(state: LazyListState) {
        _playerPageScrollState.value = state
    }
    
    /**
     * 获取当前歌词索引
     */
    fun getCurrentLyricIndex(): Int {
        return _currentLyricIndex.value
    }
}