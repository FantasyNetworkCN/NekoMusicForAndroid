package com.neko.music.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.neko.music.R
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.theme.SakuraPink
import com.neko.music.ui.screens.LatestMusicCard
import com.neko.music.ui.screens.PlaylistCard
import com.neko.music.ui.screens.RankingMusicCard

/**
 * 首页搜索 + 推荐区顶栏。
 *
 * **须**与 [com.neko.music.MainActivity] 中 `NavHost` **同级**、在 Nav 绘制**之后**组合，并向 [GlassSurface] 传入
 * 与 `NavHost` 相同的 `layerBackdrop(liquidBackdrop)` 实例（与底栏一致）。若在 NavHost 内再套独立 pageBackdrop，
 * 嵌套录屏在部分机型上会导致折射/模糊无效。
 *
 * 本组件内 `CompositionLocalProvider(LocalLiquidLayerBackdrop provides …)` 仅便于子级读取；也可由调用方提供。
 */
@Composable
fun HomeLiquidHeroOverlay(
    state: HomeLiquidHeroState,
    liquidBackdrop: LayerBackdrop,
    onSearchClick: () -> Unit,
    onNavigateToPlaylist: (Int) -> Unit,
    onNavigateToRanking: () -> Unit,
    onNavigateToLatest: () -> Unit,
    onHeroHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDarkHome = colorScheme.background.luminance() < 0.5f

    CompositionLocalProvider(LocalLiquidLayerBackdrop provides liquidBackdrop) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .onSizeChanged { onHeroHeightChanged(it.height) }
    ) {
        val heroSearch = LiquidGlassDefaults.homeHeroSearchBar
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .height(48.dp),
            sampleBackdrop = liquidBackdrop,
            shape = RoundedCornerShape(20.dp),
            backgroundAlpha = heroSearch.tint.background(isDarkHome),
            borderAlpha = heroSearch.tint.border(isDarkHome),
            highlightAlpha = heroSearch.tint.highlight(isDarkHome),
            borderColor = if (isDarkHome) {
                SakuraPink.copy(alpha = LiquidGlassDefaults.homeHeroSearchBarDarkBorderSakuraAlpha)
            } else {
                colorScheme.outline
            },
            // 与 Kyant Glass Bottom Bar 教程同量级，便于看出 vibrancy + blur + lens
            liquidBlur = heroSearch.liquid.blur,
            liquidLensHeight = heroSearch.liquid.lensHeight,
            liquidLensAmount = heroSearch.liquid.lensAmount
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSearchClick
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(id = R.string.search),
                    tint = if (isDarkHome) {
                        Color.White.copy(alpha = 0.72f)
                    } else {
                        colorScheme.onSurface.copy(alpha = 0.55f)
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(id = R.string.search_music_artist_album),
                    fontSize = 15.sp,
                    color = if (isDarkHome) {
                        Color.White.copy(alpha = 0.62f)
                    } else {
                        colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Normal
                )
            }
        }

        val heroShortcut = LiquidGlassDefaults.homeHeroShortcutCard
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            sampleBackdrop = liquidBackdrop,
            shape = RoundedCornerShape(20.dp),
            backgroundAlpha = heroShortcut.tint.background(isDarkHome),
            borderAlpha = heroShortcut.tint.border(isDarkHome),
            highlightAlpha = heroShortcut.tint.highlight(isDarkHome),
            borderColor = if (isDarkHome) {
                SakuraPink.copy(alpha = LiquidGlassDefaults.homeHeroShortcutCardDarkBorderSakuraAlpha)
            } else {
                colorScheme.outline
            },
            liquidBlur = heroShortcut.liquid.blur,
            liquidLensHeight = heroShortcut.liquid.lensHeight,
            liquidLensAmount = heroShortcut.liquid.lensAmount
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                val sectionTitleColor =
                    if (isDarkHome) Color.White.copy(alpha = 0.98f) else colorScheme.onSurface
                val sectionTitleShadow =
                    if (isDarkHome) {
                        Shadow(
                            color = Color.Black.copy(alpha = 0.5f),
                            offset = Offset(0f, 1f),
                            blurRadius = 6f
                        )
                    } else {
                        Shadow(
                            color = Color.Black.copy(alpha = 0.12f),
                            offset = Offset(0f, 1f),
                            blurRadius = 3f
                        )
                    }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(20.dp)
                                .background(
                                    colorScheme.primary,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(id = R.string.recommended_playlists),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = sectionTitleColor,
                            letterSpacing = 0.3.sp,
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = sectionTitleShadow
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    when {
                        state.playlistsLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    color = if (isDarkHome) {
                                        Color.White.copy(alpha = 0.75f)
                                    } else {
                                        colorScheme.primary
                                    },
                                    strokeWidth = 2.5.dp
                                )
                            }
                        }
                        state.loadError -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.network_error_msg),
                                    fontSize = 16.sp,
                                    color = if (isDarkHome) {
                                        Color.White.copy(alpha = 0.88f)
                                    } else {
                                        colorScheme.onSurface
                                    },
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        else -> {
                            val gridItems = buildList {
                                if (state.rankingMusic.isNotEmpty()) {
                                    add(@androidx.compose.runtime.Composable {
                                        RankingMusicCard(
                                            musicList = state.rankingMusic,
                                            onClick = onNavigateToRanking,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    })
                                }
                                if (state.latestMusic.isNotEmpty()) {
                                    add(@androidx.compose.runtime.Composable {
                                        LatestMusicCard(
                                            musicList = state.latestMusic,
                                            onClick = onNavigateToLatest,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    })
                                }
                                state.recommendedPlaylists.take(2).forEach { playlist ->
                                    add(@androidx.compose.runtime.Composable {
                                        PlaylistCard(
                                            playlist = playlist,
                                            onClick = { onNavigateToPlaylist(playlist.id) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    })
                                }
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                for (i in gridItems.indices step 2) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            gridItems[i]()
                                        }
                                        if (i + 1 < gridItems.size) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                gridItems[i + 1]()
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}
