package com.neko.music.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.neko.music.R
import com.neko.music.data.api.MusicApi
import com.neko.music.data.model.Music
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.isLiquidGlassEffectActive
import com.neko.music.ui.screens.LatestMusicCard
import com.neko.music.ui.screens.PlaylistCard
import com.neko.music.ui.screens.RankingMusicCard
import com.neko.music.ui.theme.SakuraPink
import com.neko.music.ui.theme.isAppDarkTheme
import com.neko.music.util.UrlConfig

/**
 * 首页搜索与推荐层。
 *
 * 与 [com.neko.music.MainActivity] 中的 NavHost 同级组合，采样主 liquidBackdrop，
 * 保证滚动的搜索、推荐与固定迷你播放器使用同一套液态玻璃折射。
 */
@Composable
fun HomeLiquidHeroOverlay(
    state: HomeLiquidHeroState,
    liquidBackdrop: LayerBackdrop,
    onSearchClick: () -> Unit,
    onRecognitionClick: () -> Unit,
    onNavigateToPlaylist: (Int) -> Unit,
    onNavigateToDailyRecommendations: () -> Unit,
    onNavigateToRanking: () -> Unit,
    onNavigateToLatest: () -> Unit,
    onHeroHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDarkHome = isAppDarkTheme()
    val titleColor = if (isDarkHome) Color.White else colorScheme.onSurface
    val secondaryColor = if (isDarkHome) {
        Color.White.copy(alpha = 0.68f)
    } else {
        colorScheme.onSurfaceVariant
    }
    val titleShadow = if (isDarkHome) {
        Shadow(Color.Black.copy(alpha = 0.5f), Offset(0f, 1f), 10f)
    } else {
        Shadow(Color.Black.copy(alpha = 0.08f), Offset(0f, 1f), 4f)
    }

    CompositionLocalProvider(LocalLiquidLayerBackdrop provides liquidBackdrop) {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .onSizeChanged { onHeroHeightChanged(it.height) },
            contentPadding = PaddingValues(bottom = 250.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "home_header") {
                HomeHeader(
                    isDarkHome = isDarkHome,
                    titleColor = titleColor,
                    secondaryColor = secondaryColor,
                    titleShadow = titleShadow,
                    sampleBackdrop = liquidBackdrop,
                    onSearchClick = onSearchClick,
                    onRecognitionClick = onRecognitionClick,
                )
            }

            if (state.dailyRecommendedMusic.isNotEmpty()) {
                item(key = "home_daily") {
                    HomeDailyFeature(
                        musicList = state.dailyRecommendedMusic,
                        isDarkHome = isDarkHome,
                        titleColor = titleColor,
                        secondaryColor = secondaryColor,
                        sampleBackdrop = liquidBackdrop,
                        onClick = onNavigateToDailyRecommendations,
                    )
                }
            }

            item(key = "home_recommended") {
                HomeRecommendedPanel(
                    state = state,
                    isDarkHome = isDarkHome,
                    titleColor = titleColor,
                    secondaryColor = secondaryColor,
                    sampleBackdrop = liquidBackdrop,
                    onNavigateToPlaylist = onNavigateToPlaylist,
                    onNavigateToRanking = onNavigateToRanking,
                    onNavigateToLatest = onNavigateToLatest,
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    isDarkHome: Boolean,
    titleColor: Color,
    secondaryColor: Color,
    titleShadow: Shadow,
    sampleBackdrop: LayerBackdrop,
    onSearchClick: () -> Unit,
    onRecognitionClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val glass = LiquidGlassDefaults.homeHeroSearchBar
    val useLiquidStyle = isLiquidGlassEffectActive(sampleBackdrop)
    val searchHeight = if (useLiquidStyle) 50.dp else 56.dp
    val borderColor = if (isDarkHome) {
        SakuraPink.copy(alpha = LiquidGlassDefaults.homeHeroSearchBarDarkBorderSakuraAlpha)
    } else {
        colorScheme.outline
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 8.dp, end = 20.dp),
    ) {
        Text(
            text = stringResource(id = R.string.welcome_back),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = secondaryColor,
            letterSpacing = 0.2.sp,
            style = androidx.compose.ui.text.TextStyle(shadow = titleShadow),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(id = R.string.home_listen_now),
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.ExtraBold,
            color = titleColor,
            letterSpacing = (-0.6).sp,
            style = androidx.compose.ui.text.TextStyle(shadow = titleShadow),
        )
        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassSurface(
                modifier = Modifier
                    .weight(1f)
                    .height(searchHeight),
                sampleBackdrop = sampleBackdrop,
                shape = RoundedCornerShape(18.dp),
                material3Shape = RoundedCornerShape(28.dp),
                material3Container = colorScheme.surfaceContainerHigh,
                backgroundAlpha = glass.tint.background(isDarkHome),
                borderAlpha = glass.tint.border(isDarkHome),
                highlightAlpha = glass.tint.highlight(isDarkHome),
                borderColor = borderColor,
                liquidBlur = glass.liquid.blur,
                liquidLensHeight = glass.liquid.lensHeight,
                liquidLensAmount = glass.liquid.lensAmount,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(searchHeight)
                        .padding(horizontal = 16.dp)
                        .then(
                            if (useLiquidStyle) {
                                Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onSearchClick,
                                )
                            } else {
                                Modifier.clickable(onClick = onSearchClick)
                            }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(id = R.string.search),
                        tint = secondaryColor,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(id = R.string.search_music_artist_album),
                        fontSize = 15.sp,
                        color = secondaryColor,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            GlassSurface(
                modifier = Modifier
                    .size(searchHeight)
                    .clickable(onClick = onRecognitionClick),
                sampleBackdrop = sampleBackdrop,
                shape = CircleShape,
                material3Container = colorScheme.surfaceContainerHigh,
                backgroundAlpha = glass.tint.background(isDarkHome),
                borderAlpha = glass.tint.border(isDarkHome),
                highlightAlpha = glass.tint.highlight(isDarkHome),
                borderColor = borderColor,
                liquidBlur = glass.liquid.blur,
                liquidLensHeight = glass.liquid.lensHeight,
                liquidLensAmount = glass.liquid.lensAmount,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_music_recognition),
                        contentDescription = stringResource(R.string.music_recognition),
                        tint = if (isDarkHome) SakuraPink else colorScheme.primary,
                        modifier = Modifier.size(23.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeDailyFeature(
    musicList: List<Music>,
    isDarkHome: Boolean,
    titleColor: Color,
    secondaryColor: Color,
    sampleBackdrop: LayerBackdrop,
    onClick: () -> Unit,
) {
    val music = musicList.firstOrNull() ?: return
    val colorScheme = MaterialTheme.colorScheme
    val glass = LiquidGlassDefaults.homeHeroShortcutCard
    val useLiquidStyle = isLiquidGlassEffectActive(sampleBackdrop)
    val borderColor = if (isDarkHome) {
        SakuraPink.copy(alpha = LiquidGlassDefaults.homeHeroShortcutCardDarkBorderSakuraAlpha)
    } else {
        colorScheme.outline
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && useLiquidStyle) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "home_daily_press",
    )

    val context = LocalContext.current
    val musicApi = remember { MusicApi(context) }
    var coverUrl by remember(music.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(music.id) {
        coverUrl = musicApi.getMusicCoverUrl(music)
    }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        sampleBackdrop = sampleBackdrop,
        shape = RoundedCornerShape(24.dp),
        material3Shape = RoundedCornerShape(16.dp),
        material3Container = colorScheme.surfaceContainerHigh,
        backgroundAlpha = glass.tint.background(isDarkHome),
        borderAlpha = glass.tint.border(isDarkHome),
        highlightAlpha = glass.tint.highlight(isDarkHome),
        borderColor = borderColor,
        liquidBlur = glass.liquid.blur,
        liquidLensHeight = glass.liquid.lensHeight,
        liquidLensAmount = glass.liquid.lensAmount,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                SakuraPink.copy(alpha = if (isDarkHome) 0.16f else 0.08f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(pressScale)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = if (useLiquidStyle) null else ripple(),
                        onClick = onClick,
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(
                            width = 0.8.dp,
                            color = Color.White.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(18.dp),
                        ),
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(coverUrl ?: UrlConfig.getDefaultAvatarUrl())
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(id = R.string.daily_recommendation_title),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.daily_recommendation_title),
                        fontSize = 18.sp,
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(id = R.string.daily_recommendation_desc),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = secondaryColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(SakuraPink.copy(alpha = if (isDarkHome) 0.20f else 0.12f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.songs_count_short, musicList.size),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDarkHome) Color.White.copy(alpha = 0.92f) else colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeRecommendedPanel(
    state: HomeLiquidHeroState,
    isDarkHome: Boolean,
    titleColor: Color,
    secondaryColor: Color,
    sampleBackdrop: LayerBackdrop,
    onNavigateToPlaylist: (Int) -> Unit,
    onNavigateToRanking: () -> Unit,
    onNavigateToLatest: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val glass = LiquidGlassDefaults.homeHeroShortcutCard
    val borderColor = if (isDarkHome) {
        SakuraPink.copy(alpha = LiquidGlassDefaults.homeHeroShortcutCardDarkBorderSakuraAlpha)
    } else {
        colorScheme.outline
    }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        sampleBackdrop = sampleBackdrop,
        shape = RoundedCornerShape(24.dp),
        material3Shape = RoundedCornerShape(16.dp),
        material3Container = colorScheme.surfaceContainer,
        backgroundAlpha = glass.tint.background(isDarkHome),
        borderAlpha = glass.tint.border(isDarkHome),
        highlightAlpha = glass.tint.highlight(isDarkHome),
        borderColor = borderColor,
        liquidBlur = glass.liquid.blur,
        liquidLensHeight = glass.liquid.lensHeight,
        liquidLensAmount = glass.liquid.lensAmount,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isDarkHome) SakuraPink else colorScheme.primary),
                )
                Spacer(modifier = Modifier.width(9.dp))
                Column {
                    Text(
                        text = stringResource(id = R.string.recommended_playlists),
                        fontSize = 21.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        letterSpacing = (-0.2).sp,
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = stringResource(id = R.string.home_recommended_hint),
                        fontSize = 12.sp,
                        color = secondaryColor,
                    )
                }
            }

            Spacer(modifier = Modifier.height(15.dp))

            when {
                state.playlistsLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(188.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = if (isDarkHome) Color.White.copy(alpha = 0.76f) else colorScheme.primary,
                            strokeWidth = 2.5.dp,
                        )
                    }
                }

                state.loadError -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(188.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(id = R.string.network_error_msg),
                            fontSize = 15.sp,
                            color = secondaryColor,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                else -> HomeRecommendationGrid(
                    state = state,
                    onNavigateToPlaylist = onNavigateToPlaylist,
                    onNavigateToRanking = onNavigateToRanking,
                    onNavigateToLatest = onNavigateToLatest,
                )
            }
        }
    }
}

/**
 * 推荐内容少于两项时并排铺满，多项时按双列纵向展开。
 * 首页是纵向信息流，不为了隐藏内容制造无意义的横向滚动。
 */
@Composable
private fun HomeRecommendationGrid(
    state: HomeLiquidHeroState,
    onNavigateToPlaylist: (Int) -> Unit,
    onNavigateToRanking: () -> Unit,
    onNavigateToLatest: () -> Unit,
) {
    val cards = buildList<@Composable () -> Unit> {
        if (state.rankingMusic.isNotEmpty()) {
            add {
                RankingMusicCard(
                    musicList = state.rankingMusic,
                    onClick = onNavigateToRanking,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (state.latestMusic.isNotEmpty()) {
            add {
                LatestMusicCard(
                    musicList = state.latestMusic,
                    onClick = onNavigateToLatest,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        state.recommendedPlaylists.take(7).forEach { playlist ->
            add {
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onNavigateToPlaylist(playlist.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        cards.chunked(2).forEach { rowCards ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                rowCards.forEach { card ->
                    Box(modifier = Modifier.weight(1f)) {
                        card()
                    }
                }
                if (rowCards.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
