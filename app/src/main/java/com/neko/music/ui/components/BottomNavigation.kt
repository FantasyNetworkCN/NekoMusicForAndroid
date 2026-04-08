package com.neko.music.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import com.neko.music.R
import com.neko.music.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

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

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // 背景层：渐变背景（根据主题切换）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = if (isDarkTheme) {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1A1A2E).copy(alpha = 0.85f),
                                Color(0xFF16213E).copy(alpha = 0.75f)
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.95f),
                                Color.White.copy(alpha = 0.88f)
                            )
                        )
                    }
                )
        )

        // 顶部高光
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            if (isDarkTheme) {
                                Color(0xFF2A2A4E).copy(alpha = 0.5f)
                            } else {
                                Color.White.copy(alpha = 0.6f)
                            },
                            Color.Transparent
                        )
                    )
                )
        )

        // 内容层：清晰的文字和装饰
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            // 背景装饰层
            Canvas(
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                val width = size.width
                val height = size.height
                val itemWidth = width / items.size
                val centerX = itemWidth * selectedIndex + itemWidth / 2

                // 绘制底部装饰线
                drawLine(
                    color = RoseRed.copy(alpha = 0.2f),
                    start = Offset(centerX - 30.dp.toPx(), height - 2.dp.toPx()),
                    end = Offset(centerX + 30.dp.toPx(), height - 2.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 导航栏文字
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                items.forEach { item ->
                    val isSelected = currentRoute == item.route

                    val scaleValue by animateFloatAsState(
                        targetValue = if (isSelected) 1.15f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
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
                                    // 切换到不同的标签页，先清除该路由的所有历史记录
                                    navController.popBackStack(item.route, inclusive = true)
                                    // 然后导航到目标路由，不保存状态以确保每次都回到首页
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = false
                                        }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                } else {
                                    // 点击当前选中的标签页，返回到该标签页的首页
                                    navController.popBackStack(item.route, inclusive = false)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = item.titleResId),
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) {
                                RoseRed
                            } else {
                                if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.9f) else Color.Gray
                            },
                            modifier = Modifier.scale(scaleValue)
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(24.dp))
        ) {
            // 渐变背景（根据主题切换）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = if (isDarkTheme) {
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1A1A2E).copy(alpha = 0.82f),
                                    Color(0xFF16213E).copy(alpha = 0.72f)
                                )
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.92f),
                                    Color.White.copy(alpha = 0.85f)
                                )
                            )
                        }
                    )
            )

            // 顶部高光
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                if (isDarkTheme) {
                                    Color(0xFF2A2A4E).copy(alpha = 0.6f)
                                } else {
                                    Color.White.copy(alpha = 0.7f)
                                },
                                Color.Transparent
                            )
                        )
                    )
            )

            // 内容层
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
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            RoseRed.copy(alpha = 0.12f),
                                            SakuraPink.copy(alpha = 0.12f)
                                        )
                                    )
                                )
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
                                    modifier = Modifier.size(26.dp)
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
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else Color.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = artist.ifEmpty { stringResource(id = R.string.player_click_to_play) },
                                fontSize = 13.sp,
                                color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.85f) else Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // 圆形进度条
                            Canvas(
                                modifier = Modifier.size(48.dp)
                            ) {
                                val strokeWidth = 3.5.dp.toPx()
                                val radius = size.minDimension / 2 - strokeWidth / 2 - 2.dp.toPx()
                                val center = Offset(size.width / 2, size.height / 2)

                                // 背景圆环
                                drawCircle(
                                    color = if (isDarkTheme) Color(0xFF353558).copy(alpha = 0.6f) else Color(0xFFE8E8E8),
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
                                        color = RoseRed,
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
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                RoseRed,
                                                SakuraPink
                                            )
                                        )
                                    )
                                    .clickable(onClick = onPlayPauseClick)
                                    .shadow(
                                        elevation = 6.dp,
                                        spotColor = RoseRed.copy(alpha = 0.5f),
                                        ambientColor = RoseRed.copy(alpha = 0.25f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (isPlaying) R.drawable.pause else R.drawable.play
                                    ),
                                    contentDescription = if (isPlaying) stringResource(id = R.string.content_description_pause) else stringResource(id = R.string.content_description_play),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // 播放列表按钮
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (isDarkTheme) Color(0xFF252545).copy(alpha = 0.7f) else Color(0xFFF3F3F3))
                                .clickable(onClick = onPlaylistClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.playlist),
                                contentDescription = stringResource(id = R.string.content_description_playlist),
                                tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.9f) else Color.Gray,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}