package com.neko.music.ui.components

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.neko.music.ui.screens.LrcLine
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private data class AppleDrawableGlyph(
    val text: String,
    val bounds: Rect,
    val baseline: Float,
    val rtl: Boolean,
    val timing: AppleGlyphTiming?,
)

// 字符蒙版、柔边揭示与长音动画移植自 MeloX。
@Composable
internal fun AppleKaraokeText(
    line: LrcLine,
    playheadSeconds: () -> Float,
    lineEndTime: Float,
    timed: Boolean,
    baseColor: Color,
    highlightColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    glowEnabled: Boolean,
    centerText: Boolean,
    timingStrength: Float,
    unplayedAlpha: Float,
    softBlurDp: Float,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val style = remember(fontSize, lineHeight, centerText) {
        TextStyle(
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Black,
            textAlign = if (centerText) TextAlign.Center else TextAlign.Start,
        )
    }
    val timings = remember(line, lineEndTime) {
        buildAppleGlyphTimings(line, lineEndTime).associateBy { it.startChar }
    }
    val firstStartMs = remember(timings) { timings.values.minOfOrNull { it.startMs } ?: 0f }
    // 连写文字保留整段塑形；中日韩与拉丁文本直接绘制缓存的单字形，减少整段重复栅格化。
    val needsShaping = remember(line.text) {
        line.text.codePoints().anyMatch { code ->
            Character.isLetter(code) && Character.UnicodeScript.of(code) !in setOf(
                Character.UnicodeScript.LATIN,
                Character.UnicodeScript.HAN,
                Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA,
                Character.UnicodeScript.HANGUL,
                Character.UnicodeScript.CYRILLIC,
                Character.UnicodeScript.GREEK,
            )
        }
    }
    val paint = remember(fontSize, density) {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = with(density) { fontSize.toPx() }
            typeface = if (Build.VERSION.SDK_INT >= 28) {
                Typeface.create(Typeface.DEFAULT, 900, false)
            } else {
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        }
    }
    val blurMask = remember(softBlurDp, density) {
        if (softBlurDp > 0.05f) {
            BlurMaskFilter(with(density) { softBlurDp.dp.toPx() }, BlurMaskFilter.Blur.NORMAL)
        } else null
    }
    val glowRadius = with(density) { max(fontSize.toPx() * 0.12f, 3.dp.toPx()) }
    val glowMask = remember(glowRadius) { BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.NORMAL) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val layout = remember(line.text, widthPx, style, measurer) {
            measurer.measure(
                AnnotatedString(line.text), style,
                constraints = Constraints(minWidth = widthPx, maxWidth = widthPx),
            )
        }
        val glyphs = remember(layout, line.text, timings) {
            appleCharacterRanges(line.text).mapNotNull { range ->
                if (line.text[range.first] == '\n' || line.text[range.first] == '\r') return@mapNotNull null
                val box = layout.getBoundingBox(range.first)
                if (!box.width.isFinite() || box.width <= 0f) return@mapNotNull null
                val row = layout.getLineForOffset(range.first)
                AppleDrawableGlyph(
                    line.text.substring(range),
                    Rect(box.left, layout.getLineTop(row), box.right, layout.getLineBottom(row)),
                    layout.getLineBaseline(row),
                    layout.getBidiRunDirection(range.first) == ResolvedTextDirection.Rtl,
                    timings[range.first],
                )
            }
        }
        Canvas(
            Modifier.fillMaxWidth()
                .height(with(density) { layout.size.height.toDp() })
                .then(if (needsShaping) Modifier.blur(softBlurDp.dp) else Modifier)
                .semantics { text = AnnotatedString(line.text) },
        ) {
            if (!timed || timings.isEmpty()) {
                if (blurMask == null || needsShaping) {
                    drawText(layout, color = baseColor)
                } else {
                    paint.color = baseColor.toArgb()
                    paint.maskFilter = blurMask
                    glyphs.forEach { glyph ->
                        drawContext.canvas.nativeCanvas.drawText(glyph.text, glyph.bounds.left, glyph.baseline, paint)
                    }
                    paint.maskFilter = null
                }
                return@Canvas
            }
            val now = playheadSeconds() * 1000f
            val activation = lyricSmootherStep(
                (now - firstStartMs) / AppleLyricMotion.FocusColorDurationMs,
            )
            val effects = min(timingStrength, activation)
            val risePx = (fontSize.toPx() * 0.1f).coerceIn(1.5f * density.density, 6.dp.toPx())
            val shakePx = min(0.58.dp.toPx(), fontSize.toPx() * 0.013f)
            glyphs.forEach { glyph ->
                val bounds = glyph.bounds
                val fx = glyph.timing?.let { appleGlyphVisual(it, now) }
                withTransform({
                    translate(
                        left = (fx?.shakeX ?: 0f) * shakePx * effects,
                        top = (-(fx?.lift ?: 0f) * risePx + (fx?.shakeY ?: 0f) * shakePx) * effects,
                    )
                    val scale = 1f + ((fx?.scale ?: 1f) - 1f) * effects
                    scale(scale, scale, bounds.center)
                }) {
                    fun drawGlyph(color: Color, alpha: Float, glow: Boolean = false) {
                        if (needsShaping) {
                            clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
                                drawText(
                                    layout, color = color.copy(alpha = color.alpha * alpha.coerceIn(0f, 1f)),
                                    topLeft = Offset.Zero,
                                    shadow = if (glow) Shadow(color.copy(alpha = alpha), blurRadius = glowRadius) else null,
                                )
                            }
                        } else {
                            paint.color = color.toArgb()
                            paint.alpha = (color.alpha * alpha.coerceIn(0f, 1f) * 255f).roundToInt()
                            paint.maskFilter = if (glow) glowMask else blurMask
                            drawContext.canvas.nativeCanvas.drawText(glyph.text, bounds.left, glyph.baseline, paint)
                            paint.maskFilter = null
                        }
                    }
                    // 灰字和亮字共享同一次变换，抬升时不会留下原位的重影。
                    drawGlyph(baseColor, unplayedAlpha)
                    val reveal = fx?.reveal ?: 0f
                    if (reveal <= 0f) return@withTransform
                    val glow = if (glowEnabled) ((fx?.glow ?: 0f) * effects).coerceIn(0f, 1f) else 0f
                    if (glow > 0.001f) {
                        val front = if (glyph.rtl) bounds.right - bounds.width * reveal else bounds.left + bounds.width * reveal
                        clipRect(
                            if (glyph.rtl) front - glowRadius else bounds.left - glowRadius,
                            bounds.top - glowRadius,
                            if (glyph.rtl) bounds.right + glowRadius else front + glowRadius,
                            bounds.bottom + glowRadius,
                        ) { drawGlyph(highlightColor, glow, glow = true) }
                    }
                    val feather = max(bounds.width * AppleLyricMotion.GradientWidth, 1.5.dp.toPx())
                    val front = if (glyph.rtl) {
                        bounds.right + feather - (bounds.width + feather) * reveal
                    } else {
                        bounds.left - feather + (bounds.width + feather) * reveal
                    }
                    val solidLeft = if (glyph.rtl) max(front, bounds.left) else bounds.left
                    val solidRight = if (glyph.rtl) bounds.right else min(front, bounds.right)
                    if (solidRight > solidLeft) {
                        clipRect(solidLeft, bounds.top, solidRight, bounds.bottom) { drawGlyph(highlightColor, 1f) }
                    }
                    repeat(3) { step ->
                        val a = step / 3f
                        val b = (step + 1) / 3f
                        val mid = (a + b) * 0.5f
                        val mask = (1f - mid) * (1f - AppleLyricMotion.GradientReduction * mid)
                        val alpha = mask + (1f - mask) * glow * 0.14f
                        val left = if (glyph.rtl) max(front - feather * b, bounds.left) else max(front + feather * a, bounds.left)
                        val right = if (glyph.rtl) min(front - feather * a, bounds.right) else min(front + feather * b, bounds.right)
                        if (right > left) {
                            clipRect(left, bounds.top, right, bounds.bottom) { drawGlyph(highlightColor, alpha) }
                        }
                    }
                }
            }
        }
    }
}
