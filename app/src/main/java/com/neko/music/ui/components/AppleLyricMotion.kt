package com.neko.music.ui.components

import androidx.compose.animation.core.Easing
import com.neko.music.ui.screens.LrcLine
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// 动画模型移植自 MeloX Android 的 Apple 主题。
internal object AppleLyricMotion {
    const val FocusPosition = 0.25f
    const val CurrentLineScale = 1.02f
    const val BlurIntensity = 0.8f
    const val DistanceBlurScale = 1.05f
    const val BlurSkipAlpha = 0.05f
    const val FocusColorDurationMs = 120
    const val BlurSmoothDurationMs = 160
    const val CascadeDelayMs = 21f
    const val CascadeDelayIncreaseMs = 5f
    const val CascadeFollowingDelayMs = 30f
    const val CascadeCatchUpRatio = 0.97f
    const val CascadeChaseGradient = 0.70f
    const val CascadeDurationMs = 740f
    const val CascadeSnapThresholdMs = 260f
    const val CascadeBounce = 0.26f
    const val CascadeBounceGradient = 0.85f
    const val ScaleBounce = 0.32f
    const val ScaleDurationMs = 580
    const val LongToneThresholdMs = 950f
    const val LongToneMaxScale = 1.0625f
    const val LongToneGlowPeak = 0.7f
    const val GlowTailMs = 550f
    const val LiftContinuationMs = 320f
    const val GradientWidth = 0.7f
    const val GradientReduction = 0.65f
}

internal data class KaraokeSyllableTiming(
    val startChar: Int,
    val endChar: Int,
    val startSeconds: Float,
    val endSeconds: Float,
)

internal fun buildKaraokeSyllableTimings(
    line: LrcLine,
    lineEndTime: Float,
): List<KaraokeSyllableTiming> {
    if (line.text.isEmpty() || line.syllables.isEmpty()) return emptyList()
    var searchFrom = 0
    return line.syllables.mapIndexedNotNull { index, syllable ->
        if (syllable.text.isEmpty()) return@mapIndexedNotNull null
        val startChar = line.text.indexOf(syllable.text, searchFrom)
        if (startChar < 0) return@mapIndexedNotNull null
        val endChar = startChar + syllable.text.length
        searchFrom = endChar
        val start = max(line.time, syllable.time)
        KaraokeSyllableTiming(
            startChar, endChar, start,
            max(start + 0.001f, syllable.endTime ?: line.syllables.getOrNull(index + 1)?.time ?: lineEndTime),
        )
    }
}

internal fun karaokeRevealProgress(timing: KaraokeSyllableTiming, playheadSeconds: Float): Float =
    lyricSmootherStep((playheadSeconds - timing.startSeconds) /
        (timing.endSeconds - timing.startSeconds).coerceAtLeast(0.001f))

internal data class AppleGlyphTiming(
    val startChar: Int,
    val endChar: Int,
    val startMs: Float,
    val endMs: Float,
    val toneStartMs: Float,
    val toneDurationMs: Float,
    val toneCharacterIndex: Int,
    val toneCharacterCount: Int,
) {
    val isLongTone: Boolean get() = toneDurationMs >= AppleLyricMotion.LongToneThresholdMs
}

internal data class AppleGlyphVisual(
    val reveal: Float,
    val lift: Float,
    val scale: Float,
    val glow: Float,
    val shakeX: Float,
    val shakeY: Float,
)

internal fun appleCharacterRanges(text: String): List<IntRange> {
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
    return buildList {
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            add(start until end)
            start = end
            end = iterator.next()
        }
    }
}

