package com.neko.music.ui.icons

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.neko.music.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 页面可统一调节图标比例，默认保留字形原始比例。 */
val LocalNekoIconScale = compositionLocalOf { 1f }

/**
 * 双字体图标入口。显式字号按 sp 渲染；兼容现有界面时由图标盒尺寸确定字号。
 * 字体图标使用真实墨迹边界居中，不启用会放大字形的 ss16 特性。
 */
@Composable
fun NekoSymbolIcon(
    symbol: NekoSymbol,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    variant: NekoSymbolVariant = NekoSymbolVariant.Regular,
    iconSize: TextUnit = TextUnit.Unspecified,
) {
    val density = LocalDensity.current
    val iconScale = LocalNekoIconScale.current
    val tint = if (color == Color.Unspecified) LocalContentColor.current else color
    val explicitSizePx = if (iconSize == TextUnit.Unspecified) {
        null
    } else {
        with(density) { iconSize.toPx() }
    }
    Box(
        modifier = modifier
            .size(24.dp)
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                    role = Role.Image
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (symbol) {
            NekoSymbol.Home, NekoSymbol.Settings -> {
                val resource = if (symbol == NekoSymbol.Home) {
                    R.drawable.ic_sf_home
                } else {
                    R.drawable.ic_sf_settings
                }
                val vectorModifier = if (explicitSizePx != null) {
                    Modifier.requiredSize(with(density) { (explicitSizePx * iconScale).toDp() })
                } else {
                    Modifier.matchParentSize()
                }
                Icon(
                    painter = painterResource(resource),
                    contentDescription = null,
                    modifier = vectorModifier,
                    tint = tint,
                )
            }

            NekoSymbol.Shuffle, NekoSymbol.Repeat, NekoSymbol.RepeatOne -> {
                PlaybackModeIcon(
                    symbol = symbol,
                    modifier = Modifier.matchParentSize(),
                    color = tint,
                    sizePx = explicitSizePx,
                    fontScale = density.fontScale,
                    iconScale = iconScale,
                )
            }

            NekoSymbol.Star, NekoSymbol.Circle, NekoSymbol.HeartPulse -> {
                VectorSymbolIcon(
                    symbol = symbol,
                    variant = variant,
                    modifier = Modifier.matchParentSize(),
                    color = tint,
                    sizePx = explicitSizePx,
                    fontScale = density.fontScale,
                    iconScale = iconScale,
                )
            }

            else -> {
                val context = LocalContext.current
                val weight = if (variant == NekoSymbolVariant.Fill) FontWeight.Medium else FontWeight.Normal
                val typeface = remember(context, weight, symbol.usesSubsetFont) {
                    NekoSfTypefaceCache.obtain(context, weight.weight, symbol.usesSubsetFont)
                }
                val glyph = remember(typeface, symbol) {
                    typeface?.let { SfGlyph(it, requireNotNull(symbol.codePoint)) }
                }
                if (glyph == null) {
                    Icon(Icons.Default.Info, null, Modifier.matchParentSize(), tint)
                } else {
                    Canvas(Modifier.matchParentSize()) {
                        val textSize = (explicitSizePx ?: (min(size.width, size.height) * density.fontScale)) * iconScale
                        drawIntoCanvas { canvas ->
                            glyph.draw(canvas.nativeCanvas, textSize, size.width, size.height, tint)
                        }
                    }
                }
            }
        }
    }
}

/** 复用 Paint 和墨迹测量结果；动画重绘时不重复创建字体、字符串或轮廓。 */
private class SfGlyph(typeface: Typeface, codePoint: Int) {
    private val text = String(Character.toChars(codePoint))
    private val bounds = Rect()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        this.typeface = typeface
        textAlign = Paint.Align.LEFT
    }
    private var measuredSize = Float.NaN

    fun draw(canvas: android.graphics.Canvas, textSize: Float, width: Float, height: Float, color: Color) {
        if (textSize != measuredSize) {
            paint.textSize = textSize
            paint.getTextBounds(text, 0, text.length, bounds)
            measuredSize = textSize
        }
        paint.color = color.toArgb()
        val x = width / 2f - (bounds.left + bounds.right) / 2f
        val baseline = height / 2f - (bounds.top + bounds.bottom) / 2f
        canvas.drawText(text, x, baseline, paint)
    }
}

