package com.neko.music.ui.components

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import kotlin.math.exp
import kotlin.math.sin

/**
 * 播放页展开、收起与手势跟手共用同一套转场参数。
 *
 * 参数与 MeloX 参考实现保持一致：外壳和封面使用同一套 Apple 风格缓动，
 * 主时间线使用临界阻尼弹簧，关闭尾段在 bounds 几何中完成下沉回弹。
 */
internal const val NekoPlayerTransitionDurationMillis = 460

private const val NekoPlayerSpringK = 6f
private const val NekoPlayerSpringInitialVelocity = 0.15f
private const val NekoPlayerSettle99K = 6.2f
private const val NekoPlayerSinkWindowStart = 0.62f

private val nekoPlayerSpringEnd =
    1f - (1f + (1f - NekoPlayerSpringInitialVelocity) * NekoPlayerSpringK) *
        exp(-NekoPlayerSpringK)

internal val NekoPlayerAppleEasing = Easing { fraction ->
    val f = fraction.coerceIn(0f, 1f)
    val z = NekoPlayerSpringK * f
    (1f - (1f + (1f - NekoPlayerSpringInitialVelocity) * z) * exp(-z)) /
        nekoPlayerSpringEnd
}

internal fun nekoPlayerExpansionSpec() = tween<Float>(
    durationMillis = NekoPlayerTransitionDurationMillis,
    easing = NekoPlayerAppleEasing,
)

internal fun nekoPlayerSettlementSpec(): FiniteAnimationSpec<Float> {
    val durationSec = NekoPlayerTransitionDurationMillis / 1000f
    val omega = NekoPlayerSettle99K / durationSec
    return spring(
        dampingRatio = 1f,
        stiffness = omega * omega,
        visibilityThreshold = 0.001f,
    )
}

internal const val NekoPlayerSinkPeakDp = 22f

private fun nekoPlayerSinkFraction(fraction: Float): Float {
    if (fraction.isNaN() || fraction.isInfinite()) return 0f
    val span = 1f - NekoPlayerSinkWindowStart
    val unit = ((fraction - NekoPlayerSinkWindowStart) / span).coerceIn(0f, 1f)
    return if (unit <= 0f) 0f else sin(Math.PI.toFloat() * unit)
}

internal fun nekoPlayerSharedBoundsTransform(
    sinkPeakPx: Float,
    sinkEnabled: () -> Boolean,
): BoundsTransform = BoundsTransform { _, _ ->
    NekoPlayerSinkBoundsSpec(
        durationMillis = NekoPlayerTransitionDurationMillis,
        sinkPeakPx = sinkPeakPx,
        sinkEnabled = sinkEnabled,
    )
}

private class NekoPlayerSinkBoundsSpec(
    private val durationMillis: Int,
    private val sinkPeakPx: Float,
    private val sinkEnabled: () -> Boolean,
) : FiniteAnimationSpec<Rect> {
    override fun <V : AnimationVector> vectorize(
        converter: TwoWayConverter<Rect, V>,
    ): VectorizedFiniteAnimationSpec<V> =
        NekoPlayerSinkVectorizedSpec(
            converter = converter,
            durationMillis = durationMillis,
            sinkPeakPx = sinkPeakPx,
            sinkEnabled = sinkEnabled,
        )
}