internal fun buildAppleGlyphTimings(line: LrcLine, lineEndTime: Float): List<AppleGlyphTiming> {
    val characters = appleCharacterRanges(line.text)
    val result = buildList {
        buildKaraokeSyllableTimings(line, lineEndTime).forEach { syllable ->
            val ranges = characters.filter { it.first >= syllable.startChar && it.first < syllable.endChar }
            val durationMs = (syllable.endSeconds - syllable.startSeconds) * 1000f
            ranges.forEachIndexed { index, range ->
                add(AppleGlyphTiming(
                    startChar = range.first,
                    endChar = range.last + 1,
                    startMs = syllable.startSeconds * 1000f + durationMs * index / ranges.size,
                    endMs = syllable.startSeconds * 1000f + durationMs * (index + 1) / ranges.size,
                    toneStartMs = syllable.startSeconds * 1000f,
                    toneDurationMs = durationMs,
                    toneCharacterIndex = index,
                    toneCharacterCount = ranges.size,
                ))
            }
        }
    }.toMutableList()
    // 拉丁单词共享长音呼吸，揭示与上浮仍按各字符时间，避免把一个单词整体放大。
    val words = BreakIterator.getWordInstance(Locale.ROOT).apply { setText(line.text) }
    var wordStart = words.first()
    var wordEnd = words.next()
    while (wordEnd != BreakIterator.DONE) {
        val indexes = result.indices.filter { result[it].startChar in wordStart until wordEnd }
        if (indexes.size > 1 && indexes.all { index ->
                val char = line.text[result[index].startChar]
                char in 'a'..'z' || char in 'A'..'Z'
            }) {
            val startMs = indexes.minOf { result[it].startMs }
            val endMs = indexes.maxOf { result[it].endMs }
            indexes.forEachIndexed { index, resultIndex ->
                result[resultIndex] = result[resultIndex].copy(
                    toneStartMs = startMs,
                    toneDurationMs = endMs - startMs,
                    toneCharacterIndex = index,
                    toneCharacterCount = indexes.size,
                )
            }
        }
        wordStart = wordEnd
        wordEnd = words.next()
    }
    return result
}

internal fun appleGlyphVisual(timing: AppleGlyphTiming, nowMs: Float): AppleGlyphVisual {
    val duration = (timing.endMs - timing.startMs).coerceAtLeast(1f)
    val raw = ((nowMs - timing.startMs) / duration).coerceIn(0f, 1f)
    val reveal = if (!timing.isLongTone || duration <= 460f) {
        lyricSmootherStep(raw)
    } else {
        val attack = lyricSmootherStep((nowMs - timing.startMs) / 300f)
        val release = lyricSmootherStep((nowMs - timing.endMs + 160f) / 160f)
        (0.82f * attack + 0.08f * raw + 0.10f * release).coerceIn(0f, 1f)
    }
    val lift = lyricSmootherStep((nowMs - timing.startMs) / (duration + AppleLyricMotion.LiftContinuationMs))
    val envelope = if (timing.isLongTone) appleLongToneEnvelope(
        nowMs, timing.toneStartMs, timing.toneDurationMs,
        timing.toneCharacterIndex, timing.toneCharacterCount,
    ) else 0f
    val emphasis = lyricSmootherStep((timing.toneDurationMs - AppleLyricMotion.LongToneThresholdMs) /
        (2800f - AppleLyricMotion.LongToneThresholdMs))
    val expansion = if (timing.isLongTone) 0.7f + 0.3f * emphasis else 0f
    val glow = if (timing.isLongTone) envelope * (0.32f + 0.38f * emphasis) / AppleLyricMotion.LongToneGlowPeak else 0f
    val shake = if (raw > 0f && raw < 1f) envelope * expansion else 0f
    val phase = (nowMs - timing.toneStartMs).coerceAtLeast(0f)
    return AppleGlyphVisual(
        reveal = reveal,
        lift = lift,
        scale = 1f + (AppleLyricMotion.LongToneMaxScale - 1f) * envelope * expansion,
        glow = glow,
        shakeX = sin(phase * 0.016f + timing.toneCharacterIndex * 1.73f) * shake,
        shakeY = sin(phase * 0.021f + timing.toneCharacterIndex * 0.91f) * shake * 0.42f,
    )
}

internal fun appleLongToneEnvelope(
    playbackTimeMs: Float,
    groupStartMs: Float,
    durationMs: Float,
    characterIndex: Int,
    characterCount: Int,
): Float {
    val stagger = if (characterCount > 1) durationMs * 0.55f * characterIndex / (characterCount - 1) else 0f
    val progress = ((playbackTimeMs - groupStartMs - stagger) / max(durationMs, 1000f)).coerceIn(0f, 1f)
    return if (progress <= 0.5f) lyricSmootherStep(progress / 0.5f)
    else 1f - lyricSmootherStep((progress - 0.5f) / 0.5f)
}

internal data class AppleCascadeTiming(val delayMs: Float, val durationMs: Float)

internal fun appleCascadeTimings(maximumOrder: Int, durationMs: Float): List<AppleCascadeTiming> =
    (0..maximumOrder.coerceAtLeast(0)).map { order ->
        if (order == 0) AppleCascadeTiming(0f, durationMs) else {
            val delay = AppleLyricMotion.CascadeFollowingDelayMs + order * AppleLyricMotion.CascadeDelayMs +
                order * (order - 1) / 2f * AppleLyricMotion.CascadeDelayIncreaseMs
            AppleCascadeTiming(delay, max(durationMs * AppleLyricMotion.CascadeCatchUpRatio - delay, min(180f, durationMs * 0.5f)))
        }
    }

