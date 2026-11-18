package com.neko.music.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import com.neko.music.R
import com.neko.music.ui.theme.isAppDarkTheme
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

private val NekoMiniHeight = 48.dp
private val NekoMiniArtworkSize = 40.dp
private val NekoMiniControlSize = 30.dp
private val NekoMiniControlIconSize = 18.dp
private const val NekoMiniTitleFontDp = 13.5f
private const val NekoMiniArtistFontDp = 12f
private const val NekoMiniLineHeightDp = 15f

private val MiniMorphShrinkEase = CubicBezierEasing(0.28f, 0.06f, 0.55f, 0.86f)
private val MiniMorphRebound = spring<Float>(
    dampingRatio = 0.55f,
    stiffness = 520f,
    visibilityThreshold = 1e-3f,
)
private val MiniMorphRapidRecover = spring<Float>(
    dampingRatio = 0.78f,
    stiffness = 900f,
    visibilityThreshold = 1e-3f,
)

/**
 * MeloX 迷你播放器的 Android 适配版。
 *
 * 保留对方的 48dp 胶囊高度、40dp 封面、13.5/12dp 两行文字、30dp 控件热区、
 * 18dp 字形，以及播放/暂停收缩换字、下一首推进和按钮按压时的整栏回弹。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MeloXMiniPlayer(
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    songTitle: String = "",
    artist: String = "",
    coverUrl: String? = null,
    onPlayPauseClick: () -> Unit = {},
    onNextClick: () -> Unit = {},
    onPlayerClick: () -> Unit = {},
    onPlayerDragStart: () -> Unit = {},
    onPlayerDrag: (Float) -> Unit = {},
    onPlayerDragEnd: (Float) -> Unit = {},
    onPlayerDragCancel: () -> Unit = {},
    sampleBackdrop: Backdrop? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedArtworkMediaId: Int? = null,
    closingIntent: Boolean = false,
) {
    val density = LocalDensity.current
    val isDarkTheme = isAppDarkTheme()
    val glass = LiquidGlassDefaults.miniPlayerBar
    val liquid = LiquidGlassDefaults.liquidSoft
    val onSurface = MaterialTheme.colorScheme.onSurface
    val noFontPadding = LocalTextStyle.current.merge(
        TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
    )
    val titleSize = with(density) { NekoMiniTitleFontDp.dp.toSp() }
    val artistSize = with(density) { NekoMiniArtistFontDp.dp.toSp() }
    val lineHeight = with(density) { NekoMiniLineHeightDp.dp.toSp() }
    val morph = rememberMeloXMiniPlayPauseMorph(isPlaying)
    val nextPulse = rememberMeloXMiniNextPulse()
    var controlPressed by remember { mutableStateOf(false) }
    val expansionProgressState: State<Float> = if (animatedVisibilityScope != null) {
        animatedVisibilityScope.transition.animateFloat(
            transitionSpec = { nekoPlayerExpansionSpec() },
            label = "mini_player_expansion_progress",
        ) { visibility ->
            if (visibility == EnterExitState.Visible) 0f else 1f
        }
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val surfaceAlphaProvider: () -> Float = remember(expansionProgressState) {
        {
            1f - miniSmoothStep(expansionProgressState.value, 0.02f, 0.48f)
        }
    }
    val closingIntentState = rememberUpdatedState(closingIntent)
    val sinkPeakPx = with(density) { NekoPlayerSinkPeakDp.dp.toPx() }
    val sharedShellBoundsTransform = remember(sinkPeakPx) {
        nekoPlayerSharedBoundsTransform(
            sinkPeakPx = sinkPeakPx,
            sinkEnabled = { closingIntentState.value },
        )
    }
    val sharedArtworkBoundsTransform = remember(sinkPeakPx) {
        nekoPlayerSharedBoundsTransform(
            sinkPeakPx = sinkPeakPx,
            sinkEnabled = { closingIntentState.value },
        )
    }
    val sharedShellModifier =
        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = NekoPlayerShellKey),
                    animatedVisibilityScope = animatedVisibilityScope,
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                    boundsTransform = sharedShellBoundsTransform,
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                    zIndexInOverlay = 1f,
                )
            }
        } else {
            Modifier
        }
    val sharedArtworkModifier =
        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedElement(
                    sharedContentState = rememberSharedContentState(
                        key = NekoPlayerArtworkKey(sharedArtworkMediaId),
                    ),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = sharedArtworkBoundsTransform,
                    renderInOverlayDuringTransition = true,
                    zIndexInOverlay = 99f,
                )
            }
        } else {
            Modifier
        }
    val barScale by animateFloatAsState(
        targetValue = if (controlPressed) 1.032f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 300f,
            visibilityThreshold = 1e-3f,
        ),
        label = "melox_mini_bar_press",
    )
    val swipeUpModifier = Modifier.pointerInput(Unit) {
        var started = false
        val velocityTracker = VelocityTracker()
        detectVerticalDragGestures(
            onDragStart = {
                started = false
                velocityTracker.resetTracking()
            },
            onVerticalDrag = { change, dragAmount ->
                if (!started && dragAmount < 0f) {
                    started = true
                    onPlayerDragStart()
                }
                if (started) {
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    onPlayerDrag(dragAmount)
                    change.consume()
                }
            },
            onDragEnd = {
                if (started) onPlayerDragEnd(velocityTracker.calculateVelocity().y)
                started = false
            },
            onDragCancel = {
                if (started) onPlayerDragCancel()
                started = false
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(NekoMiniHeight)
            .then(sharedShellModifier),
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(NekoMiniHeight)
                .graphicsLayer {
                    alpha = surfaceAlphaProvider()
                    scaleX = barScale
                    scaleY = barScale
                }
                .then(swipeUpModifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPlayerClick,
                ),
            shape = RoundedCornerShape(24.dp),
            material3Shape = RoundedCornerShape(24.dp),
            sampleBackdrop = sampleBackdrop,
            material3Container = MaterialTheme.colorScheme.surfaceContainerHigh,
            material3Elevation = 2.dp,
            backgroundAlpha = glass.backgroundAlpha,
            borderAlpha = glass.borderAlpha,
            highlightAlpha = glass.highlightAlpha,
            liquidBlur = liquid.blur,
            liquidLensHeight = liquid.lensHeight,
            liquidLensAmount = liquid.lensAmount,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = stringResource(R.string.content_description_cover),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(NekoMiniArtworkSize)
                        .then(sharedArtworkModifier)
                        .clip(RoundedCornerShape(6.dp)),
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .wrapContentHeight(Alignment.CenterVertically),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = songTitle.ifBlank { stringResource(R.string.player_no_music) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = titleSize,
                        lineHeight = lineHeight,
                        softWrap = false,
                        fontWeight = FontWeight.SemiBold,
                        color = onSurface,
                        style = noFontPadding,
                    )
                    Text(
                        text = artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = artistSize,
                        lineHeight = lineHeight,
                        softWrap = false,
                        color = onSurface.copy(alpha = 0.64f),
                        modifier = Modifier.height(15.dp),
                        style = noFontPadding,
                    )
                }

                MiniDancingBars(
                    isPlaying = isPlaying,
                    color = onSurface.copy(alpha = 0.72f),
                )

                Row(
                    modifier = Modifier.height(NekoMiniControlSize),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    MiniTransportButton(
                        onClick = {
                            morph.startToggle()
                            onPlayPauseClick()
                        },
                        onPressChanged = { controlPressed = it },
                        modifier = Modifier.graphicsLayer { translationX = (-4).dp.toPx() },
                    ) {
                        Box(
                            modifier = Modifier.drawWithContent {
                                val s = morph.scale.value
                                if (s == 1f) drawContent() else scale(s) { this@drawWithContent.drawContent() }
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (morph.displayIsPlaying) R.drawable.pause else R.drawable.play,
                                ),
                                contentDescription = stringResource(
                                    if (isPlaying) R.string.content_description_pause
                                    else R.string.content_description_play,
                                ),
                                tint = onSurface.copy(alpha = 0.94f),
                                modifier = Modifier.size(NekoMiniControlIconSize),
                            )
                        }
                    }
                    MiniTransportButton(
                        onClick = {
                            nextPulse.trigger()
                            onNextClick()
                        },
                        onPressChanged = { controlPressed = it },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.next_song),
                            contentDescription = stringResource(R.string.desktop_lyric_next),
                            tint = onSurface.copy(alpha = 0.94f),
                            modifier = Modifier
                                .size(NekoMiniControlIconSize)
                                .graphicsLayer {
                                    val pulse = nextPulse.scroll.value
                                    translationX = 3.dp.toPx() * (1f - pulse)
                                    val s = 1f + 0.08f * pulse
                                    scaleX = s
                                    scaleY = s
                                },
                        )
                    }
                }
            }
        }
    }
}

private fun miniSmoothStep(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    val fraction = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return fraction * fraction * (3f - 2f * fraction)
}

@Composable
private fun MiniTransportButton(
    onClick: () -> Unit,
    onPressChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.82f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 500f,
            visibilityThreshold = 1e-3f,
        ),
        label = "melox_mini_control_press",
    )
    LaunchedEffect(pressed) { onPressChanged(pressed) }
    Box(
        modifier = modifier
            .size(NekoMiniControlSize)
            .clip(CircleShape)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private class MiniPlayPauseMorphController {
    val scale = Animatable(1f)
    var displayIsPlaying by mutableStateOf(false)
        private set
    private var fromIsPlaying = false
    private var targetIsPlaying = false
    private var swapped = true
    private var primed = false
    private var rapid = false
    private val lastClickAt = longArrayOf(Long.MIN_VALUE)
    var token by mutableIntStateOf(0)
        private set

    fun onExternalIsPlaying(isPlaying: Boolean) {
        if (!primed) {
            primed = true
            targetIsPlaying = isPlaying
            fromIsPlaying = isPlaying
            displayIsPlaying = isPlaying
            return
        }
        if (isPlaying == targetIsPlaying) return
        startMorph(isPlaying)
    }

    fun startToggle() = startMorph(!targetIsPlaying)

    private fun startMorph(to: Boolean) {
        val now = SystemClock.uptimeMillis()
        rapid = lastClickAt[0] != Long.MIN_VALUE && now - lastClickAt[0] < 300L
        lastClickAt[0] = now
        fromIsPlaying = if (swapped) targetIsPlaying else fromIsPlaying
        swapped = rapid
        targetIsPlaying = to
        displayIsPlaying = if (swapped) targetIsPlaying else fromIsPlaying
        token++
    }

    suspend fun run(ticket: Int) {
        if (ticket == 0) return
        try {
            if (rapid) {
                scale.animateTo(
                    targetValue = 0.80f,
                    animationSpec = tween(60, easing = MiniMorphShrinkEase),
                )
                swapped = true
                displayIsPlaying = targetIsPlaying
                scale.animateTo(1f, MiniMorphRapidRecover)
            } else {
                scale.animateTo(
                    targetValue = 0.22f,
                    animationSpec = tween(115, easing = MiniMorphShrinkEase),
                ) {
                    if (!swapped && value <= 0.50f) swapped = true
                    if (swapped && displayIsPlaying != targetIsPlaying) {
                        displayIsPlaying = targetIsPlaying
                    }
                }
                swapped = true
                displayIsPlaying = targetIsPlaying
                scale.animateTo(1f, MiniMorphRebound)
            }
        } catch (_: Throwable) {
            swapped = true
            displayIsPlaying = targetIsPlaying
        }
    }
}

@Composable
private fun rememberMeloXMiniPlayPauseMorph(isPlaying: Boolean): MiniPlayPauseMorphController {
    val controller = remember { MiniPlayPauseMorphController() }
    LaunchedEffect(controller, isPlaying) { controller.onExternalIsPlaying(isPlaying) }
    LaunchedEffect(controller, controller.token) { controller.run(controller.token) }
    return controller
}

private class MiniNextPulse internal constructor(
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    val scroll = Animatable(0f)

    fun trigger() {
        scope.launch {
            runCatching {
                scroll.snapTo(0f)
                scroll.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.55f,
                        stiffness = 300f,
                        visibilityThreshold = 1e-3f,
                    ),
                )
                scroll.snapTo(0f)
            }
        }
    }
}

@Composable
private fun rememberMeloXMiniNextPulse(): MiniNextPulse {
    val scope = rememberCoroutineScope()
    return remember(scope) { MiniNextPulse(scope) }
}

@Composable
private fun MiniDancingBars(
    isPlaying: Boolean,
    color: androidx.compose.ui.graphics.Color,
) {
    val transition = rememberInfiniteTransition(label = "melox_mini_bars")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "melox_mini_bars_phase",
    )
    Canvas(
        Modifier
            .width(15.dp)
            .height(18.dp),
    ) {
        val gap = size.width * 0.12f
        val barWidth = (size.width - gap * 3f) / 4f
        val p = if (isPlaying) phase.value else 0f
        val heights = floatArrayOf(
            0.42f + 0.20f * sin((p * 2f * PI + 0.2).toFloat()),
            0.68f + 0.20f * sin((p * 2f * PI + 1.4).toFloat()),
            0.36f + 0.18f * sin((p * 2f * PI + 2.6).toFloat()),
            0.58f + 0.22f * sin((p * 2f * PI + 3.8).toFloat()),
        )
        heights.forEachIndexed { index, heightFraction ->
            val barHeight = size.height * heightFraction.coerceIn(0.12f, 1f)
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}
