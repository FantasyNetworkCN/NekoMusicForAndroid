package com.neko.music.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.min

internal class MoreSheetDragState(
    private val scope: CoroutineScope,
    private val density: Float,
    private val onDismiss: () -> Unit,
) {
    var offsetPx by mutableFloatStateOf(0f)
        private set
    var heightPx by mutableFloatStateOf(1f)
        private set
    private var animation: Job? = null
    private var closing = false

    fun updateHeight(height: Float) {
        heightPx = height.coerceAtLeast(1f)
        offsetPx = offsetPx.coerceIn(0f, heightPx)
    }

    fun dragBy(deltaY: Float): Float {
        if (closing) return 0f
        animation?.cancel()
        val previous = offsetPx
        offsetPx = (offsetPx + deltaY).coerceIn(0f, heightPx)
        return offsetPx - previous
    }

    fun release(velocityY: Float) {
        settle(
            shouldDismissMoreSheet(offsetPx, heightPx, velocityY, density),
            velocityY,
        )
    }

    fun dismiss() = settle(dismiss = true, velocityY = 0f)

    private fun settle(dismiss: Boolean, velocityY: Float) {
        if (closing) return
        closing = dismiss
        animation?.cancel()
        animation = scope.launch {
            animate(
                initialValue = offsetPx,
                targetValue = if (dismiss) heightPx else 0f,
                initialVelocity = velocityY,
                animationSpec = spring(
                    dampingRatio = .95f,
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = .5f,
                ),
            ) { value, _ ->
                offsetPx = value.coerceIn(0f, heightPx)
            }
            if (dismiss) onDismiss()
        }
    }
}

internal fun shouldDismissMoreSheet(
    offsetPx: Float,
    heightPx: Float,
    velocityY: Float,
    density: Float,
): Boolean {
    if (heightPx <= 0f || offsetPx <= 0f || density <= 0f) return false
    // 明确向上甩动表示收回面板，轻微下移不应因速度抖动误关闭。
    if (velocityY < -700f * density) return false
    val distanceThreshold = min(heightPx * .25f, 120f * density)
    return offsetPx >= distanceThreshold ||
        (offsetPx >= 8f * density && velocityY >= 900f * density)
}