internal fun appleCascadeBounce(order: Int, maximumOrder: Int): Float {
    val normalized = (order.coerceIn(0, maximumOrder) + 1f) / (maximumOrder + 1f)
    return AppleLyricMotion.CascadeBounce * (1f - (1f - normalized) * AppleLyricMotion.CascadeBounceGradient)
}

internal fun appleDistanceOpacity(distance: Float, focus: Float): Float {
    val base = when {
        distance <= 1f -> 1f - distance * 0.44f
        distance <= 2f -> 0.56f - (distance - 1f) * 0.22f
        else -> max(0.12f, 0.34f - (distance - 2f) * 0.07f)
    }
    return (base + (1f - base) * focus) * (0.52f + 0.48f * focus)
}

internal fun appleDistanceBlurRadius(distance: Float, focus: Float): Float =
    min(max(distance - 1.35f, 0f) * 3.1f, 10f) * AppleLyricMotion.BlurIntensity *
        AppleLyricMotion.DistanceBlurScale * (1f - focus)

internal fun appleLineEndTime(lines: List<LrcLine>, index: Int): Float {
    val line = lines[index]
    return line.endTime ?: line.syllables.maxOfOrNull { it.endTime ?: it.time }
        ?.takeIf { it > line.time }
        ?: lines.getOrNull(index + 1)?.time
        ?: (line.time + 4f)
}

internal fun appleDisplayLyrics(lines: List<LrcLine>): List<LrcLine> = buildList {
    lines.forEachIndexed { index, line ->
        val previous = lines.getOrNull(index - 1)
        val previousEnd = previous?.let {
            it.endTime ?: it.syllables.maxOfOrNull { syllable -> syllable.endTime ?: syllable.time }
                ?.takeIf { end -> end > it.time }
        } ?: if (index == 0) 0f else line.time
        val countdownEnd = line.time - 0.25f
        if (countdownEnd - previousEnd >= 4f && previous?.text?.isBlank() != true) {
            add(LrcLine(previousEnd, "", endTime = countdownEnd))
        }
        add(line)
    }
}

internal fun appleActiveLineIndexes(lines: List<LrcLine>, positionSeconds: Float): Set<Int> {
    val latest = lines.indexOfLast { (it.syllables.minOfOrNull { word -> word.time } ?: it.time) <= positionSeconds }
    if (latest < 0) return emptySet()
    if (lines[latest].text.isBlank()) return setOf(latest)
    return buildSet {
        for (index in 0..latest) {
            val line = lines[index]
            if (line.text.isNotBlank() && line.syllables.isNotEmpty() &&
                appleLineEndTime(lines, index) > positionSeconds
            ) add(index)
        }
        // 普通 LRC 没有逐字结束事件，保持最后一句聚焦直到下一句接管。
        if (isEmpty()) add(latest)
    }
}

internal fun lyricSmootherStep(value: Float): Float {
    val p = value.coerceIn(0f, 1f)
    return p * p * p * (p * (p * 6f - 15f) + 10f)
}

internal object AppleLyricsSmoothStepEasing : Easing {
    override fun transform(fraction: Float): Float {
        val p = fraction.coerceIn(0f, 1f)
        return p * p * (3f - 2f * p)
    }
}

internal class AppleLyricsSpringEasing(private val bounce: Float) : Easing {
    override fun transform(fraction: Float): Float {
        val t = fraction.coerceIn(0f, 1f)
        if (bounce <= 0.0001f) return AppleLyricsSmoothStepEasing.transform(t)
        val damping = (1f - bounce.coerceIn(0f, 0.95f) * 0.72f).coerceIn(0.18f, 0.999f)
        val omega = 9.5f
        val damped = omega * sqrt(max(1f - damping * damping, 0.0001f))
        val envelope = exp((-damping * omega * t).toDouble()).toFloat()
        val raw = 1f - envelope * (
            kotlin.math.cos((damped * t).toDouble()).toFloat() +
                (damping / sqrt(max(1f - damping * damping, 0.0001f))) *
                sin((damped * t).toDouble()).toFloat()
            )
        val endEnvelope = exp((-damping * omega).toDouble()).toFloat()
        val endRaw = 1f - endEnvelope * (
            kotlin.math.cos(damped.toDouble()).toFloat() +
                (damping / sqrt(max(1f - damping * damping, 0.0001f))) *
                sin(damped.toDouble()).toFloat()
            )
        return if (abs(endRaw) > 0.0001f) raw / endRaw else raw
    }
}
