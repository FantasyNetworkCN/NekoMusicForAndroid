package com.neko.music.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.neko.music.ui.theme.RoseRed
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

private val TrackHeight = 6.dp
private val ThumbWidth = 40.dp
private val ThumbHeight = 24.dp

/**
 * 对齐 Kyant [Glass Slider](https://kyant.gitbook.io/backdrop/tutorials/glass-slider)：
 * 轨道单独录制到独立 LayerBackdrop，拇指再通过 combined backdrop 同时折射页面与已播放轨道。
 *
 * 整条轨道都是连续拖动区域；按压力度会驱动 blur、lens、高光和回弹形变。
 * 仅在真液态玻璃路径调用，Material 3 回退仍使用标准 Slider。
 */
@Composable
fun LiquidGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = RoseRed,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val trackColor = if (isDark) {
        Color(0xFF787880).copy(alpha = 0.36f)
    } else {
        Color(0xFF787878).copy(alpha = 0.2f)
    }

    val trackBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val externalValue = value.coerceIn(valueRange)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    var displayedValue by remember { mutableFloatStateOf(externalValue) }
    var isDragging by remember { mutableStateOf(false) }
    val animationScope = rememberCoroutineScope()
    val dragAnimation = remember(animationScope, valueRange) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = displayedValue,
            valueRange = valueRange,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {},
            onDrag = { _, _ -> },
        )
    }

    LaunchedEffect(dragAnimation) {
        snapshotFlow { displayedValue }
            .collectLatest { nextValue ->
                if (dragAnimation.targetValue != nextValue) {
                    dragAnimation.updateValue(nextValue)
                }
            }
    }

    LaunchedEffect(externalValue) {
        if (!isDragging) {
            displayedValue = externalValue
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.pointerInput(isLtr, valueRange) {
                        fun valueAt(positionX: Float): Float {
                            val fraction = (positionX / size.width.coerceAtLeast(1))
                                .fastCoerceIn(0f, 1f)
                            val span = valueRange.endInclusive - valueRange.start
                            return if (isLtr) {
                                valueRange.start + span * fraction
                            } else {
                                valueRange.endInclusive - span * fraction
                            }
                        }

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var horizontalDrag = false

                            fun commit(positionX: Float) {
                                displayedValue = valueAt(positionX)
                                latestOnValueChange(displayedValue)
                            }

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes
                                        .fastFirstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        if (!horizontalDrag) {
                                            isDragging = true
                                            dragAnimation.press()
                                        }
                                        commit(change.position.x)
                                        change.consume()
                                        break
                                    }

                                    if (horizontalDrag) {
                                        commit(change.position.x)
                                        change.consume()
                                    } else {
                                        val delta = change.position - down.position
                                        if (delta.getDistance() > viewConfiguration.touchSlop) {
                                            if (abs(delta.x) > abs(delta.y)) {
                                                horizontalDrag = true
                                                isDragging = true
                                                dragAnimation.press()
                                                commit(change.position.x)
                                                change.consume()
                                            } else {
                                                break
                                            }
                                        }
                                    }
                                }
                            } finally {
                                if (isDragging) {
                                    isDragging = false
                                    dragAnimation.release()
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidth = constraints.maxWidth

        Box(
            modifier = Modifier
                .layerBackdrop(trackBackdrop)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = externalValue,
                        range = valueRange,
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .clip(Capsule())
                    .background(trackColor)
                    .height(TrackHeight)
                    .fillMaxWidth(),
            )

            Box(
                modifier = Modifier
                    .height(TrackHeight)
                    .fillMaxWidth()
                    .drawBehind {
                        val width = size.width * dragAnimation.progress
                        if (width > 0f) {
                            drawRoundRect(
                                color = if (enabled) {
                                    accentColor
                                } else {
                                    accentColor.copy(alpha = 0.38f)
                                },
                                size = Size(width, size.height),
                                cornerRadius = CornerRadius(minOf(width, size.height) / 2f),
                            )
                        }
                    },
            )
        }

        val trackSampleTransform: DrawScope.(DrawScope.() -> Unit) -> Unit =
            remember(dragAnimation) {
                { drawBackdrop ->
                    val progress = dragAnimation.pressProgress
                    val scaleX = lerp(2f / 3f, 1f, progress)
                    val scaleY = lerp(0f, 1f, progress)
                    scale(scaleX, scaleY) {
                        drawBackdrop()
                    }
                }
            }
        val sampledTrack = rememberBackdrop(trackBackdrop, trackSampleTransform)
        val thumbBackdrop = rememberCombinedBackdrop(backdrop, sampledTrack)
        val thumbModifier = remember(thumbBackdrop, density, dragAnimation) {
            Modifier.drawBackdrop(
                backdrop = thumbBackdrop,
                shape = { Capsule() },
                effects = {
                    val progress = dragAnimation.pressProgress
                    blur(with(density) { 8.dp.toPx() * (1f - progress) })
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        lens(
                            refractionHeight = with(density) { 10.dp.toPx() * progress },
                            refractionAmount = with(density) { 14.dp.toPx() * progress },
                            chromaticAberration = true,
                        )
                    }
                },
                highlight = {
                    val progress = dragAnimation.pressProgress
                    Highlight.Ambient.copy(
                        width = Highlight.Ambient.width / 1.5f,
                        blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                        alpha = progress,
                    )
                },
                shadow = {
                    Shadow(
                        radius = 4.dp,
                        color = Color.Black.copy(alpha = 0.05f),
                    )
                },
                innerShadow = {
                    val progress = dragAnimation.pressProgress
                    InnerShadow(
                        radius = 4.dp * progress,
                        alpha = progress,
                    )
                },
                layerBlock = {
                    scaleX = dragAnimation.scaleX
                    scaleY = dragAnimation.scaleY
                    val velocity = dragAnimation.velocity / 10f
                    scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                    scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                },
                onDrawSurface = {
                    val progress = dragAnimation.pressProgress
                    drawRect(Color.White.copy(alpha = 1f - progress))
                },
            )
        }

        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = (
                        -size.width / 2f + trackWidth * dragAnimation.progress
                        ).fastCoerceIn(
                        minimumValue = -size.width / 4f,
                        maximumValue = trackWidth - size.width * 3f / 4f,
                    ) * if (isLtr) 1f else -1f
                }
                .then(thumbModifier)
                .size(ThumbWidth, ThumbHeight),
        )
    }
}
