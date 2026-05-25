package com.neko.music.ui.components

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
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
import com.neko.music.ui.theme.RoseRed
import kotlinx.coroutines.flow.collectLatest

private val TrackWidth = 64.dp
private val TrackHeight = 28.dp
private val ThumbWidth = 40.dp
private val ThumbHeight = 24.dp

/**
 * 对齐 Kyant [LiquidToggle](https://github.com/Kyant0/AndroidLiquidGlass/blob/master/catalog/src/main/java/com/kyant/backdrop/catalog/components/LiquidToggle.kt)：
 * 轨道 layerBackdrop + 拇指 combined drawBackdrop，点击/拖动切换。
 */
@Composable
fun LiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isDarkChrome: Boolean? = null,
    enabled: Boolean = true,
    sampleBackdrop: LayerBackdrop? = null,
) {
    val backdrop = sampleBackdrop ?: LocalLiquidLayerBackdrop.current
    val hardwareLiquid = LocalLiquidGlassHardwareEffectsEnabled.current

    if (!enabled || !hardwareLiquid || backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val isDark = isDarkChrome ?: isSystemInDarkTheme()
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = modifier,
            colors = SwitchDefaults.colors(
                checkedThumbColor = RoseRed,
                checkedTrackColor = RoseRed.copy(alpha = 0.5f),
                uncheckedThumbColor = if (isDark) Color(0xFFB8B8D1) else Color.Gray,
                uncheckedTrackColor = if (isDark) Color(0xFFB8B8D1).copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.5f)
            )
        )
        return
    }

    LiquidGlassSwitchLiquid(
        checked = checked,
        onCheckedChange = onCheckedChange,
        backdrop = backdrop,
        isDarkChrome = isDarkChrome,
        modifier = modifier
    )
}

@Composable
private fun LiquidGlassSwitchLiquid(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: Backdrop,
    isDarkChrome: Boolean?,
    modifier: Modifier = Modifier,
) {
    val isDark = isDarkChrome
        ?: (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val accentColor = RoseRed
    val trackColorOff =
        if (isDark) Color(0xFF787880).copy(alpha = 0.36f)
        else Color(0xFF787878).copy(alpha = 0.2f)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }

    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {
                if (didDrag) {
                    fraction = if (targetValue >= 0.5f) 1f else 0f
                    onCheckedChange(fraction == 1f)
                    didDrag = false
                } else {
                    fraction = if (checked) 0f else 1f
                    onCheckedChange(fraction == 1f)
                }
            },
            onDrag = { _, dragAmount ->
                if (!didDrag && dragAmount.x != 0f) didDrag = true
                val delta = dragAmount.x / dragWidth
                fraction =
                    if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                    else (fraction - delta).fastCoerceIn(0f, 1f)
            }
        )
    }

    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }
            .collectLatest { dampedDragAnimation.updateValue(it) }
    }

    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (target != fraction) {
            fraction = target
            dampedDragAnimation.animateToValue(target)
        }
    }

    val trackBackdrop = rememberLayerBackdrop()
    val trackShape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50)
    val thumbShape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier.semantics { role = Role.Switch },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(trackShape)
                .drawBehind {
                    val f = dampedDragAnimation.value
                    drawRect(lerp(trackColorOff, accentColor, f))
                }
                .size(TrackWidth, TrackHeight)
        )

        Box(
            Modifier
                .graphicsLayer {
                    val f = dampedDragAnimation.value
                    val padding = 2.dp.toPx()
                    translationX =
                        if (isLtr) lerp(padding, padding + dragWidth, f)
                        else lerp(-padding, -(padding + dragWidth), f)
                }
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 0.75f, progress)
                            val scaleY = lerp(0f, 0.75f, progress)
                            scale(scaleX, scaleY) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { thumbShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(with(density) { (8.dp * (1f - progress)).toPx() })
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            lens(
                                refractionHeight = with(density) { (5.dp * progress).toPx() },
                                refractionAmount = with(density) { (10.dp * progress).toPx() },
                                chromaticAberration = true
                            )
                        }
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4.dp,
                            color = Color.Black.copy(alpha = 0.05f)
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(ThumbWidth, ThumbHeight)
        )
    }
}
