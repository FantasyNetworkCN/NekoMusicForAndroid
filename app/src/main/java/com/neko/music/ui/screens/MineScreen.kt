package com.neko.music.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.neko.music.R
import com.neko.music.util.UrlConfig
import com.neko.music.ui.theme.*
import com.neko.music.data.manager.AppBackgroundKind
import com.neko.music.data.manager.NotificationCenter
import com.neko.music.ui.components.AppPageBackgroundImage
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.VipPill
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop

@Composable
fun MineScreen(
    onRecentPlayClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onLocalMusicClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onAccountInfoClick: () -> Unit = {},
    onUploadClick: () -> Unit = {},
    onVipCenterClick: () -> Unit = {},
    onQrScanClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    isLoggedIn: Boolean = false,
    nickname: String? = null,
    userId: Int = -1,
    isVip: Boolean = false,
    vipExpiresAt: String? = null,
    onLoginSuccess: () -> Unit = {},
    token: String? = null
) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as android.app.Activity).window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }
    
    val scheme = MaterialTheme.colorScheme
    val isDarkTheme = isAppDarkTheme()

    // 未读数来自消息长连接推送帧，不轮询
    val unreadCount by NotificationCenter.unread.collectAsState()
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)
    val glassTint = LiquidGlassDefaults.screenListCard
    val glassBg = glassTint.background(isDarkTheme)
    val glassBorder = glassTint.border(isDarkTheme)
    val glassHighlight = glassTint.highlight(isDarkTheme)
    val glassLiquid = LiquidGlassDefaults.liquidMedium
    val glassBorderColor = if (isDarkTheme) {
        SakuraPink.copy(alpha = LiquidGlassDefaults.appUpdateDialogDarkBorderSakuraAlpha)
    } else {
        scheme.outline
    }
    val listState = rememberLazyListState()
    val collapsedTopBarThreshold = with(LocalDensity.current) { 180.dp.roundToPx() }
    val showCollapsedTopBar by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > collapsedTopBarThreshold
        }
    }

    var uploadCount by remember(token) { mutableStateOf<Int?>(null) }
    LaunchedEffect(token) {
        if (token == null) {
            uploadCount = null
            return@LaunchedEffect
        }
        try {
            val response = com.neko.music.data.api.UserApi(token).getUploadedMusic()
            uploadCount = if (response.success) response.total else null
        } catch (e: Exception) {
            uploadCount = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop),
        ) {
            AppPageBackgroundImage(
                modifier = Modifier.fillMaxSize(),
            )
            com.neko.music.ui.components.PlaylistPageDarkTintOverlay(enabled = isDarkTheme)
        }

        CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 160.dp),
            ) {
                item {
                    MineHeader(
                        onLoginClick = onLoginClick,
                        isLoggedIn = isLoggedIn,
                        nickname = nickname,
                        userId = userId,
                        isVip = isVip,
                        vipExpiresAt = vipExpiresAt,
                        onVipEntryClick = onVipCenterClick,
                        onLogoutClick = onLogoutClick,
                        onAccountInfoClick = onAccountInfoClick,
                        onQrScanClick = onQrScanClick,
                        onNotificationsClick = onNotificationsClick,
                        unreadCount = unreadCount,
                    )

                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Spacer(modifier = Modifier.height(20.dp))

                        MineSectionLabel(stringResource(id = R.string.mine_section_music))
                        Spacer(modifier = Modifier.height(8.dp))

                        GlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            sampleBackdrop = pageBackdrop,
                            backgroundAlpha = glassBg,
                            borderAlpha = glassBorder,
                            highlightAlpha = glassHighlight,
                            borderColor = glassBorderColor,
                            liquidBlur = glassLiquid.blur,
                            liquidLensHeight = glassLiquid.lensHeight,
                            liquidLensAmount = glassLiquid.lensAmount,
                        ) {
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                MineMenu(
                                    onRecentPlayClick = onRecentPlayClick,
                                    onLocalMusicClick = onLocalMusicClick,
                                    onVipCenterClick = onVipCenterClick,
                                    onUploadClick = onUploadClick,
                                    uploadCount = uploadCount,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        MineSectionLabel(stringResource(id = R.string.mine_section_account))
                        Spacer(modifier = Modifier.height(8.dp))

                        GlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            sampleBackdrop = pageBackdrop,
                            backgroundAlpha = glassBg,
                            borderAlpha = glassBorder,
                            highlightAlpha = glassHighlight,
                            borderColor = glassBorderColor,
                            liquidBlur = glassLiquid.blur,
                            liquidLensHeight = glassLiquid.lensHeight,
                            liquidLensAmount = glassLiquid.lensAmount,
                        ) {
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                MoreSettings(
                                    onAboutClick = onAboutClick,
                                    onNavigateToSettings = onNavigateToSettings,
                                    onAccountInfoClick = onAccountInfoClick,
                                    isLoggedIn = isLoggedIn,
                                    userId = userId,
                                    onLoginClick = onLoginClick,
                                    onLogoutClick = onLogoutClick,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        MineScreenFooter(
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }

            AnimatedVisibility(
                visible = showCollapsedTopBar,
                enter = fadeIn(animationSpec = tween(durationMillis = 180)),
                exit = fadeOut(animationSpec = tween(durationMillis = 140)),
            ) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
                    sampleBackdrop = pageBackdrop,
                    backgroundAlpha = glassBg,
                    borderAlpha = glassBorder,
                    highlightAlpha = glassHighlight,
                    borderColor = glassBorderColor,
                    liquidBlur = glassLiquid.blur,
                    liquidLensHeight = glassLiquid.lensHeight,
                    liquidLensAmount = glassLiquid.lensAmount,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(id = R.string.mine),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MineSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 8.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.3.sp,
        color = if (isAppDarkTheme()) {
            Color.White.copy(alpha = 0.62f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
fun MineHeader(
    onLoginClick: () -> Unit = {},
    isLoggedIn: Boolean = false,
    nickname: String? = null,
    userId: Int = -1,
    isVip: Boolean = false,
    vipExpiresAt: String? = null,
    onVipEntryClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onAccountInfoClick: () -> Unit = {},
    onQrScanClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    unreadCount: Int = 0,
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
            .height(300.dp)
    ) {
        AppPageBackgroundImage(
            kind = AppBackgroundKind.MineHeader,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.34f),
                        0.34f to Color.Transparent,
                        0.66f to Color.Black.copy(alpha = 0.18f),
                        1f to Color.Black.copy(alpha = 0.78f),
                    ),
                ),
        )
        // 右上角：站内消息（带未读徽标）与「扫一扫」合成一块玻璃胶囊，避免两个孤立圆点
        val headerIsDark = isAppDarkTheme()
        val headerGlassTint = LiquidGlassDefaults.screenListCard
        GlassSurface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 10.dp, end = 16.dp),
            shape = RoundedCornerShape(50),
            backgroundAlpha = headerGlassTint.background(headerIsDark),
            borderAlpha = headerGlassTint.border(headerIsDark),
            highlightAlpha = headerGlassTint.highlight(headerIsDark),
            borderColor = if (headerIsDark) {
                SakuraPink.copy(alpha = LiquidGlassDefaults.appUpdateDialogDarkBorderSakuraAlpha)
            } else {
                MaterialTheme.colorScheme.outline
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onNotificationsClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_notification_message),
                            contentDescription = stringResource(id = R.string.notifications_title),
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 7.dp, end = 7.dp)
                                .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                                .clip(CircleShape)
                                .background(RoseRed),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(Color.White.copy(alpha = 0.22f))
                )

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onQrScanClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_qr_scan),
                        contentDescription = stringResource(id = R.string.qr_scan),
                        tint = Color.White,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }

        Text(
            text = stringResource(id = R.string.mine),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 20.dp, top = 14.dp),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 0.2.sp,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val vipRingBrush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFE082), Color(0xFFFFB300), Color(0xFFFFA000))
            )
            Box(
                modifier = Modifier.size(88.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoggedIn && isVip) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(width = 2.5.dp, brush = vipRingBrush, shape = CircleShape)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                        .shadow(
                            elevation = 10.dp,
                            spotColor = if (isVip) Color(0xFFFFB300).copy(alpha = 0.45f) else RoseRed.copy(alpha = 0.4f),
                            ambientColor = Color.Black.copy(alpha = 0.28f)
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

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isLoggedIn && !nickname.isNullOrBlank()) {
                        nickname
                    } else {
                        stringResource(id = R.string.not_logged_in)
                    },
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isLoggedIn && userId != -1) {
                            stringResource(id = R.string.mine_account_id_format, userId)
                        } else {
                            stringResource(id = R.string.mine_login_subtitle)
                        },
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isLoggedIn) {
                        Spacer(modifier = Modifier.width(8.dp))
                        VipPill(
                            isVip = isVip,
                            onClick = onVipEntryClick,
                            compact = true,
                        )
                    }
                }
            }

            Text(
                text = "›",
                fontSize = 24.sp,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
fun MineMenu(
    onRecentPlayClick: () -> Unit = {},
    onLocalMusicClick: () -> Unit = {},
    onVipCenterClick: () -> Unit = {},
    onUploadClick: () -> Unit = {},
    uploadCount: Int? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MenuItem(
            title = stringResource(id = R.string.recent_play),
            iconResId = R.drawable.recently_played,
            iconColor = RoseRed,
            onClick = onRecentPlayClick,
        )
        MineMenuDivider()
        MenuItem(
            title = stringResource(id = R.string.local_music),
            iconResId = R.drawable.music,
            iconColor = SkyBlue,
            onClick = onLocalMusicClick,
        )
        MineMenuDivider()
        MenuItem(
            title = stringResource(id = R.string.vip_menu_entry),
            iconResId = R.drawable.ic_vip_star,
            iconColor = Color(0xFFFFB300),
            onClick = onVipCenterClick,
        )
        MineMenuDivider()
        MenuItem(
            title = stringResource(id = R.string.my_music),
            iconResId = R.drawable.music,
            iconColor = Lilac,
            trailingText = uploadCount?.let { stringResource(R.string.songs_count_label, it) },
            onClick = onUploadClick,
        )
    }
}

