package com.neko.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.neko.music.R
import com.neko.music.data.manager.PlaylistManager
import com.neko.music.data.model.Music
import com.neko.music.ui.theme.RoseRed
import kotlinx.coroutines.launch

@Composable
fun PlaylistScreen(
    isVisible: Boolean,
    currentMusicId: Int?,
    onBackClick: () -> Unit,
    onMusicClick: (Music) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playlistManager = PlaylistManager.getInstance(context)
    val playlist by playlistManager.playlist.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onBackClick)
        ) {
            // 处理返回键
            BackHandler(enabled = isVisible) {
                onBackClick()
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(600.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(if (isDarkTheme) Color(0xFF1A1A2E).copy(alpha = 0.95f) else Color.White)
                    .clickable(enabled = false) {},
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部栏
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
                            tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.9f) else Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = stringResource(id = R.string.playback_list),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else Color.Black
                    )

                    // 清空按钮
                    androidx.compose.material3.TextButton(
                        onClick = {
                            scope.launch {
                                // 清空列表但保留当前歌曲
                                currentMusicId?.let { playlistManager.clearPlaylistExcept(it) }
                            }
                        }
                    ) {
                        Text(
                            text = stringResource(id = R.string.clear),
                            fontSize = 14.sp,
                            color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray
                        )
                    }
                }

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(if (isDarkTheme) Color(0xFF2A2A4E).copy(alpha = 0.5f) else Color(0xFFE0E0E0))
                )

                // 播放列表
                if (playlist.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.playlist_empty),
                            fontSize = 14.sp,
                            color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(playlist) { music ->
                            PlaylistItem(
                                music = music,
                                isPlaying = music.id == currentMusicId,
                                onClick = {
                                    onMusicClick(music)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistItem(
    music: Music,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
            .background(Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isDarkTheme) Color(0xFF252545).copy(alpha = 0.6f) else Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            val coverUrl = if (!music.coverFilePath.isNullOrEmpty()) {
                "https://music.cnmsb.xin${music.coverFilePath}"
            } else {
                "https://music.cnmsb.xin/api/music/cover/${music.id}"
            }
            AsyncImage(
                model = coverUrl,
                contentDescription = "封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 歌曲信息
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = music.title,
                fontSize = 15.sp,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                color = if (isPlaying) RoseRed else if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else Color.Black,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = music.artist,
                fontSize = 13.sp,
                color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray,
                maxLines = 1
            )
        }
    }
}