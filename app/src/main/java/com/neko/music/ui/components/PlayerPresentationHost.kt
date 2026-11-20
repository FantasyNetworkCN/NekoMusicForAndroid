package com.neko.music.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.neko.music.data.model.Music
import com.neko.music.ui.screens.PlayerScreen
import kotlinx.coroutines.flow.first
import kotlin.math.min

private const val NekoPlayerRestCornerDp = 24f
private const val NekoPlayerPeakCornerDp = 34f
private const val NekoPlayerCornerRiseStart = 0.50f
private const val NekoPlayerCornerRiseEnd = 0.72f
private const val NekoPlayerCornerFlattenMillis = 140

/**
 * 播放页的共享元素目标层。
 *
 * 外壳由迷你播放器连续 morph 到全屏，封面通过独立 sharedElement 飞行；
 * 页面内容只在壳体成形后淡入，避免整页被压缩后提前暴露。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PlayerPresentationHost(
    music: Music,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    transitionTargetOpen: Boolean,
    closingIntent: Boolean,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    onDragCancel: () -> Unit,
    onBackClick: () -> Unit,
    onPlaylistClick: () -> Unit,
) {
    BackHandler(onBack = onBackClick)

    val expansionProgressState = animatedVisibilityScope.transition.animateFloat(
        transitionSpec = { nekoPlayerExpansionSpec() },
        label = "full_player_expansion_progress",
    ) { visibility ->
        if (visibility == EnterExitState.Visible) 1f else 0f
    }
    val expansionProgressProvider: () -> Float = remember(expansionProgressState) {
        { expansionProgressState.value }
    }

    var commentsPageReady by remember(music.id) { mutableStateOf(false) }
    var lyricsPageReady by remember(music.id) { mutableStateOf(false) }
    LaunchedEffect(music.id) {
        snapshotFlow { expansionProgressState.value >= 0.985f }.first { it }
        withFrameNanos { }
        commentsPageReady = true
        withFrameNanos { }
        lyricsPageReady = true
    }

    val flattenAnim = remember { Animatable(0f) }
    var shellSettledOpen by remember { mutableStateOf(false) }
    LaunchedEffect(
        animatedVisibilityScope.transition.currentState,
        animatedVisibilityScope.transition.targetState,
        transitionTargetOpen,
    ) {
        shellSettledOpen = transitionTargetOpen &&
            animatedVisibilityScope.transition.currentState == EnterExitState.Visible &&
            animatedVisibilityScope.transition.targetState == EnterExitState.Visible
    }
    LaunchedEffect(shellSettledOpen) {
        if (shellSettledOpen) {
            flattenAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(NekoPlayerCornerFlattenMillis),
            )
        } else {
            // 参考实现要求返回方向第一帧立即恢复屏幕圆角，不做渐变。
            flattenAnim.snapTo(0f)
        }
    }

    val closingIntentState = rememberUpdatedState(closingIntent)
    val density = LocalDensity.current
    val sinkPeakPx = with(density) { NekoPlayerSinkPeakDp.dp.toPx() }
    val shellBoundsTransform = remember(sinkPeakPx) {
        nekoPlayerSharedBoundsTransform(
            sinkPeakPx = sinkPeakPx,
            sinkEnabled = { closingIntentState.value },
        )
    }
    val artworkBoundsTransform = remember(sinkPeakPx) {
        nekoPlayerSharedBoundsTransform(
            sinkPeakPx = sinkPeakPx,
            sinkEnabled = { closingIntentState.value },
        )
    }
    val sharedShellState = with(sharedTransitionScope) {
        rememberSharedContentState(key = NekoPlayerShellKey)
    }
    val sharedShellModifier = with(sharedTransitionScope) {
        Modifier.sharedBounds(
            sharedContentState = sharedShellState,
            animatedVisibilityScope = animatedVisibilityScope,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            boundsTransform = shellBoundsTransform,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
    val sharedArtworkModifier = with(sharedTransitionScope) {
        Modifier.sharedElement(
            sharedContentState = rememberSharedContentState(
                key = NekoPlayerArtworkKey(music.id),
            ),
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = artworkBoundsTransform,
            renderInOverlayDuringTransition = true,
            zIndexInOverlay = 100f,
        )
    }
    val themeBackground = MaterialTheme.colorScheme.background

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(sharedShellModifier)
                .graphicsLayer {
                    val expansion = expansionProgressState.value.coerceIn(0f, 1f)
                    // 没有迷你栏可衔接时，仍提供完整的进入和退出动画。
                    val fallback = !sharedShellState.isMatchFound
                    translationY = if (fallback) size.height * .24f * (1f - expansion) else 0f
                    scaleX = if (fallback) .94f + .06f * expansion else 1f
                    scaleY = scaleX
                    alpha = if (fallback) smoothStep(expansion, 0f, .24f) else 1f
                    val rise = smoothStep(
                        expansion,
                        NekoPlayerCornerRiseStart,
                        NekoPlayerCornerRiseEnd,
                    )
                    val radiusDp = NekoPlayerRestCornerDp +
                        (NekoPlayerPeakCornerDp - NekoPlayerRestCornerDp) * rise
                    val halfHeightDp = size.height.toDp().value / 2f
                    val flatten = if (expansion >= 0.999f) flattenAnim.value else 0f
                    val resolvedRadiusDp = min(radiusDp, halfHeightDp) * (1f - flatten)
                    shape = RoundedCornerShape(resolvedRadiusDp.dp)
                    clip = true
                }
                .background(themeBackground)
                .drawBehind {
                    if (closingIntentState.value) {
                        val expansion = expansionProgressState.value.coerceIn(0f, 1f)
                        drawRect(
                            color = Color.Black,
                            alpha = smoothStep(expansion, 0.02f, 0.34f),
                        )
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val expansion = expansionProgressState.value.coerceIn(0f, 1f)
                        val reveal = smoothStep(expansion, 0.04f, 0.52f)
                        alpha = reveal
                        translationY = 6.dp.toPx() * (1f - reveal)
                    },
            ) {
                PlayerScreen(
                    music = music,
                    onBackClick = onBackClick,
                    onPlaylistClick = onPlaylistClick,
                    onDismissDragStart = onDragStart,
                    onDismissDrag = onDrag,
                    onDismissDragEnd = onDragEnd,
                    onDismissDragCancel = onDragCancel,
                    commentsPageReady = commentsPageReady,
                    lyricsPageReady = lyricsPageReady,
                    artworkSharedModifier = sharedArtworkModifier,
                    artworkExpansionProgress = expansionProgressProvider,
                )
            }
        }
    }
}

private fun smoothStep(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    val progress = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return progress * progress * (3f - 2f * progress)
}