@Composable
fun MenuItem(
    title: String,
    iconResId: Int,
    iconColor: Color = RoseRed,
    subtitle: String? = null,
    trailingText: String? = null,
    onClick: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(iconColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = iconResId),
                contentDescription = title,
                modifier = Modifier.size(23.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(iconColor),
            )
        }

        Spacer(modifier = Modifier.width(13.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (!trailingText.isNullOrBlank()) {
            Text(
                text = trailingText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        Text(
            text = "›",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
        )
    }
}

@Composable
private fun MineMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 65.dp, end = 14.dp),
        thickness = 0.5.dp,
        color = if (isAppDarkTheme()) {
            Color.White.copy(alpha = 0.10f)
        } else {
            Color.Black.copy(alpha = 0.08f)
        },
    )
}

@Composable
fun MoreSettings(
    onAboutClick: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onAccountInfoClick: () -> Unit = {},
    isLoggedIn: Boolean = false,
    userId: Int = -1,
    onLoginClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (isLoggedIn) {
            MenuItem(
                title = stringResource(id = R.string.account_info),
                iconResId = R.drawable.user,
                iconColor = SkyBlue,
                subtitle = if (userId != -1) {
                    stringResource(id = R.string.mine_account_id_format, userId)
                } else {
                    null
                },
                onClick = onAccountInfoClick,
            )
            MineMenuDivider()
        }
        MenuItem(
            title = stringResource(id = R.string.settings_title),
            iconResId = R.drawable.setting,
            iconColor = RoseRed,
            onClick = onNavigateToSettings,
        )
        MineMenuDivider()
        MenuItem(
            title = stringResource(id = R.string.about_us),
            iconResId = R.drawable.about,
            iconColor = StarYellow,
            onClick = onAboutClick,
        )
        MineMenuDivider()
        if (isLoggedIn) {
            MenuItem(
                title = stringResource(id = R.string.logout),
                iconResId = R.drawable.logout,
                iconColor = Color(0xFFFF6B6B),
                onClick = onLogoutClick,
            )
        } else {
            MenuItem(
                title = stringResource(id = R.string.login),
                iconResId = R.drawable.login,
                iconColor = Peach,
                onClick = onLoginClick,
            )
        }
    }
}

@Composable
private fun MineScreenFooter(
    isDarkTheme: Boolean = isAppDarkTheme(),
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
    isDarkTheme: Boolean = isAppDarkTheme(),
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
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
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
            ) {
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
