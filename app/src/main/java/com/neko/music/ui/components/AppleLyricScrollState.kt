package com.neko.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.neko.music.ui.screens.LrcLine
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// 每行独立追赶列表滚动，保留换行被打断时的实际位移。
@Stable
internal class AppleLyricScrollState(private val lines: List<LrcLine>) {
    var focusIndex by mutableIntStateOf(-1)
        private set
    var positioned by mutableStateOf(false)
        private set
    val rowHeights = mutableMapOf<Int, Int>()
    private val rowProgress = List(lines.size) { Animatable(1f) }
    private val scrollProgress = Animatable(1f)
    private var distance by mutableStateOf(0f)
    private var initialOffsets by mutableStateOf<Map<Int, Float>>(emptyMap())
    private var destinations by mutableStateOf<Map<Int, Float>>(emptyMap())
    private var focusExpansion = 0f

    private fun settledOffset(index: Int): Float =
        if (index > focusIndex && focusIndex >= 0) focusExpansion else 0f

    fun offset(index: Int): Float {
        val initial = initialOffsets[index] ?: return settledOffset(index)
        val destination = destinations[index] ?: 0f
        return initial + distance * scrollProgress.value -
            (distance + initial - destination) * rowProgress[index].value
    }

    fun clear(focus: Int = focusIndex) {
        focusIndex = focus
        initialOffsets = emptyMap()
        destinations = emptyMap()
        distance = 0f
    }

    suspend fun follow(
        listState: LazyListState,
        nextIndex: Int,
        viewportHeightPx: Float,
        estimatedHeight: (Int) -> Float,
        smooth: Boolean,
        scaleCurrentLine: Boolean,
        playbackSpeed: Float,
        playheadSeconds: Float,
        forcePosition: Boolean = false,
    ) {
        if (nextIndex !in lines.indices) {
            clear(-1)
            listState.scrollToItem(0)
            positioned = true
            return
        }
        val previousIndex = focusIndex
        val desiredTop = (viewportHeightPx - estimatedHeight(nextIndex)) * AppleLyricMotion.FocusPosition
        val targetOffset = -desiredTop.roundToInt()
        val expansion = if (scaleCurrentLine) {
            estimatedHeight(nextIndex) * (AppleLyricMotion.CurrentLineScale - 1f)
        } else {
            0f
        }
        if (!positioned || !smooth || forcePosition) {
            focusExpansion = expansion
            clear(nextIndex)
            listState.scrollToItem(nextIndex + 1, targetOffset)
            positioned = true
            return
        }
        val speed = playbackSpeed.coerceAtLeast(0.01f)
        val remainingMs = lines.getOrNull(nextIndex + 1)?.let {
            max((it.time - playheadSeconds) * 1000f / speed, 0f)
        }
        val duration = if (remainingMs == null) {
            AppleLyricMotion.CascadeDurationMs
        } else if (remainingMs < AppleLyricMotion.CascadeSnapThresholdMs) {
            0f
        } else {
            min(AppleLyricMotion.CascadeDurationMs, remainingMs)
        }
        val targetItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == nextIndex + 1 }
        val adjacentForward = previousIndex < nextIndex &&
            (nextIndex - previousIndex == 1 ||
                (previousIndex >= 0 && (previousIndex until nextIndex).all {
                    lines[it + 1].time - lines[it].time <= 0.28f
                }))
        if (duration <= 0f || targetItem == null || !adjacentForward) {
            focusExpansion = expansion
            clear(nextIndex)
            if (duration <= 0f || abs(nextIndex - previousIndex) > 4 || targetItem == null) {
                listState.scrollToItem(nextIndex + 1, targetOffset)
            } else {
                listState.animateScrollToItem(nextIndex + 1, targetOffset)
            }
            return
        }
        val movement = targetItem.offset - desiredTop
        if (abs(movement) <= 0.5f) {
            focusExpansion = expansion
            clear(nextIndex)
            return
        }
        val visibleIndexes = listState.layoutInfo.visibleItemsInfo.mapNotNull {
            (it.index - 1).takeIf(lines.indices::contains)
        }
        val first = min(visibleIndexes.minOrNull() ?: nextIndex, max(nextIndex - 1, 0))
        val last = min((visibleIndexes.maxOrNull() ?: nextIndex) + 4, lines.lastIndex)
        val moving = first..last
        val carried = moving.associateWith(::offset)
        focusExpansion = expansion
        focusIndex = nextIndex
        distance = movement
        initialOffsets = carried
        destinations = moving.associateWith(::settledOffset)
        scrollProgress.snapTo(0f)
        moving.forEach { rowProgress[it].snapTo(0f) }
        val firstChasing = max(nextIndex - 1, 0)
        val maximumOrder = max(last - firstChasing, 0)
        val timings = appleCascadeTimings(maximumOrder, duration)
        coroutineScope {
            launch {
                var previous = 0f
                listState.scroll {
                    scrollProgress.animateTo(
                        1f,
                        tween(duration.roundToInt(), easing = AppleLyricsSmoothStepEasing),
                    ) {
                        scrollBy((value - previous) * movement)
                        previous = value
                    }
                }
            }
            moving.forEach { index ->
                val chaseOrder = max(index - firstChasing, 0)
                val movementTiming = timings[min(max(index - nextIndex, 0), timings.lastIndex)]
                val chaseTiming = timings[min(chaseOrder, timings.lastIndex)]
                val rowDuration = duration +
                    (chaseTiming.durationMs - duration) * AppleLyricMotion.CascadeChaseGradient
                launch {
                    delay(movementTiming.delayMs.toLong())
                    rowProgress[index].animateTo(
                        1f,
                        tween(
                            rowDuration.roundToInt().coerceAtLeast(1),
                            easing = AppleLyricsSpringEasing(appleCascadeBounce(chaseOrder, maximumOrder)),
                        ),
                    )
                }
            }
        }
        clear(nextIndex)
    }
}
