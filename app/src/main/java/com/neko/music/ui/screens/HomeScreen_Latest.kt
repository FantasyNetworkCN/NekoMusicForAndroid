package com.neko.music.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.neko.music.R
import com.neko.music.util.UrlConfig
import com.neko.music.ui.theme.Lilac
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.SakuraPink
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
    
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Column(
        modifier = Modifier
            .width(160.dp)
            .scale(scale)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                isPressed = true
                android.util.Log.d("LatestMusicCard", clickLatestMusicInfo)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 封面
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(20.dp))
                .shadow(
                    elevation = 8.dp,
                    spotColor = SkyBlue.copy(alpha = 0.3f),
                    ambientColor = Color.Gray.copy(alpha = 0.15f)
                )
        ) {
            // 背景渐变
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                SkyBlue.copy(alpha = 0.35f),
                                Lilac.copy(alpha = 0.25f),
                                SakuraPink.copy(alpha = 0.2f)
                            )
                        )
                    )
            )
            
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
                        .data(coverUrl ?: UrlConfig.getDefaultAvatarUrl())
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
                    Image(
                        painter = painterResource(id = R.drawable.music),
                        contentDescription = latestMusicTitle,
                        modifier = Modifier.size(56.dp),
                        alpha = 0.7f
                    )
                }
            }
            
            // 渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.35f)
                            )
                        )
                    )
            )
            
            // 光泽效果
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.White.copy(alpha = 0.05f)
                            ),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(160f, 160f)
                        )
                    )
            )
            
            // 音乐数量标签
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                SkyBlue.copy(alpha = 0.9f),
                                Lilac.copy(alpha = 0.85f)
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .shadow(
                        elevation = 4.dp,
                        spotColor = SkyBlue.copy(alpha = 0.3f),
                        ambientColor = Color.Gray.copy(alpha = 0.1f)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = songsCountFormat,
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 标题
        Text(
            text = latestMusicTitle,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = SkyBlue.copy(alpha = 0.95f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(160.dp),
            letterSpacing = 0.2.sp
        )
        
        Spacer(modifier = Modifier.height(5.dp))
        
        // 描述
        Text(
            text = stringResource(id = R.string.latest_songs),
            fontSize = 12.sp,
            color = SkyBlue.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(160.dp),
            fontWeight = FontWeight.Medium
        )
    }
}