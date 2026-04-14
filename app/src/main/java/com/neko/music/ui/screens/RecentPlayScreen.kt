package com.neko.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.neko.music.R
import com.neko.music.util.UrlConfig
import com.neko.music.data.model.Music
import com.neko.music.service.MusicPlayerManager
import com.neko.music.ui.theme.RoseRed
import kotlinx.coroutines.launch

@Composable
fun RecentPlayScreen(
    onBackClick: () -> Unit,
    onMusicClick: (Music) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playerManager = MusicPlayerManager.getInstance(context)
    val scope = rememberCoroutineScope()
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    
    var playHistory by remember { mutableStateOf<List<Music>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    
    // 加载最近播放历史
    LaunchedEffect(Unit) {
        isLoading = true
        scope.launch {
            val recentPlayManager = com.neko.music.data.manager.RecentPlayManager(context)
            playHistory = recentPlayManager.getRecentPlays()
            isLoading = false
        }
    }
    
    // 过滤后的列表
    val filteredList = if (searchQuery.isEmpty()) {
        playHistory
    } else {
        playHistory.filter { music ->
            music.title.contains(searchQuery, ignoreCase = true) ||
            music.artist.contains(searchQuery, ignoreCase = true)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDarkTheme) Color(0xFF121228) else Color.White)
            .statusBarsPadding()
    ) {
        // 顶部导航栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(id = R.string.back),
                    tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.9f) else Color.Black
                )
            }
            
            Text(
                text = stringResource(id = R.string.recent_play),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else Color.Black
            )
            
            Spacer(modifier = Modifier.size(48.dp))
        }
        
        // 搜索框
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 内容区域
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = RoseRed)
            }
        } else if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isEmpty()) stringResource(id = R.string.no_play_history) else stringResource(id = R.string.no_related_songs),
                    fontSize = 16.sp,
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 150.dp)
            ) {
                items(filteredList) { music ->
                    RecentPlayItem(
                        music = music,
                        onClick = { onMusicClick(music) }
                    )
                }
            }
        }
    }
}

@Composable
fun RecentPlayItem(
    music: Music,
    onClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playerManager = MusicPlayerManager.getInstance(context)
    val scope = rememberCoroutineScope()
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    
    // 获取当前播放的音乐ID
    val currentMusicId by playerManager.currentMusicId.collectAsState()
    
    // 构建完整的封面URL
    val fullCoverUrl = if (!music.coverFilePath.isNullOrEmpty()) {
        UrlConfig.buildFullUrl("${music.coverFilePath}")
    } else {
        UrlConfig.getMusicCoverUrl(music.id)
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(
                if (currentMusicId == music.id) 
                    RoseRed.copy(alpha = 0.15f) 
                else 
                    Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    RoseRed.copy(alpha = 0.1f),
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = fullCoverUrl,
                contentDescription = "封面",
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 歌曲信息
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = music.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else Color.Black,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = music.artist,
                fontSize = 14.sp,
                color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray,
                maxLines = 1
            )
        }
        
        // 播放按钮
        IconButton(
            onClick = {
                scope.launch {
                    val musicApi = com.neko.music.data.api.MusicApi(context)
                    val url = musicApi.getMusicFileUrl(music)
                    playerManager.playMusic(url, music.id, music.title, music.artist, music.coverFilePath, fullCoverUrl)
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "播放",
                tint = RoseRed,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/**
 * 搜索框组件
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    
    Row(
        modifier = modifier
            .height(40.dp)
            .background(
                color = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color(0xFFF5F5F5),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = stringResource(id = R.string.search),
            tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.7f) else Color.Gray,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else Color.Black
            ),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(id = R.string.search_songs),
                        fontSize = 14.sp,
                        color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.6f) else Color.Gray
                    )
                }
                innerTextField()
            }
        )
    }
}