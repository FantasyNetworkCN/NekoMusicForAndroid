package com.neko.music.desktoplyric

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.util.AttributeSet
import android.widget.TextView

/**
 * 桌面歌词专用 TextView。
 *
 * 逐字高亮直接按字符裁剪并重绘文本，不创建/移除 ForegroundColorSpan，
 * 避免长歌词在 20~30 FPS 更新时反复触发布局和 SpanWatcher。
 */
class DesktopLyricKaraokeTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle,
) : TextView(context, attrs, defStyleAttr) {

    private var karaokeProgress: FloatArray? = null
    private var highlightColor: Int = Color.rgb(255, 93, 162)
    private val highlightPath = Path()

    fun setKaraokeProgress(progress: FloatArray?) {
        karaokeProgress = progress
        invalidate()
    }

    fun setKaraokeHighlightColor(color: Int) {
        if (highlightColor == color) return
        highlightColor = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val progress = karaokeProgress ?: return
        val layout = layout ?: return
        if (progress.isEmpty()) return

        val textPaint = paint
        val previousColor = textPaint.color
        highlightPath.reset()

        progress.forEachIndexed { index, value ->
            if (value <= 0f || index >= text.length) return@forEachIndexed
            val left = layout.getPrimaryHorizontal(index)
            val right = layout.getPrimaryHorizontal(index + 1)
            if (right <= left) return@forEachIndexed
            val lineIndex = layout.getLineForOffset(index)
            highlightPath.addRect(
                left,
                layout.getLineTop(lineIndex).toFloat(),
                if (value >= 1f) right else left + (right - left) * value,
                layout.getLineBottom(lineIndex).toFloat(),
                Path.Direction.CW,
            )
        }

        if (highlightPath.isEmpty) return
        textPaint.color = highlightColor
        canvas.save()
        canvas.translate(compoundPaddingLeft.toFloat(), extendedPaddingTop.toFloat())
        canvas.clipPath(highlightPath)
        layout.draw(canvas)
        canvas.restore()
        textPaint.color = previousColor
    }
}
