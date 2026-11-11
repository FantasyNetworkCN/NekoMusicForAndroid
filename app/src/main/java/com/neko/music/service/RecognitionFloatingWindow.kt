package com.neko.music.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import com.neko.music.R
import kotlin.math.abs
import kotlin.math.sin

/**
 * Floating recognition control modeled after NetEase Cloud Music's floating
 * recognition ball/panel, with NekoMusic styling and interactions.
 */
internal class RecognitionFloatingWindow(
    context: Context,
    private val onStopRecognition: () -> Unit,
    private val onClose: () -> Unit,
    private val onOpenApp: () -> Unit,
    private val onStartRecognition: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val params = WindowManager.LayoutParams(
        dp(OVERLAY_WIDTH_DP),
        dp(OVERLAY_HEIGHT_DP),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        },
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        android.graphics.PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = ((appContext.resources.displayMetrics.widthPixels - dp(OVERLAY_WIDTH_DP)) / 2)
            .coerceAtLeast(0)
        y = dp(92)
    }
    private val view = RecognitionOverlayView(
        context = appContext,
        onExpand = { setMode(expandedMode) },
        onCollapse = { setMode(OverlayMode.Bubble, repositionBubble = true) },
        onStopRecognition = onStopRecognition,
        onClose = onClose,
        onOpenApp = onOpenApp,
        onStartRecognition = onStartRecognition,
        onDrag = ::dragWindow,
        onDragEnd = ::snapBubbleToEdge,
    )
    private var attached = false
    private var visible = false
    private var mode = OverlayMode.Bubble
    private var expandedMode = OverlayMode.Idle

    val isVisible: Boolean
        get() = attached && visible

    fun showBubble() {
        val firstAttach = !attached
        if (!attached) {
            runCatching {
                windowManager.addView(view, params)
                attached = true
            }.onFailure {
                return
            }
        }
        visible = true
        view.visibility = View.VISIBLE
        setMode(OverlayMode.Bubble, repositionBubble = firstAttach || mode != OverlayMode.Bubble)
    }

    fun showIdle() {
        expandedMode = OverlayMode.Idle
        view.setRecordingActive(false)
        showBubble()
    }

    fun hide() {
        if (!attached) return
        visible = false
        view.visibility = View.GONE
    }

    fun remove() {
        if (!attached) return
        runCatching { windowManager.removeView(view) }
        attached = false
        visible = false
    }

    fun updateRecording(elapsedSeconds: Int, maxSeconds: Int) {
        expandedMode = OverlayMode.Panel
        view.setRecordingActive(true)
        view.updateRecording(elapsedSeconds, maxSeconds)
        if (mode == OverlayMode.Panel) setMode(OverlayMode.Panel)
    }

    fun showMatch(title: String, artist: String, confidence: Double) {
        expandedMode = OverlayMode.Result
        view.setRecordingActive(false)
        setMode(OverlayMode.Result)
        view.showMatch(title, artist, confidence)
    }

    fun showMessage(title: String, subtitle: String, isError: Boolean = false) {
        expandedMode = OverlayMode.Message
        view.setRecordingActive(false)
        setMode(OverlayMode.Message)
        view.showMessage(title, subtitle, isError)
    }

    private fun setMode(nextMode: OverlayMode, repositionBubble: Boolean = false) {
        if (!attached || !visible) {
            mode = nextMode
            return
        }
        mode = nextMode
        val width = if (nextMode == OverlayMode.Bubble) BUBBLE_SIZE_DP else OVERLAY_WIDTH_DP
        val height = if (nextMode == OverlayMode.Bubble) BUBBLE_SIZE_DP else OVERLAY_HEIGHT_DP
        params.width = dp(width)
        params.height = dp(height)
        if (nextMode == OverlayMode.Bubble && repositionBubble) {
            params.x = (screenWidth() - params.width - dp(8))
                .coerceAtLeast(0)
            params.y = params.y.coerceAtLeast(dp(64))
        } else if (nextMode != OverlayMode.Bubble) {
            params.x = ((screenWidth() - params.width) / 2)
                .coerceAtLeast(0)
            params.y = params.y.coerceIn(dp(64), dp(220))
        }
        view.setOverlayMode(nextMode)
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun snapBubbleToEdge() {
        if (!attached || mode != OverlayMode.Bubble) return
        val screenWidth = screenWidth()
        val margin = dp(8)
        val left = margin
        val right = (screenWidth - params.width - margin).coerceAtLeast(left)
        val target = if (params.x + params.width / 2 < screenWidth / 2) left else right
        params.x = target
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun dragWindow(dx: Float, dy: Float) {
        if (!attached) return
        val screenWidth = screenWidth()
        val screenHeight = appContext.resources.displayMetrics.heightPixels
        params.x = (params.x + dx.toInt()).coerceIn(0, (screenWidth - params.width).coerceAtLeast(0))
        params.y = (params.y + dy.toInt()).coerceIn(0, (screenHeight - params.height).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun dp(value: Int): Int =
        (value * appContext.resources.displayMetrics.density).toInt()

    private fun screenWidth(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.width()
        } else {
            appContext.resources.displayMetrics.widthPixels
        }

    private companion object {
        const val BUBBLE_SIZE_DP = 44
        const val OVERLAY_WIDTH_DP = 220
        const val OVERLAY_HEIGHT_DP = 96
    }
}

private enum class OverlayMode {
    Bubble,
    Idle,
    Panel,
    Result,
    Message,
}

private class RecognitionOverlayView(
    context: Context,
    private val onExpand: () -> Unit,
    private val onCollapse: () -> Unit,
    private val onStopRecognition: () -> Unit,
    private val onClose: () -> Unit,
    private val onOpenApp: () -> Unit,
    private val onStartRecognition: () -> Unit,
    private val onDrag: (Float, Float) -> Unit,
    private val onDragEnd: () -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val waveBars = FloatArray(9)
    private val closeRect = RectF()
    private val collapseRect = RectF()
    private val actionRect = RectF()
    private val stopRect = RectF()

    private var mode = OverlayMode.Panel
    private var elapsedSeconds = 0
    private var maxSeconds = 20
    private var songTitle = ""
    private var songArtist = ""
    private var confidence = 0.0
    private var messageTitle = ""
    private var messageSubtitle = ""
    private var isErrorMessage = false
    private var recordingActive = false
    private var animating = false
    private var lastFrameNanos = 0L
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var bubbleDragging = false
    private var panelDragging = false
    private var panelCanDrag = false
    private var pressedAction = Action.None

    private val frameRunnable = object : Runnable {
        override fun run() {
            val now = System.nanoTime()
            val deltaSeconds = if (lastFrameNanos == 0L) 0f else (now - lastFrameNanos) / 1_000_000_000f
            lastFrameNanos = now
            for (index in waveBars.indices) {
                val wave = (sin((now / 1_000_000_000.0 + index * 0.52) * 5.2) + 1.0) / 2.0
                val target = (0.18f + wave.toFloat() * 0.82f).coerceIn(0.16f, 1f)
                waveBars[index] += (target - waveBars[index]) * (deltaSeconds * 13f).coerceIn(0f, 1f)
            }
            invalidate()
            if (animating) postOnAnimation(this)
        }
    }

    init {
        contentDescription = context.getString(R.string.recognition_float_brand)
        waveBars.indices.forEach { waveBars[it] = 0.24f }
    }

    fun setOverlayMode(mode: OverlayMode) {
        this.mode = mode
        startOrStopAnimation()
        invalidate()
    }

    fun setRecordingActive(active: Boolean) {
        recordingActive = active
        startOrStopAnimation()
    }

    fun updateRecording(elapsedSeconds: Int, maxSeconds: Int) {
        this.elapsedSeconds = elapsedSeconds
        this.maxSeconds = maxSeconds
    }

    fun showMatch(title: String, artist: String, confidence: Double) {
        songTitle = title
        songArtist = artist
        this.confidence = confidence
        invalidate()
    }

    fun showMessage(title: String, subtitle: String, isError: Boolean) {
        messageTitle = title
        messageSubtitle = subtitle
        isErrorMessage = isError
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startOrStopAnimation()
    }

    override fun onDetachedFromWindow() {
        animating = false
        removeCallbacks(frameRunnable)
        super.onDetachedFromWindow()
    }

    private fun startOrStopAnimation() {
        val shouldAnimate = mode == OverlayMode.Panel || mode == OverlayMode.Bubble
        if (shouldAnimate == animating) return
        animating = shouldAnimate
        if (animating) {
            lastFrameNanos = 0L
            postOnAnimation(frameRunnable)
        } else {
            removeCallbacks(frameRunnable)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (mode) {
            OverlayMode.Bubble -> drawBubble(canvas)
            OverlayMode.Idle -> drawIdle(canvas)
            OverlayMode.Panel -> drawPanel(canvas)
            OverlayMode.Result -> drawResult(canvas)
            OverlayMode.Message -> drawMessage(canvas)
        }
    }

    private fun drawBubble(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = dp(19f)
        val pulse = ((sin(System.nanoTime() / 520_000_000.0) + 1.0) / 2.0).toFloat()
        fillPaint.shader = RadialGradient(
            cx,
            cy,
            radius * 1.6f,
            intArrayOf(
                ColorTokens.ROSE.withAlpha(0.28f + pulse * 0.10f),
                ColorTokens.ROSE.withAlpha(0f),
            ),
            floatArrayOf(0.42f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius * 1.6f, fillPaint)
        fillPaint.shader = LinearGradient(
            cx - radius,
            cy - radius,
            cx + radius,
            cy + radius,
            intArrayOf(ColorTokens.PANEL_START, ColorTokens.PANEL_END),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, fillPaint)
        fillPaint.shader = null
        strokePaint.color = ColorTokens.ROSE.withAlpha(0.72f)
        strokePaint.strokeWidth = dp(1.2f)
        canvas.drawCircle(cx, cy, radius, strokePaint)
        if (recordingActive) {
            drawBubbleWave(canvas, cx, cy, radius)
        } else {
            drawBubbleIdleGlyph(canvas, cx, cy)
        }
    }

    private fun drawBubbleIdleGlyph(canvas: Canvas, cx: Float, cy: Float) {
        buttonPaint.color = ColorTokens.TEXT
        buttonPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx - dp(7f), cy, dp(2.3f), buttonPaint)
        canvas.drawCircle(cx, cy, dp(2.3f), buttonPaint)
        canvas.drawCircle(cx + dp(7f), cy, dp(2.3f), buttonPaint)
    }

    private fun drawBubbleWave(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val barWidth = dp(2.4f)
        val gap = dp(3.2f)
        val totalWidth = barWidth * 5 + gap * 4
        var x = cx - totalWidth / 2f
        for (index in 0 until 5) {
            val wave = waveBars[(index * 2).coerceAtMost(waveBars.lastIndex)]
            val h = dp(5f) + radius * 0.7f * wave
            buttonPaint.color = ColorTokens.TEXT
            buttonPaint.strokeCap = Paint.Cap.ROUND
            buttonPaint.strokeWidth = barWidth
            canvas.drawLine(x + barWidth / 2f, cy - h / 2f, x + barWidth / 2f, cy + h / 2f, buttonPaint)
            x += barWidth + gap
        }
    }

    private fun drawPanel(canvas: Canvas) {
        drawCardBackground(canvas)
        drawHeader(canvas, context.getString(R.string.recognition_float_brand))
        drawText(
            canvas,
            context.getString(R.string.recognition_float_listening),
            dp(12f),
            dp(39f),
            dp(11.4f),
            ColorTokens.TEXT,
            boldPaint,
            width - dp(76f),
        )
        drawText(
            canvas,
            context.getString(R.string.recognition_float_time, elapsedSeconds, maxSeconds),
            dp(12f),
            dp(37f),
            dp(7.8f),
            ColorTokens.ROSE,
            boldPaint,
            width - dp(16f),
            alignRight = true,
        )
        drawWaveform(
            canvas = canvas,
            left = dp(12f),
            top = dp(46f),
            right = width - dp(70f),
            bottom = dp(66f),
            accent = ColorTokens.ROSE,
        )
        stopRect.set(width - dp(64f), height - dp(32f), width - dp(10f), height - dp(10f))
        drawPillButton(
            canvas,
            stopRect,
            context.getString(R.string.recognition_float_stop),
            ColorTokens.ROSE,
            ColorTokens.TEXT,
        )
    }

    private fun drawIdle(canvas: Canvas) {
        drawCardBackground(canvas)
        drawHeader(canvas, context.getString(R.string.recognition_float_idle_brand))
        drawText(
            canvas,
            context.getString(R.string.recognition_float_idle_title),
            dp(12f),
            dp(39f),
            dp(11.4f),
            ColorTokens.TEXT,
            boldPaint,
            width - dp(76f),
        )
        drawText(
            canvas,
            context.getString(R.string.recognition_float_idle_tip),
            dp(12f),
            dp(56f),
            dp(8.6f),
            ColorTokens.MUTED,
            textPaint,
            width - dp(76f),
        )
        actionRect.set(width - dp(64f), height - dp(32f), width - dp(10f), height - dp(10f))
        drawPillButton(
            canvas,
            actionRect,
            context.getString(R.string.recognition_float_idle_action),
            ColorTokens.ROSE,
            ColorTokens.TEXT,
        )
    }

    private fun drawResult(canvas: Canvas) {
        drawCardBackground(canvas)
        drawHeader(
            canvas,
            context.getString(R.string.recognition_float_result_title),
            ColorTokens.SUCCESS,
        )
        drawText(
            canvas,
            songTitle.ifBlank { context.getString(R.string.recognition_success) },
            dp(12f),
            dp(38f),
            dp(11.4f),
            ColorTokens.TEXT,
            boldPaint,
            width - dp(76f),
        )
        drawText(
            canvas,
            buildString {
                append(songArtist)
                if (confidence > 0.0) {
                    if (isNotEmpty()) append(" · ")
                    append((confidence * 100).toInt().coerceIn(0, 100))
                    append('%')
                }
            },
            dp(12f),
            dp(55f),
            dp(8.6f),
            ColorTokens.MUTED,
            textPaint,
            width - dp(76f),
        )
        actionRect.set(width - dp(66f), height - dp(32f), width - dp(10f), height - dp(10f))
        drawPillButton(
            canvas,
            actionRect,
            context.getString(R.string.recognition_float_open_app),
            ColorTokens.SUCCESS,
            ColorTokens.DARK_TEXT,
        )
    }

    private fun drawMessage(canvas: Canvas) {
        drawCardBackground(canvas)
        drawHeader(
            canvas,
            context.getString(R.string.recognition_float_brand),
            if (isErrorMessage) ColorTokens.ROSE else ColorTokens.SUCCESS,
        )
        drawText(
            canvas,
            messageTitle,
            dp(12f),
            dp(38f),
            dp(11.4f),
            if (isErrorMessage) ColorTokens.ROSE else ColorTokens.TEXT,
            boldPaint,
            width - dp(76f),
        )
        drawText(
            canvas,
            messageSubtitle,
            dp(12f),
            dp(55f),
            dp(8.6f),
            ColorTokens.MUTED,
            textPaint,
            width - dp(76f),
        )
        actionRect.set(width - dp(62f), height - dp(32f), width - dp(10f), height - dp(10f))
        drawPillButton(
            canvas,
            actionRect,
            context.getString(R.string.recognition_float_close),
            ColorTokens.PANEL_BUTTON,
            ColorTokens.TEXT,
        )
    }

    private fun drawCardBackground(canvas: Canvas) {
        val rect = RectF(dp(1f), dp(1f), width - dp(1f), height - dp(1f))
        fillPaint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            intArrayOf(ColorTokens.PANEL_START, ColorTokens.PANEL_END),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(rect, dp(16f), dp(16f), fillPaint)
        fillPaint.shader = null
        strokePaint.color = ColorTokens.ROSE.withAlpha(0.38f)
        strokePaint.strokeWidth = dp(1f)
        canvas.drawRoundRect(rect, dp(16f), dp(16f), strokePaint)
        fillPaint.shader = RadialGradient(
            width * 0.86f,
            dp(10f),
            width * 0.52f,
            intArrayOf(ColorTokens.ROSE.withAlpha(0.17f), ColorTokens.ROSE.withAlpha(0f)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(rect, dp(16f), dp(16f), fillPaint)
        fillPaint.shader = null
    }

    private fun drawHeader(
        canvas: Canvas,
        title: String,
        accent: Int = ColorTokens.ROSE,
    ) {
        buttonPaint.color = accent
        buttonPaint.style = Paint.Style.FILL
        canvas.drawCircle(dp(11f), dp(14f), dp(2.2f), buttonPaint)
        drawText(
            canvas,
            title,
            dp(18f),
            dp(18f),
            dp(7.8f),
            accent,
            boldPaint,
            width - dp(62f),
        )
        collapseRect.set(width - dp(58f), dp(2f), width - dp(32f), dp(28f))
        closeRect.set(width - dp(32f), dp(2f), width - dp(6f), dp(28f))
        drawWindowIcon(canvas, collapseRect, Icon.Minimize)
        drawWindowIcon(canvas, closeRect, Icon.Close)
    }

    private fun drawWindowIcon(canvas: Canvas, rect: RectF, icon: Icon) {
        buttonPaint.color = ColorTokens.MUTED.withAlpha(0.92f)
        buttonPaint.strokeWidth = dp(1.6f)
        buttonPaint.strokeCap = Paint.Cap.ROUND
        buttonPaint.style = Paint.Style.STROKE
        val cx = rect.centerX()
        val cy = rect.centerY()
        when (icon) {
            Icon.Minimize -> canvas.drawLine(cx - dp(5f), cy, cx + dp(5f), cy, buttonPaint)
            Icon.Close -> {
                canvas.drawLine(cx - dp(4.5f), cy - dp(4.5f), cx + dp(4.5f), cy + dp(4.5f), buttonPaint)
                canvas.drawLine(cx + dp(4.5f), cy - dp(4.5f), cx - dp(4.5f), cy + dp(4.5f), buttonPaint)
            }
        }
    }

    private fun drawWaveform(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        accent: Int,
    ) {
        val barWidth = dp(3f)
        val gap = dp(4f)
        val count = waveBars.size
        var x = left
        val centerY = (top + bottom) / 2f
        val maxHeight = bottom - top
        for (index in 0 until count) {
            val level = if (mode == OverlayMode.Panel) waveBars[index] else 0.42f
            val h = dp(4f) + maxHeight * level
            buttonPaint.color = accent.withAlpha(0.5f + level * 0.5f)
            buttonPaint.strokeCap = Paint.Cap.ROUND
            buttonPaint.strokeWidth = barWidth
            canvas.drawLine(x + barWidth / 2f, centerY - h / 2f, x + barWidth / 2f, centerY + h / 2f, buttonPaint)
            x += barWidth + gap
            if (x + barWidth > right) break
        }
    }

    private fun drawPillButton(
        canvas: Canvas,
        rect: RectF,
        label: String,
        background: Int,
        foreground: Int,
    ) {
        fillPaint.color = background
        fillPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, fillPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = dp(8.2f)
        textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textPaint.color = foreground
        val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, rect.centerX(), baseline, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        textSizeDp: Float,
        color: Int,
        paint: Paint,
        maxWidth: Float,
        alignRight: Boolean = false,
    ) {
        // Callers already convert the logical text size to pixels with dp().
        paint.textSize = textSizeDp
        paint.color = color
        paint.textAlign = if (alignRight) Paint.Align.RIGHT else Paint.Align.LEFT
        val widthLimit = (maxWidth - x).coerceAtLeast(dp(20f))
        val measurePaint = TextPaint(paint)
        val shown = TextUtils.ellipsize(
            text,
            measurePaint,
            widthLimit,
            TextUtils.TruncateAt.END,
        )
        canvas.drawText(shown.toString(), if (alignRight) x + widthLimit else x, baseline, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                bubbleDragging = false
                panelDragging = false
                pressedAction = hitAction(event.x, event.y)
                panelCanDrag = mode != OverlayMode.Bubble && pressedAction == Action.None
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (mode == OverlayMode.Bubble) {
                    val totalDx = event.rawX - downRawX
                    val totalDy = event.rawY - downRawY
                    if (!bubbleDragging && abs(totalDx) + abs(totalDy) > touchSlop) {
                        bubbleDragging = true
                    }
                    if (bubbleDragging) {
                        onDrag(event.rawX - lastRawX, event.rawY - lastRawY)
                    }
                } else if (panelCanDrag) {
                    val totalDx = event.rawX - downRawX
                    val totalDy = event.rawY - downRawY
                    if (!panelDragging && abs(totalDx) + abs(totalDy) > touchSlop) {
                        panelDragging = true
                    }
                    if (panelDragging) {
                        onDrag(event.rawX - lastRawX, event.rawY - lastRawY)
                    }
                }
                lastRawX = event.rawX
                lastRawY = event.rawY
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (mode == OverlayMode.Bubble) {
                    if (bubbleDragging) {
                        onDragEnd()
                    } else {
                        onExpand()
                    }
                } else if (
                    !panelDragging &&
                    pressedAction != Action.None &&
                    hitAction(event.x, event.y) == pressedAction
                ) {
                    when (pressedAction) {
                        Action.Expand -> onExpand()
                        Action.Collapse -> onCollapse()
                        Action.Stop -> onStopRecognition()
                        Action.Close -> onClose()
                        Action.OpenApp -> onOpenApp()
                        Action.StartRecognition -> onStartRecognition()
                        Action.None -> Unit
                    }
                }
                if (pressedAction != Action.None) performClick()
                pressedAction = Action.None
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (bubbleDragging) onDragEnd()
                panelDragging = false
                panelCanDrag = false
                pressedAction = Action.None
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun hitAction(x: Float, y: Float): Action {
        if (mode == OverlayMode.Bubble) return Action.Expand
        if (closeRect.contains(x, y)) return Action.Close
        if (collapseRect.contains(x, y)) return Action.Collapse
        if (mode == OverlayMode.Panel && stopRect.contains(x, y)) return Action.Stop
        if (mode == OverlayMode.Idle && actionRect.contains(x, y)) return Action.StartRecognition
        if (mode == OverlayMode.Result && actionRect.contains(x, y)) return Action.OpenApp
        if (mode == OverlayMode.Message && actionRect.contains(x, y)) return Action.Close
        return Action.None
    }

    private fun dp(value: Float): Float = value * density

    private fun dp(value: Int): Float = value * density

    private fun Int.withAlpha(alpha: Float): Int {
        val alphaInt = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        return (this and 0x00FFFFFF) or (alphaInt shl 24)
    }

    private enum class Icon {
        Minimize,
        Close,
    }

    private enum class Action {
        None,
        Expand,
        Collapse,
        Close,
        Stop,
        OpenApp,
        StartRecognition,
    }

    private object ColorTokens {
        val ROSE = 0xFFFF7897.toInt()
        val SUCCESS = 0xFF68D7A5.toInt()
        val TEXT = 0xFFF7F2FA.toInt()
        val MUTED = 0xFFC3B9CF.toInt()
        val DARK_TEXT = 0xFF172019.toInt()
        val PANEL_START = 0xF21A1725.toInt()
        val PANEL_END = 0xF22B1724.toInt()
        const val PANEL_BUTTON = 0x3DFFFFFF
    }
}
