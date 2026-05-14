package com.neko.music.ui.list

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neko.music.R
import com.neko.music.data.api.MusicApi
import com.neko.music.data.model.Music
import com.neko.music.service.MusicPlayerManager
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.util.UrlConfig
import kotlinx.coroutines.launch

@Composable
fun RankingLiquidTopBarOverlay(
    state: RankingLiquidBarState,
    onBackClick: () -> Unit,
    onBarHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    sampleBackdrop: LayerBackdrop? = null
) {
    ListScreenLiquidTopBarOverlay(
        titleRes = R.string.hot_music,
        musicList = state.musicList,
        logTag = "RankingLiquidTopBar",
        onBackClick = onBackClick,
        onBarHeightChanged = onBarHeightChanged,
        modifier = modifier,
        sampleBackdrop = sampleBackdrop
    )
}

@Composable
fun LatestLiquidTopBarOverlay(
    state: LatestLiquidBarState,
    onBackClick: () -> Unit,
    onBarHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    sampleBackdrop: LayerBackdrop? = null
) {
    ListScreenLiquidTopBarOverlay(
        titleRes = R.string.latest_music,
        musicList = state.musicList,
        logTag = "LatestLiquidTopBar",
        onBackClick = onBackClick,
        onBarHeightChanged = onBarHeightChanged,
        modifier = modifier,
        sampleBackdrop = sampleBackdrop
    )
}

@Composable
private fun ListScreenLiquidTopBarOverlay(
    titleRes: Int,
    musicList: List<Music>,
    logTag: String,
    onBackClick: () -> Unit,
    onBarHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    sampleBackdrop: LayerBackdrop? = null
) {
    val backdropForGlass = sampleBackdrop ?: LocalLiquidLayerBackdrop.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val musicApi = remember { MusicApi(context) }
    val playerManager = remember { MusicPlayerManager.getInstance(context) }
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val density = LocalDensity.current
    val extraTopGap = remember(density) {
        with(density) { (5f / this.density).dp }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = extraTopGap)
            .statusBarsPadding()
            .onSizeChanged { onBarHeightChanged(it.height) }
    ) {
        val topBarGlass = LiquidGlassDefaults.rankingLatestTopBar
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .height(52.dp),
            shape = RoundedCornerShape(22.dp),
            sampleBackdrop = backdropForGlass,
            backgroundAlpha = topBarGlass.tint.background(isDark),
            borderAlpha = topBarGlass.tint.border(isDark),
            highlightAlpha = topBarGlass.tint.highlight(isDark),
            borderColor = if (isDark) Color.White else scheme.outline,
            liquidBlur = topBarGlass.liquid.blur,
            liquidLensHeight = topBarGlass.liquid.lensHeight,
            liquidLensAmount = topBarGlass.liquid.lensAmount
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(start = 4.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(id = R.string.back),
                        tint = if (isDark) Color.White.copy(alpha = 0.92f) else scheme.onSurface
                    )
                }
                Text(
                    text = stringResource(id = titleRes),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White.copy(alpha = 0.96f) else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (musicList.isNotEmpty()) {
                    val playAllSource = remember { MutableInteractionSource() }
                    Text(
                        text = stringResource(id = R.string.play_all),
                        modifier = Modifier
                            .clickable(
                                interactionSource = playAllSource,
                                indication = null
                            ) {
                                Log.d(logTag, "播放全部: ${musicList.size}首")
                                scope.launch {
                                    try {
                                        val firstMusic = musicList[0]
                                        val url = musicApi.getMusicFileUrl(firstMusic)
                                        val fullCoverUrl = UrlConfig.getMusicCoverUrl(firstMusic.id)
                                        playerManager.playMusic(
                                            url,
                                            firstMusic.id,
                                            firstMusic.title,
                                            firstMusic.artist,
                                            firstMusic.coverFilePath ?: "",
                                            fullCoverUrl
                                        )
                                    } catch (e: Exception) {
                                        Log.e(logTag, "播放全部失败", e)
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = scheme.primary
                    )
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
    }
}
