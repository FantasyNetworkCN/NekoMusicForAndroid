package com.neko.music.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.AsyncImage
import com.neko.music.R

sealed class BottomNavItem(
    val route: String,
    val titleResId: Int
) {
    object Home : BottomNavItem("home", R.string.nav_home)
    object Mine : BottomNavItem("mine", R.string.nav_mine)
    object MyPlaylists : BottomNavItem("my_playlists", R.string.nav_my_playlists)
}

@Composable
fun BottomNavigationBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Mine,
        BottomNavItem.MyPlaylists
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 计算选中项的索引
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }

    // 动态光效动画
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // 获取当前主题的背景色
    val isDarkTheme = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < 0.5f

    GlassSurface(
        modifier = modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(28.dp),
        backgroundAlpha = 0.32f,
        borderAlpha = 0.15f,
        highlightAlpha = 0.08f
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = currentRoute == item.route

                val scaleValue by animateFloatAsState(
                    targetValue = if (isSelected) 1.05f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )

                val indicatorAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = tween(300)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            if (currentRoute != item.route) {
                                navController.navigate(item.route) {
                                    popUpTo(item.route) { inclusive = true }
                                    launchSingleTop = true
                                }
                            } else {
                                navController.popBackStack(item.route, inclusive = false)
                            }
                        }
                        .scale(scaleValue),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(id = item.titleResId),
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White.copy(alpha = 0.98f) else Color.White.copy(alpha = 0.5f),
                            letterSpacing = 0.3.sp,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = if (isSelected) {
                                    Shadow(
                                        color = Color.Black.copy(alpha = 0.5f),
                                        offset = Offset(0f, 1f),
                                        blurRadius = 4f
                                    )
                                } else null
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // 选中指示器
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(Color.White.copy(alpha = 0.9f * indicatorAlpha))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(
    isPlaying: Boolean = false,
    songTitle: String = "",
    artist: String = "",
    coverUrl: String? = null,
    progress: Float = 0f,
    onPlayPauseClick: () -> Unit = {},
    onPlayerClick: () -> Unit = {},
    onPlaylistClick: () -> Unit = {}
) {
    var isPressed by remember { mutableStateOf(false) }
    val scaleValue by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    // 脉冲动画
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // 获取当前主题的背景色
    val isDarkTheme = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < 0.5f

    GlassSurface(
        modifier = Modifier.fillMaxWidth().height(68.dp),
        shape = RoundedCornerShape(28.dp),
        backgroundAlpha = 0.32f,
        borderAlpha = 0.15f,
        highlightAlpha = 0.08f
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        isPressed = true
                        onPlayerClick()
                    }
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .scale(scaleValue)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧：封面、歌曲信息
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // 封面
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1A1A2E).copy(alpha = 0.4f))
                            .then(
                                if (isPlaying) {
                                    Modifier.scale(pulseScale)
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!coverUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = stringResource(id = R.string.content_description_cover),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Image(
                                painter = painterResource(R.drawable.music),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // 歌曲信息
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = songTitle.ifEmpty { stringResource(id = R.string.player_no_music) },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.98f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    offset = Offset(0f, 1f),
                                    blurRadius = 4f
                                )
                            )
                        )
                        Text(
                            text = artist.ifEmpty { stringResource(id = R.string.player_click_to_play) },
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.4f),
                                    offset = Offset(0f, 1f),
                                    blurRadius = 3f
                                )
                            )
                        )
                    }
                }

                // 右侧：播放/暂停按钮、播放列表
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 播放/暂停按钮（带圆形进度条）
                    Box(
                        modifier = Modifier.size(46.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 圆形进度条
                        Canvas(
                            modifier = Modifier.size(46.dp)
                        ) {
                            val strokeWidth = 3.dp.toPx()
                            val radius = size.minDimension / 2 - strokeWidth / 2 - 2.dp.toPx()
                            val center = Offset(size.width / 2, size.height / 2)

                            // 背景圆环
                            drawCircle(
                                color = Color.White.copy(alpha = 0.15f),
                                radius = radius,
                                center = center,
                                style = Stroke(
                                    width = strokeWidth,
                                    cap = StrokeCap.Round
                                )
                            )

                            // 进度圆环
                            if (progress > 0f) {
                                drawArc(
                                    color = Color.White.copy(alpha = 0.9f),
                                    startAngle = -90f,
                                    sweepAngle = 360f * progress,
                                    useCenter = false,
                                    style = Stroke(
                                        width = strokeWidth,
                                        cap = StrokeCap.Round
                                    ),
                                    size = Size(radius * 2, radius * 2),
                                    topLeft = Offset(center.x - radius, center.y - radius)
                                )
                            }
                        }

                        // 播放/暂停按钮
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.95f))
                                .clickable(onClick = onPlayPauseClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isPlaying) R.drawable.pause else R.drawable.play
                                ),
                                contentDescription = if (isPlaying) stringResource(id = R.string.content_description_pause) else stringResource(id = R.string.content_description_play),
                                tint = Color(0xFF1A1A2E),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 播放列表按钮
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(
                                width = 0.5.dp,
                                color = Color.White.copy(alpha = 0.18f),
                                shape = CircleShape
                            )
                            .clickable(onClick = onPlaylistClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.playlist),
                            contentDescription = stringResource(id = R.string.content_description_playlist),
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}