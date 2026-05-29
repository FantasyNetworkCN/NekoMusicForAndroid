package com.neko.music.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.neko.music.R
import com.neko.music.util.UrlConfig
import com.neko.music.ui.theme.*
import com.neko.music.data.manager.AppBackgroundKind
import com.neko.music.ui.components.AppPageBackgroundImage
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.VipPill
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.delay

@Composable
fun MineScreen(
    onRecentPlayClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onAccountInfoClick: () -> Unit = {},
    onUploadClick: () -> Unit = {},
    onVipCenterClick: () -> Unit = {},
    isLoggedIn: Boolean = false,
    username: String? = null,
    userId: Int = -1,
    isVip: Boolean = false,
    vipExpiresAt: String? = null,
    onLoginSuccess: () -> Unit = {},
    token: String? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    SideEffect {
        val window = (view.context as android.app.Activity).window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }
    
    val scheme = MaterialTheme.colorScheme
    val isDarkTheme = isSystemInDarkTheme()
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)
    val glassTint = LiquidGlassDefaults.screenListCard
    val glassBg = glassTint.background(isDarkTheme)
    val glassBorder = glassTint.border(isDarkTheme)
    val glassHighlight = glassTint.highlight(isDarkTheme)
    val dialogLiquid = LiquidGlassDefaults.appUpdateDialog.liquid
    val glassBorderColor = if (isDarkTheme) {
        SakuraPink.copy(alpha = LiquidGlassDefaults.appUpdateDialogDarkBorderSakuraAlpha)
    } else {
        scheme.outline
    }
    val scrimAlpha = if (isDarkTheme) 0.42f else 0.28f

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop),
        ) {
            AppPageBackgroundImage(
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = scrimAlpha * 0.35f),
                                Color.Black.copy(alpha = scrimAlpha),
                            ),
                        ),
                    ),
            )
        }

        CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 160.dp),
            ) {
                item {
                    MineHeader(
                        onLoginClick = onLoginClick,
                        isLoggedIn = isLoggedIn,
                        username = username,
                        userId = userId,
                        isVip = isVip,
                        vipExpiresAt = vipExpiresAt,
                        onVipEntryClick = onVipCenterClick,
                        onLogoutClick = onLogoutClick,
                        onAccountInfoClick = onAccountInfoClick,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(20.dp),
                        sampleBackdrop = pageBackdrop,
                        backgroundAlpha = glassBg,
                        borderAlpha = glassBorder,
                        highlightAlpha = glassHighlight,
                        borderColor = glassBorderColor,
                        liquidBlur = dialogLiquid.blur,
                        liquidLensHeight = dialogLiquid.lensHeight,
                        liquidLensAmount = dialogLiquid.lensAmount,
                    ) {
                        MineStats(
                            onUploadClick = onUploadClick,
                            token = token,
                            useOuterPadding = false,
                            embeddedInGlass = true
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(20.dp),
                        sampleBackdrop = pageBackdrop,
                        backgroundAlpha = glassBg,
                        borderAlpha = glassBorder,
                        highlightAlpha = glassHighlight,
                        borderColor = glassBorderColor,
                        liquidBlur = dialogLiquid.blur,
                        liquidLensHeight = dialogLiquid.lensHeight,
                        liquidLensAmount = dialogLiquid.lensAmount,
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            MineMenu(
                                onRecentPlayClick = onRecentPlayClick,
                                onFavoriteClick = onFavoriteClick,
                                onVipCenterClick = onVipCenterClick,
                                isLoggedIn = isLoggedIn,
                                onLogoutClick = onLogoutClick,
                                onLoginClick = onLoginClick,
                                useElevatedMenuItems = false,
                                contentHorizontalPadding = 0.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(20.dp),
                        sampleBackdrop = pageBackdrop,
                        backgroundAlpha = glassBg,
                        borderAlpha = glassBorder,
                        highlightAlpha = glassHighlight,
                        borderColor = glassBorderColor,
                        liquidBlur = dialogLiquid.blur,
                        liquidLensHeight = dialogLiquid.lensHeight,
                        liquidLensAmount = dialogLiquid.lensAmount,
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            MoreSettings(
                                onAboutClick = onAboutClick,
                                onNavigateToSettings = onNavigateToSettings,
                                isLoggedIn = isLoggedIn,
                                onLoginClick = onLoginClick,
                                onLogoutClick = onLogoutClick,
                                useElevatedMenuItems = false,
                                contentHorizontalPadding = 0.dp,
                                showFooter = false,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(20.dp),
                        sampleBackdrop = pageBackdrop,
                        backgroundAlpha = glassBg * 0.92f,
                        borderAlpha = glassBorder,
                        highlightAlpha = glassHighlight,
                        borderColor = glassBorderColor,
                        liquidBlur = dialogLiquid.blur,
                        liquidLensHeight = dialogLiquid.lensHeight,
                        liquidLensAmount = dialogLiquid.lensAmount,
                    ) {
                        MineScreenFooter(
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun MineHeader(
    onLoginClick: () -> Unit = {},
    isLoggedIn: Boolean = false,
    username: String? = null,
    userId: Int = -1,
    isVip: Boolean = false,
    vipExpiresAt: String? = null,
    onVipEntryClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onAccountInfoClick: () -> Unit = {},
) {
    val context = LocalContext.current
    
    // 头像更新时间戳，用于绕过缓存
    var avatarUpdateTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    // 监听登录状态变化，重新加载头像
    LaunchedEffect(isLoggedIn, userId) {
        avatarUpdateTime = System.currentTimeMillis()
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(268.dp)
    ) {
        AppPageBackgroundImage(
            kind = com.neko.music.data.manager.AppBackgroundKind.MineHeader,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                    ),
                ),
        )
        // 装饰圆圈
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawCircle(
                color = SakuraPink.copy(alpha = 0.15f),
                radius = 100.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.3f)
            )
            drawCircle(
                color = SkyBlue.copy(alpha = 0.1f),
                radius = 70.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.6f)
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // 头像容器（会员金色描边，与 Web 资料页 VIP 感一致）
            val avatarShape = RoundedCornerShape(24.dp)
            val vipRingBrush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFE082), Color(0xFFFFB300), Color(0xFFFFA000))
            )
            val avatarOuter = if (isLoggedIn && isVip) 96.dp else 90.dp
            val avatarInner = if (isLoggedIn && isVip) 88.dp else 90.dp
            Box(
                modifier = Modifier.size(avatarOuter),
                contentAlignment = Alignment.Center
            ) {
                if (isLoggedIn && isVip) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(width = 2.5.dp, brush = vipRingBrush, shape = avatarShape)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(avatarInner)
                        .clip(avatarShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .shadow(
                            elevation = 8.dp,
                            spotColor = if (isVip) Color(0xFFFFB300).copy(alpha = 0.45f) else RoseRed.copy(alpha = 0.4f),
                            ambientColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                        .clickable {
                            if (isLoggedIn) {
                                onAccountInfoClick()
                            } else {
                                onLoginClick()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                if (isLoggedIn && userId != -1) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(UrlConfig.getUserAvatarUrl(userId))
                            .memoryCacheKey("avatar_${userId}_$avatarUpdateTime")
                            .diskCacheKey("avatar_${userId}_$avatarUpdateTime")
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(id = R.string.user_avatar),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(UrlConfig.getDefaultAvatarUrl())
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(id = R.string.default_avatar),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isLoggedIn && username != null) username else stringResource(id = R.string.not_logged_in),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF6B6B),
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            if (isLoggedIn) {
                Spacer(modifier = Modifier.height(10.dp))
                VipPill(
                    isVip = isVip,
                    onClick = onVipEntryClick
                )
            }
        }
    }
}

@Composable
fun MineStats(
    onUploadClick: () -> Unit = {},
    token: String? = null,
    useOuterPadding: Boolean = true,
    embeddedInGlass: Boolean = false
) {
    var uploadCount by remember { mutableStateOf(0) }
    val context = LocalContext.current
    
    LaunchedEffect(token) {
        if (token != null) {
            try {
                val userApi = com.neko.music.data.api.UserApi(token)
                val response = userApi.getUploadedMusic()
                if (response.success) {
                    uploadCount = response.total
                }
            } catch (e: Exception) {
                // 静默失败，保持默认值0
            }
        }
    }

    val rowModifier = Modifier
        .fillMaxWidth()
        .then(
            if (useOuterPadding) {
                Modifier.padding(horizontal = 20.dp)
            } else {
                Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            }
        )
        .then(
            if (embeddedInGlass) {
                Modifier
            } else {
                Modifier
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                RoseRed.copy(alpha = 0.12f),
                                SakuraPink.copy(alpha = 0.12f)
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .shadow(
                        elevation = 4.dp,
                        spotColor = RoseRed.copy(alpha = 0.2f),
                        ambientColor = Color.Gray.copy(alpha = 0.1f)
                    )
            }
        )
        .padding(vertical = 16.dp, horizontal = 16.dp)

    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.Center
    ) {
        StatItem(uploadCount.toString(), stringResource(id = R.string.upload), onUploadClick = onUploadClick)
    }
}

@Composable
fun StatItem(count: String, label: String, onUploadClick: () -> Unit = {}) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(140)
            isPressed = false
        }
    }

    Column(
        modifier = Modifier
            .scale(scale)
            .clickable {
                isPressed = true
                onUploadClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = RoseRed
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun NekoMemberBanner() {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.95f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(100.dp)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        RoseRed.copy(alpha = 0.15f),
                        SakuraPink.copy(alpha = 0.15f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .scale(scale)
            .shadow(
                elevation = 4.dp,
                spotColor = RoseRed.copy(alpha = 0.2f),
                ambientColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
            )
            .clickable {
                // 暂未实现
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 内容已移除
        }
    }
}

@Composable
fun MineMenu(
    onRecentPlayClick: () -> Unit = {},
    onFavoriteClick: () -> Unit = {},
    onVipCenterClick: () -> Unit = {},
    isLoggedIn: Boolean = false,
    onLogoutClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    useElevatedMenuItems: Boolean = true,
    /** 嵌入 [GlassSurface] 时由外层留白，此处传 0.dp */
    contentHorizontalPadding: Dp = 20.dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = contentHorizontalPadding)
    ) {
        Text(
            text = stringResource(id = R.string.mine),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        MenuItem(
            stringResource(id = R.string.my_favorites),
            R.drawable.ic_favorite_filled,
            SakuraPink,
            onClick = onFavoriteClick,
            useElevatedSurface = useElevatedMenuItems
        )
        MenuItem(
            stringResource(id = R.string.vip_menu_entry),
            R.drawable.ic_vip_star,
            Color(0xFFFFB300),
            onClick = {
                if (isLoggedIn) onVipCenterClick() else onLoginClick()
            },
            useElevatedSurface = useElevatedMenuItems
        )
        MenuItem(
            stringResource(id = R.string.recent_play),
            R.drawable.recently_played,
            SkyBlue,
            onClick = onRecentPlayClick,
            useElevatedSurface = useElevatedMenuItems
        )
    }
}

@Composable
fun MenuItem(
    title: String,
    iconResId: Int,
    iconColor: Color = RoseRed,
    onClick: () -> Unit = {},
    useElevatedSurface: Boolean = true
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(120)
            isPressed = false
        }
    }

    val rowShape = RoundedCornerShape(16.dp)
    val elevatedModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)
        .background(
            color = MaterialTheme.colorScheme.surface,
            shape = rowShape
        )
        .padding(16.dp)
        .shadow(
            elevation = 2.dp,
            spotColor = RoseRed.copy(alpha = 0.15f),
            ambientColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
        )
    val flatModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
        .padding(horizontal = 4.dp)

    Row(
        modifier = Modifier
            .then(if (useElevatedSurface) elevatedModifier else flatModifier)
            .scale(scale)
            .clickable {
                isPressed = true
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconResId),
                contentDescription = title,
                modifier = Modifier.size(28.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(iconColor)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "›",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun MoreSettings(
    onAboutClick: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    isLoggedIn: Boolean = false,
    onLoginClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    useElevatedMenuItems: Boolean = true,
    showFooter: Boolean = true,
    /** 嵌入 [GlassSurface] 时由外层留白 */
    contentHorizontalPadding: Dp = 20.dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = contentHorizontalPadding)
    ) {
        MenuItem(
            stringResource(id = R.string.settings_title),
            R.drawable.setting,
            RoseRed,
            onClick = onNavigateToSettings,
            useElevatedSurface = useElevatedMenuItems
        )
        MenuItem(
            stringResource(id = R.string.about_us),
            R.drawable.about,
            StarYellow,
            onClick = onAboutClick,
            useElevatedSurface = useElevatedMenuItems
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (isLoggedIn) {
            MenuItem(
                stringResource(id = R.string.logout),
                R.drawable.logout,
                Lilac,
                onClick = onLogoutClick,
                useElevatedSurface = useElevatedMenuItems
            )
        } else {
            MenuItem(
                stringResource(id = R.string.login),
                R.drawable.login,
                Peach,
                onClick = onLoginClick,
                useElevatedSurface = useElevatedMenuItems
            )
        }
    }

    if (showFooter) {
        MineScreenFooter()
    }
}

@Composable
private fun MineScreenFooter(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    modifier: Modifier = Modifier,
) {
    val muted = if (isDarkTheme) {
        Color(0xFFB8B8D1).copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            ExternalLinkButton(
                text = stringResource(id = R.string.api_docs),
                url = "https://github.com/FantasyNetworkCN/NekoMusicDocs",
                icon = R.drawable.about,
                isDarkTheme = isDarkTheme,
            )
            ExternalLinkButton(
                text = stringResource(id = R.string.github_repo),
                url = "https://github.com/FantasyNetworkCN/NekoMusicForAndroid",
                icon = R.drawable.about,
                isDarkTheme = isDarkTheme,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(id = R.string.footer_icp),
            fontSize = 10.sp,
            color = muted,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(id = R.string.footer_copyright),
            fontSize = 10.sp,
            color = muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ExternalLinkButton(
    text: String,
    url: String,
    icon: Int,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
) {
    val context = LocalContext.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
    )
    val chipBg = if (isDarkTheme) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.05f)
    }
    val chipBorder = if (isDarkTheme) {
        Color.White.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }
    val labelColor = if (isDarkTheme) {
        Color(0xFFF0F0F5).copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(chipBg)
            .border(1.dp, chipBorder, RoundedCornerShape(12.dp))
            .clickable {
                isPressed = true
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url),
                )
                context.startActivity(intent)
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = icon),
            contentDescription = text,
            modifier = Modifier.size(15.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(RoseRed),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = labelColor,
            fontWeight = FontWeight.Medium,
        )
    }
}