private class NekoPlayerSinkVectorizedSpec<V : AnimationVector>(
    private val converter: TwoWayConverter<Rect, V>,
    durationMillis: Int,
    private val sinkPeakPx: Float,
    private val sinkEnabled: () -> Boolean,
) : VectorizedFiniteAnimationSpec<V> {
    private val durationNanos = durationMillis.coerceAtLeast(1).toLong() * 1_000_000L

    override fun getDurationNanos(
        initialValue: V,
        targetValue: V,
        initialVelocity: V,
    ): Long = durationNanos

    override fun getValueFromNanos(
        playTimeNanos: Long,
        initialValue: V,
        targetValue: V,
        initialVelocity: V,
    ): V {
        val timeFraction = (playTimeNanos.toFloat() / durationNanos.toFloat()).coerceIn(0f, 1f)
        val geometryFraction = NekoPlayerAppleEasing.transform(timeFraction)
        val initial = converter.convertFromVector(initialValue)
        val target = converter.convertFromVector(targetValue)

        // 打开、关闭、快速反向时每一帧都重新判断方向，避免沿用开头的旧 spec。
        val hi = maxOf(initial.height, target.height)
        val lo = minOf(initial.height, target.height)
        val sinkDy = if (sinkEnabled() && sinkPeakPx > 0f && lo < hi * 0.6f) {
            nekoPlayerSinkFraction(timeFraction) * sinkPeakPx
        } else {
            0f
        }

        val rect = Rect(
            left = initial.left + (target.left - initial.left) * geometryFraction,
            top = initial.top + (target.top - initial.top) * geometryFraction + sinkDy,
            right = initial.right + (target.right - initial.right) * geometryFraction,
            bottom = initial.bottom + (target.bottom - initial.bottom) * geometryFraction + sinkDy,
        )
        return converter.convertToVector(rect)
    }

    override fun getVelocityFromNanos(
        playTimeNanos: Long,
        initialValue: V,
        targetValue: V,
        initialVelocity: V,
    ): V = converter.convertToVector(Rect(0f, 0f, 0f, 0f))
}

/**
 * 下拉后松手回弹。
 *
 * 保持目标状态为收起，只把 fraction 沿原通道退回到全展开，避免状态翻转造成的
 * “先向下沉一下再弹回”。
 */
internal suspend fun reboundPlayerToOpen(state: SeekableTransitionState<Boolean>) {
    if (state.currentState != true || state.targetState != false) {
        state.animateTo(targetState = true, animationSpec = nekoPlayerSettlementSpec())
        return
    }
    val start = state.fraction.coerceIn(0f, 1f)
    if (start <= 1e-3f) {
        state.snapTo(true)
        return
    }

    val omega = NekoPlayerSettle99K / (NekoPlayerTransitionDurationMillis / 1000f)
    var firstFrameNanos = 0L
    var frames = 0
    while (true) {
        val now = withFrameNanos { it }
        if (firstFrameNanos == 0L) firstFrameNanos = now
        val seconds = (now - firstFrameNanos) / 1_000_000_000f
        val fraction = start * (1f + omega * seconds) * exp(-omega * seconds)
        state.seekTo(fraction = fraction.coerceIn(0f, 1f), targetState = false)
        if (fraction <= start * 0.01f || fraction <= 1e-3f) break
        if (++frames > 600) break
    }
    state.snapTo(true)
}

/**
 * 从底部上滑后又取消时，沿原方向退回迷你栏。
 *
 * 与 [reboundPlayerToOpen] 对称：保持目标状态为展开，只让 fraction 回到 0。
 */
internal suspend fun reboundPlayerToClosed(state: SeekableTransitionState<Boolean>) {
    if (state.currentState != false || state.targetState != true) {
        state.animateTo(targetState = false, animationSpec = nekoPlayerSettlementSpec())
        return
    }
    val start = state.fraction.coerceIn(0f, 1f)
    if (start <= 1e-3f) {
        state.snapTo(false)
        return
    }

    val omega = NekoPlayerSettle99K / (NekoPlayerTransitionDurationMillis / 1000f)
    var firstFrameNanos = 0L
    var frames = 0
    while (true) {
        val now = withFrameNanos { it }
        if (firstFrameNanos == 0L) firstFrameNanos = now
        val seconds = (now - firstFrameNanos) / 1_000_000_000f
        val fraction = start * (1f + omega * seconds) * exp(-omega * seconds)
        state.seekTo(fraction = fraction.coerceIn(0f, 1f), targetState = true)
        if (fraction <= start * 0.01f || fraction <= 1e-3f) break
        if (++frames > 600) break
    }
    state.snapTo(false)
}

internal data object NekoPlayerShellKey

internal data class NekoPlayerArtworkKey(val mediaId: Int?)
