package com.neko.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.neko.music.R
import com.neko.music.ui.theme.Lilac
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.SkyBlue

// LatestMusicCard组件，用于在HomeScreen中显示最新音乐
@Composable
fun LatestMusicCard(
    musicList: List<com.neko.music.data.model.Music>,
    onClick: () -> Unit
) {
    // 预加载字符串资源
    val latestMusicTitle = stringResource(id = R.string.latest_music_title)
    val clickLatestMusicInfo = stringResource(id = R.string.click_latest_music_info, musicList.size)
    val songsCountFormat = stringResource(id = R.string.songs_count_format, musicList.size)
    
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                android.util.Log.d("LatestMusicCard", clickLatestMusicInfo)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 封面
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            SkyBlue.copy(alpha = 0.25f),
                            Lilac.copy(alpha = 0.15f)
                        )
                    )
                )
                .shadow(
                    elevation = 4.dp,
                    spotColor = SkyBlue.copy(alpha = 0.2f),
                    ambientColor = Color.Gray.copy(alpha = 0.1f)
                )
        ) {
            if (musicList.isNotEmpty()) {
                val topMusic = musicList[0]
                val context = LocalContext.current
                val musicApi = remember { com.neko.music.data.api.MusicApi(context) }
                var coverUrl by remember { mutableStateOf<String?>(null) }
                
                LaunchedEffect(topMusic.id) {
                    coverUrl = musicApi.getMusicCoverUrl(topMusic)
                }
                
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverUrl ?: "https://music.cnmsb.xin/api/user/avatar/default")
                        .crossfade(true)
                        .build(),
                    contentDescription = latestMusicTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎵",
                        fontSize = 48.sp
                    )
                }
            }
            
            // 音乐数量标签
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(
                        color = SkyBlue.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = songsCountFormat,
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        // 标题
        Text(
            text = latestMusicTitle,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = SkyBlue.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(160.dp)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // 描述
        Text(
            text = stringResource(id = R.string.latest_songs),
            fontSize = 12.sp,
            color = SkyBlue.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(160.dp)
        )
    }
}