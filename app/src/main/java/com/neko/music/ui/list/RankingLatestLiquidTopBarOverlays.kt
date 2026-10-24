package com.neko.music.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.neko.music.R
import com.neko.music.data.model.Music
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.theme.isAppDarkTheme
import com.neko.music.ui.theme.LightRose
import com.neko.music.ui.theme.RoseRed

@Composable
fun RankingLiquidTopBarOverlay(
    state: RankingLiquidBarState,
    onBackClick: () -> Unit,
    onBarHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    sampleBackdrop: LayerBackdrop? = null,
) {
    ListScreenLiquidTopBarOverlay(
        titleRes = R.string.hot_music,
        musicList = state.musicList,
        onBackClick = onBackClick,
        onBarHeightChanged = onBarHeightChanged,
        modifier = modifier,
        sampleBackdrop = sampleBackdrop,
    )
}

@Composable
fun LatestLiquidTopBarOverlay(
    state: LatestLiquidBarState,
    onBackClick: () -> Unit,
    onBarHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    sampleBackdrop: LayerBackdrop? = null,
) {
    ListScreenLiquidTopBarOverlay(
        titleRes = R.string.latest_music,
        musicList = state.musicList,
        onBackClick = onBackClick,
        onBarHeightChanged = onBarHeightChanged,
        modifier = modifier,
        sampleBackdrop = sampleBackdrop,
    )
}

@Composable
private fun ListScreenLiquidTopBarOverlay(
    titleRes: Int,
    musicList: List<Music>,
    onBackClick: () -> Unit,
    onBarHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    sampleBackdrop: LayerBackdrop? = null,
) {
    val backdropForGlass = sampleBackdrop ?: LocalLiquidLayerBackdrop.current
    val scheme = MaterialTheme.colorScheme
    val isDark = isAppDarkTheme()
    val isRanking = titleRes == R.string.hot_music
    val accent = when {
        isRanking && isDark -> Color(0xFFFFD36A)
        isRanking -> Color(0xFFC47A00)
        isDark -> LightRose
        else -> RoseRed
    }
    val titleColor = if (isDark) Color.White.copy(alpha = 0.96f) else scheme.onSurface
    val countText = stringResource(id = R.string.songs_count_short, musicList.size)
    val density = LocalDensity.current
    val statusBarInsetPx = WindowInsets.statusBars.getTop(density)
    val barHeightPx = statusBarInsetPx + with(density) { 80.dp.roundToPx() }

    SideEffect {
        onBarHeightChanged(barHeightPx)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .statusBarsPadding(),
    ) {
        val topBarGlass = LiquidGlassDefaults.rankingLatestTopBar
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(56.dp),
            shape = RoundedCornerShape(24.dp),
            sampleBackdrop = backdropForGlass,
            backgroundAlpha = topBarGlass.tint.background(isDark),
            borderAlpha = topBarGlass.tint.border(isDark),
            highlightAlpha = topBarGlass.tint.highlight(isDark),
            borderColor = if (isDark) Color.White else scheme.outline,
            liquidBlur = topBarGlass.liquid.blur,
            liquidLensHeight = topBarGlass.liquid.lensHeight,
            liquidLensAmount = topBarGlass.liquid.lensAmount,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 5.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.back),
                        tint = titleColor.copy(alpha = 0.88f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = stringResource(id = titleRes),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (musicList.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.11f))
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = countText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accent,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
