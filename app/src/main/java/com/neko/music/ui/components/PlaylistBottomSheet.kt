package com.neko.music.ui.components

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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.neko.music.R
import com.neko.music.data.manager.PlaylistManager
import com.neko.music.data.model.Music
import com.neko.music.ui.theme.RoseRed
import kotlinx.coroutines.launch

@Composable
fun PlaylistBottomSheet(
    isVisible: Boolean,
    currentMusicId: Int?,
    onDismiss: () -> Unit,
    onMusicClick: (Music) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playlistManager = PlaylistManager.getInstance(context)
    val playlist by playlistManager.playlist.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    android.util.Log.d("PlaylistBottomSheet", "PlaylistBottomSheet 渲染: isVisible=$isVisible, playlist size=${playlist.size}")

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(600.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(Color.White)
                    .clickable(enabled = false) {}
            ) {
                // 顶部栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "播放列表",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 清空列表按钮
                        androidx.compose.material3.TextButton(
                            onClick = {
                                currentMusicId?.let { id ->
                                    scope.launch {
                                        playlistManager.clearPlaylistExcept(id)
                                    }
                                }
                            }
                        ) {
                            Text(
                                text = "清空",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 播放列表
                if (playlist.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "播放列表为空",
                            fontSize = 14.sp,
                            color = Color.Gray
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
                                    onDismiss()
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick)
            .background(if (isPlaying) RoseRed.copy(alpha = 0.1f) else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            val coverUrl = if (!music.coverFilePath.isNullOrEmpty()) {
                if (music.coverFilePath.startsWith("http")) {
                    music.coverFilePath
                } else {
                    "https://music.cnmsb.xin${music.coverFilePath}"
                }
            } else {
                "https://music.cnmsb.xin/api/music/cover/${music.id}"
            }
            android.util.Log.d("PlaylistBottomSheet", "音乐: ${music.title}, coverFilePath: ${music.coverFilePath}, 最终URL: $coverUrl")
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
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                color = if (isPlaying) RoseRed else Color.Black,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = music.artist,
                fontSize = 13.sp,
                color = Color.Gray,
                maxLines = 1
            )
        }

        // 播放图标
        if (isPlaying) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.pause),
                contentDescription = "正在播放",
                tint = RoseRed,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
