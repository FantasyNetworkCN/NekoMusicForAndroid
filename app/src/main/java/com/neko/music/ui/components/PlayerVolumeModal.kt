package com.neko.music.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.neko.music.R
import com.neko.music.ui.icons.NekoSymbol
import com.neko.music.ui.icons.NekoSymbolIcon
import com.neko.music.ui.theme.SakuraPink
import com.neko.music.ui.theme.isAppDarkTheme
import kotlinx.coroutines.delay

private const val VOLUME_MODAL_EXIT_MS = 180L

/** 播放页音量模态框；内容与底部栏共用同一套实时音量状态。 */
@Composable
internal fun PlayerVolumeModal(onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val isDark = isAppDarkTheme()
    val title = stringResource(R.string.player_audio_settings)
    val closeLabel = stringResource(R.string.close)
    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val requestClose = {
        if (!closing) {
            closing = true
            shown = false
        }
    }
    LaunchedEffect(Unit) { shown = true }
    LaunchedEffect(shown, closing) {
        if (!shown && closing) {
            delay(VOLUME_MODAL_EXIT_MS)
            onDismiss()
        }
    }
    val scrimAlpha by animateFloatAsState(if (shown) 1f else 0f, tween(180), label = "volume_modal_scrim")
    BackHandler(onBack = requestClose)

    BoxWithConstraints(
        Modifier.fillMaxSize().zIndex(45f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.46f * scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = closeLabel,
                    onClick = requestClose,
                ),
        )
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.94f),
            exit = fadeOut(tween(VOLUME_MODAL_EXIT_MS.toInt())) +
                scaleOut(tween(VOLUME_MODAL_EXIT_MS.toInt()), targetScale = 0.94f),
            modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 520.dp).fillMaxWidth(),
        ) {
            val panel = LiquidGlassDefaults.playerShareBottomSheet
            val shape = RoundedCornerShape(24.dp)
            GlassSurface(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = maxHeight - 48.dp)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .semantics {
                        paneTitle = title
                        dismiss { requestClose(); true }
                    },
                shape = shape,
                backgroundAlpha = panel.tint.background(isDark),
                borderAlpha = panel.tint.border(isDark),
                highlightAlpha = panel.tint.highlight(isDark),
                borderColor = if (isDark) SakuraPink else scheme.outline,
                liquidBlur = panel.liquid.blur,
                liquidLensHeight = panel.liquid.lensHeight,
                liquidLensAmount = panel.liquid.lensAmount,
                material3Container = scheme.surfaceContainerHigh,
            ) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = scheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = requestClose) {
                            NekoSymbolIcon(
                                symbol = NekoSymbol.Xmark,
                                contentDescription = closeLabel,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            label = { Text(stringResource(R.string.player_volume_tab), color = scheme.onSurface) },
                            modifier = Modifier.weight(1f),
                        )
                        FilterChip(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            label = { Text(stringResource(R.string.player_eq_tab), color = scheme.onSurface) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (selectedTab == 0) {
                        PlayerVolumeSection(Modifier.fillMaxWidth())
                    } else {
                        EqualizerPanel(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
