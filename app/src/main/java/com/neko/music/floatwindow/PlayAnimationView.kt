package com.neko.music.floatwindow

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlinx.coroutines.launch

class PlayAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        isAntiAlias = true
    }

    private var isPlaying = false
    private val bars = listOf(
        Bar(height = 0f, targetHeight = 0f),
        Bar(height = 0f, targetHeight = 0f),
        Bar(height = 0f, targetHeight = 0f)
    )

    private data class Bar(
        var height: Float,
        var targetHeight: Float
    )

    private var animationJob: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.Job())

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val barWidth = width / 6f
        val spacing = barWidth / 2f
        val startX = barWidth / 2f

        bars.forEachIndexed { index, bar ->
            val x = startX + index * (barWidth + spacing)
            val barHeight = bar.height * height * 0.7f
            val y = (height - barHeight) / 2f

            canvas.drawRoundRect(
                x,
                y,
                x + barWidth,
                y + barHeight,
                barWidth / 4f,
                barWidth / 4f,
                paint
            )
        }
    }

    fun setPlaying(playing: Boolean) {
        if (isPlaying == playing) return
        isPlaying = playing

        if (playing) {
            visibility = VISIBLE
            startAnimation()
        } else {
            animationJob?.cancel()
            visibility = INVISIBLE
        }
    }

    private fun startAnimation() {
        animationJob?.cancel()
        animationJob = scope.launch {
            while (isPlaying) {
                // 更新每个条的目标高度
                bars.forEachIndexed { index, bar ->
                    bar.targetHeight = when (index) {
                        0 -> (Math.sin(System.currentTimeMillis() / 200.0) * 0.5 + 0.5).toFloat()
                        1 -> (Math.sin(System.currentTimeMillis() / 200.0 + Math.PI / 3) * 0.5 + 0.5).toFloat()
                        2 -> (Math.sin(System.currentTimeMillis() / 200.0 + 2 * Math.PI / 3) * 0.5 + 0.5).toFloat()
                        else -> 0f
                    }
                }

                // 平滑过渡到目标高度
                bars.forEach { bar ->
                    val diff = bar.targetHeight - bar.height
                    bar.height += diff * 0.3f
                }

                postInvalidate()
                kotlinx.coroutines.delay(16)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animationJob?.cancel()
    }
}