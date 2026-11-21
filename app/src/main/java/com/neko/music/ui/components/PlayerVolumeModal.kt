package com.neko.music.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.neko.music.R
import com.neko.music.ui.icons.NekoSymbol
import com.neko.music.ui.icons.NekoSymbolIcon
import com.neko.music.ui.theme.SakuraPink
import com.neko.music.ui.theme.isAppDarkTheme
import com.kyant.shapes.Capsule
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
                    AudioSettingsTabStrip(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                    )
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            val direction = if (targetState > initialState) 1 else -1
                            (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 5 * direction }) togetherWith
                                (fadeOut(tween(120)) + slideOutHorizontally(tween(180)) { -it / 5 * direction })
                        },
                        label = "audioSettingsTabContent",
                    ) { tab ->
                        if (tab == 0) {
                            PlayerVolumeSection(Modifier.fillMaxWidth())
                        } else {
                            EqualizerPanel(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioSettingsTabStrip(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = isAppDarkTheme()
    val backdrop = LocalLiquidLayerBackdrop.current
    val haptics = LocalHapticFeedback.current
    val labels = listOf(
        stringResource(R.string.player_volume_tab),
        stringResource(R.string.player_eq_tab),
    )
    val select: (Int) -> Unit = { index ->
        if (selectedTab != index) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onTabSelected(index)
        }
    }
    val rowModifier = modifier.height(56.dp)
    if (backdrop != null && isLiquidGlassEffectActive(backdrop)) {
        val baseScale = LocalLiquidGlassUiScale.current
        val amplifiedScale = baseScale.copy(
            tintStrength = (baseScale.tintStrength * 1.25f).coerceAtMost(LiquidGlassUiScale.StrengthMax),
            blurStrength = (baseScale.blurStrength * 1.45f).coerceAtMost(LiquidGlassUiScale.StrengthMax),
            lensHeightStrength = (baseScale.lensHeightStrength * 1.65f).coerceAtMost(LiquidGlassUiScale.StrengthMax),
            lensAmountStrength = (baseScale.lensAmountStrength * 1.65f).coerceAtMost(LiquidGlassUiScale.StrengthMax),
        )
        CompositionLocalProvider(LocalLiquidGlassUiScale provides amplifiedScale) {
            LiquidTabStrip(
                selectedTabIndex = selectedTab,
                onTabSelected = select,
                backdrop = backdrop,
                tabsCount = labels.size,
                isDark = isDark,
                accentColor = scheme.primary,
                barHeight = 56.dp,
                modifier = rowModifier,
            ) {
                labels.forEachIndexed { index, label ->
                    AudioSettingsTab(
                        label = label,
                        selected = selectedTab == index,
                        isDark = isDark,
                        liquidStyle = true,
                        onClick = { select(index) },
                    )
                }
            }
        }
    } else {
        val tint = LiquidGlassDefaults.searchTopTypeRow
        GlassSurface(
            modifier = rowModifier,
            shape = RoundedCornerShape(24.dp),
            sampleBackdrop = backdrop,
            backgroundAlpha = tint.tint.background(isDark),
            borderAlpha = tint.tint.border(isDark),
            highlightAlpha = tint.tint.highlight(isDark),
            borderColor = if (isDark) Color.White else scheme.outline,
            liquidBlur = tint.liquid.blur,
            liquidLensHeight = tint.liquid.lensHeight,
            liquidLensAmount = tint.liquid.lensAmount,
            material3Container = scheme.surfaceContainerHigh,
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
                val segmentWidth = maxWidth / labels.size
                val indicatorOffset by animateDpAsState(
                    targetValue = segmentWidth * selectedTab,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "audioSettingsTabIndicator",
                )
                Box(
                    Modifier.offset(x = indicatorOffset).width(segmentWidth).fillMaxHeight()
                        .clip(CircleShape).background(scheme.secondaryContainer),
                )
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    labels.forEachIndexed { index, label ->
                        AudioSettingsTab(
                            label = label,
                            selected = selectedTab == index,
                            isDark = isDark,
                            liquidStyle = false,
                            onClick = { select(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.AudioSettingsTab(
    label: String,
    selected: Boolean,
    isDark: Boolean,
    liquidStyle: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val textColor by animateColorAsState(
        targetValue = when {
            !liquidStyle && selected -> scheme.onSecondaryContainer
            !liquidStyle -> scheme.onSurfaceVariant
            selected && isDark -> Color.White.copy(alpha = 0.96f)
            selected -> Color.Black.copy(alpha = 0.86f)
            isDark -> Color(0xFFB8B8D1).copy(alpha = 0.76f)
            else -> scheme.onSurfaceVariant.copy(alpha = 0.76f)
        },
        animationSpec = tween(180),
        label = "audioSettingsTabText",
    )
    val tabScale = LocalLiquidTabScale.current
    Box(
        modifier = Modifier.weight(1f).fillMaxHeight().clip(Capsule())
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.graphicsLayer {
                val scale = tabScale()
                scaleX = scale
                scaleY = scale
            },
        )
    }
}