@Composable
private fun PlaybackModeIcon(
    symbol: NekoSymbol,
    modifier: Modifier,
    color: Color,
    sizePx: Float?,
    fontScale: Float,
    iconScale: Float,
) {
    val kind = when (symbol) {
        NekoSymbol.Shuffle -> NekoSfGlyphPaths.SHUFFLE
        NekoSymbol.RepeatOne -> NekoSfGlyphPaths.REPEAT_ONE
        else -> NekoSfGlyphPaths.REPEAT
    }
    val path = remember(kind) {
        PathParser().parsePathString(NekoSfGlyphPaths.paths(kind).joinToString("")).toPath()
    }
    val inkEm = when (symbol) {
        NekoSymbol.Shuffle -> 2298f / 2048f
        NekoSymbol.RepeatOne -> 2139f / 2048f
        else -> 2134f / 2048f
    }
    Canvas(modifier.alpha(color.alpha)) {
        val side = (sizePx ?: (min(size.width, size.height) * fontScale)) * inkEm * iconScale
        withTransform({
            translate((size.width - side) / 2f, (size.height - side) / 2f)
            scale(side / 100f, side / 100f, pivot = androidx.compose.ui.geometry.Offset.Zero)
        }) {
            drawPath(path, color.copy(alpha = 1f))
        }
    }
}

@Composable
private fun VectorSymbolIcon(
    symbol: NekoSymbol,
    variant: NekoSymbolVariant,
    modifier: Modifier,
    color: Color,
    sizePx: Float?,
    fontScale: Float,
    iconScale: Float,
) {
    val path = remember(symbol, variant) {
        when (symbol) {
            NekoSymbol.Star -> Path().apply {
                val outer = if (variant == NekoSymbolVariant.Fill) 46f else 46f - 46f * .16f / 2f
                for (i in 0 until 10) {
                    val radius = if (i % 2 == 0) outer else outer * .4f
                    val angle = -Math.PI / 2 + i * Math.PI / 5
                    val x = 50f + radius * cos(angle).toFloat()
                    val y = 50f + radius * sin(angle).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            NekoSymbol.HeartPulse -> heartPulsePath()
            else -> Path()
        }
    }
    val ecg = remember { heartPulseEcgPath() }
    Canvas(modifier.alpha(color.alpha)) {
        val side = (sizePx ?: (min(size.width, size.height) * fontScale)) * iconScale
        val renderSide = if (symbol == NekoSymbol.Star) side * .92f else side
        withTransform({
            translate((size.width - renderSide) / 2f, (size.height - renderSide) / 2f)
            scale(renderSide / 100f, renderSide / 100f, pivot = androidx.compose.ui.geometry.Offset.Zero)
        }) {
            val opaque = color.copy(alpha = 1f)
            when (symbol) {
                NekoSymbol.Circle -> drawCircle(
                    opaque,
                    radius = 42f - 6.5f / 2f,
                    center = androidx.compose.ui.geometry.Offset(50f, 50f),
                    style = Stroke(6.5f),
                )
                NekoSymbol.HeartPulse -> {
                    drawPath(path, opaque, style = Stroke(7.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(ecg, opaque, style = Stroke(7.5f * .72f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                else -> drawPath(
                    path,
                    opaque,
                    style = if (variant == NekoSymbolVariant.Fill) Fill
                    else Stroke(46f * .16f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

private fun heartPulsePath() = Path().apply {
    moveTo(50f, 34f)
    cubicTo(50f, 22f, 30f, 12f, 16f, 24f)
    cubicTo(2f, 36f, 4f, 56f, 20f, 68f)
    cubicTo(32f, 77f, 44f, 84f, 50f, 90f)
    cubicTo(56f, 84f, 68f, 77f, 80f, 68f)
    cubicTo(96f, 56f, 98f, 36f, 84f, 24f)
    cubicTo(70f, 12f, 50f, 22f, 50f, 34f)
    close()
}

private fun heartPulseEcgPath() = Path().apply {
    moveTo(10f, 52f)
    lineTo(38f, 52f)
    quadraticTo(44f, 50f, 49f, 52f)
    lineTo(55f, 52f)
    quadraticTo(58f, 44f, 61f, 52f)
    quadraticTo(64f, 60f, 67f, 52f)
    lineTo(72f, 52f)
    quadraticTo(76f, 49f, 80f, 52f)
    lineTo(90f, 52f)
}
