package com.neko.music.floatwindow

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CircularProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#30FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6B9D")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private var progress = 0
    private val rect = RectF()

    fun setProgress(value: Int) {
        progress = value.coerceIn(0, 100)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (width / 2f) - 2f // 留出描边的一半

        // 绘制背景圆环
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)

        // 绘制进度圆环
        if (progress > 0) {
            val sweepAngle = (progress / 100f * 360f)
            rect.set(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius
            )
            canvas.drawArc(rect, -90f, sweepAngle, false, progressPaint)
        }
    }
}