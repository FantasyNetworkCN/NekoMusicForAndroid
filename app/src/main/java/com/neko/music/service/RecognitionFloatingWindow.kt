package com.neko.music.service

import android.animation.ValueAnimator
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Build
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.neko.music.R
import com.neko.music.config.AppConfig
import com.neko.music.debug.NekoLog as Log
import com.neko.music.ui.theme.resolveAppDarkTheme
import kotlin.math.abs
import kotlin.math.sin

internal class RecognitionFloatingWindow(
    context: Context,
    private val onStopRecognition: () -> Unit,
    private val onClose: () -> Unit,
    private val onOpenApp: () -> Unit,
    private val onStartRecognition: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(WindowManager::class.java)
    private val params = WindowManager.LayoutParams(
        dp(56), dp(56),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        x = appContext.resources.displayMetrics.widthPixels
        y = dp(108)
    }
    private val view = RecognitionOverlayView(
        appContext,
        onExpand = { setMode(expandedMode) },
        onCollapse = { setMode(OverlayMode.Bubble); snapToEdge() },
        onAction = {
            when (mode) {
                OverlayMode.Panel -> onStopRecognition()
                OverlayMode.Result -> onOpenApp()
                else -> onStartRecognition()
            }
        },
        onClose = onClose,
        onDrag = ::dragWindow,
        onDragEnd = ::snapToEdge,
    )
    private var attached = false
    private var visible = false
    private var mode = OverlayMode.Bubble
    private var expandedMode = OverlayMode.Idle
    private var edgeAnimation: ValueAnimator? = null
    private val configurationListener = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            view.refreshAppearance()
            resizeAndClamp()
        }
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onLowMemory() = Unit
    }

    val isVisible: Boolean get() = attached && visible

    fun showBubble() {
        if (!attached) {
            applyPlacement(placement())
            if (runCatching { manager.addView(view, params) }.onFailure {
                    Log.w("RecognitionFloatingWindow", "无法显示识曲悬浮窗", it)
                }.isFailure) return
            attached = true
            appContext.registerComponentCallbacks(configurationListener)
        }
        visible = true
        view.visibility = View.VISIBLE
        setMode(OverlayMode.Bubble)
    }

    fun showIdle() {
        expandedMode = OverlayMode.Idle
        view.setRecordingActive(false)
        showBubble()
    }

    fun hide() {
        visible = false
        edgeAnimation?.cancel()
        view.visibility = View.GONE
    }

    fun remove() {
        edgeAnimation?.cancel()
        if (attached) {
            appContext.unregisterComponentCallbacks(configurationListener)
            runCatching { manager.removeView(view) }
        }
        attached = false
        visible = false
    }

    fun showRecording() {
        expandedMode = OverlayMode.Panel
        view.setRecordingActive(true)
        if (mode != OverlayMode.Bubble && mode != OverlayMode.Panel) setMode(OverlayMode.Panel)
    }

    fun showMatch(title: String, artist: String, confidence: Double) {
        expandedMode = OverlayMode.Result
        view.setRecordingActive(false)
        view.showMatch(title, artist, confidence)
        setMode(OverlayMode.Result)
    }

    fun showMessage(title: String, subtitle: String, isError: Boolean = false) {
        expandedMode = OverlayMode.Message
        view.setRecordingActive(false)
        view.showMessage(title, subtitle, isError)
        setMode(OverlayMode.Message)
    }

    private fun setMode(next: OverlayMode) {
        edgeAnimation?.cancel()
        mode = next
        view.setOverlayMode(next)
        view.refreshAppearance()
        resizeAndClamp()
    }

    private fun placement(x: Int = params.x, y: Int = params.y): RecognitionOverlayPlacement {
        val metrics = appContext.resources.displayMetrics
        val bounds: Rect
        val insets: android.graphics.Insets?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val window = manager.currentWindowMetrics
            bounds = window.bounds
            insets = window.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
        } else {
            bounds = Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
            insets = null
        }
        val bubble = mode == OverlayMode.Bubble
        val fontScale = appContext.resources.configuration.fontScale.coerceIn(1f, 1.5f)
        return recognitionOverlayPlacement(
            bounds.width(), bounds.height(), insets?.left ?: 0, insets?.top ?: dp(28),
            insets?.right ?: 0, insets?.bottom ?: dp(24),
            dp(if (bubble) 56 else 304), dp(if (bubble) 56 else (176 * fontScale).toInt()),
            x, y, dp(8),
        )
    }

    private fun applyPlacement(position: RecognitionOverlayPlacement) {
        params.x = position.x
        params.y = position.y
        params.width = position.width
        params.height = position.height
        if (attached) runCatching { manager.updateViewLayout(view, params) }
    }

    private fun resizeAndClamp() = applyPlacement(placement())

    private fun dragWindow(dx: Float, dy: Float) {
        edgeAnimation?.cancel()
        if (attached) applyPlacement(placement(params.x + dx.toInt(), params.y + dy.toInt()))
    }

    private fun snapToEdge() {
        if (!attached || mode != OverlayMode.Bubble) return
        val left = placement(x = Int.MIN_VALUE).x
        val right = placement(x = Int.MAX_VALUE).x
        val target = if (abs(params.x - left) < abs(params.x - right)) left else right
        edgeAnimation?.cancel()
        edgeAnimation = ValueAnimator.ofInt(params.x, target).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { if (attached && visible) applyPlacement(placement(x = it.animatedValue as Int)) }
            start()
        }
    }

    private fun dp(value: Int) = (value * appContext.resources.displayMetrics.density).toInt()
}

private enum class OverlayMode { Bubble, Idle, Panel, Result, Message }

private class RecognitionOverlayView(
    context: Context,
    private val onExpand: () -> Unit,
    private val onCollapse: () -> Unit,
    private val onAction: () -> Unit,
    private val onClose: () -> Unit,
    private val onDrag: (Float, Float) -> Unit,
    private val onDragEnd: () -> Unit,
) : FrameLayout(context) {
    private val accent = Color.rgb(230, 55, 71)
    private val density = resources.displayMetrics.density
    private val bubble = RecognitionSignalView(context)
    private val panel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val title = label(16f, bold = true)
    private val subtitle = label(12f)
    private val brand = label(11f, bold = true)
    private val footerHint = label(11f)
    private val action = label(13f, bold = true)
    private val minimize = ImageButton(context)
    private val close = ImageButton(context)
    private val signal = RecognitionSignalView(context)
    private var mode = OverlayMode.Bubble
    private var recording = false
    private var songTitle = ""
    private var songArtist = ""
    private var confidence = 0.0
    private var messageTitle = ""
    private var messageSubtitle = ""
    private var messageError = false
    private var dark = false
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var dragEligible = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        clipChildren = false
        addView(bubble, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        bubble.setOnClickListener { onExpand() }
        bubble.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        panel.setPadding(dp(14), dp(4), dp(8), dp(10))
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(brand, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        minimize.contentDescription = context.getString(R.string.recognition_float_collapse)
        close.contentDescription = context.getString(R.string.recognition_float_close)
        minimize.setOnClickListener { onCollapse() }
        close.setOnClickListener { onClose() }
        header.addView(minimize, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
        panel.addView(header, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        val body = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        body.addView(signal, LinearLayout.LayoutParams(dp(48), dp(48)))
        val copy = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(6), 0)
        }
        title.maxLines = 1
        subtitle.maxLines = 2
        copy.addView(title)
        copy.addView(subtitle, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
        body.addView(copy, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(body, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        val footer = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        footer.addView(footerHint, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        action.gravity = Gravity.CENTER
        action.minHeight = dp(48)
        action.setPadding(dp(16), 0, dp(16), 0)
        action.setOnClickListener { onAction() }
        action.isFocusable = true
        action.maxLines = 1
        footer.addView(action, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(48)).apply { marginEnd = dp(2) })
        panel.addView(footer, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        refreshAppearance()
        render()
    }

    fun setOverlayMode(mode: OverlayMode) { this.mode = mode; render() }
    fun setRecordingActive(active: Boolean) {
        if (recording == active) return
        recording = active
        render()
    }
    fun showMatch(title: String, artist: String, confidence: Double) {
        songTitle = title; songArtist = artist; this.confidence = confidence
    }
    fun showMessage(title: String, subtitle: String, isError: Boolean) {
        messageTitle = title; messageSubtitle = subtitle; messageError = isError
    }

    fun refreshAppearance() {
        val settings = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        dark = resolveAppDarkTheme(settings.getString(AppConfig.PrefConfig.KEY_THEME, AppConfig.PrefConfig.DEFAULT_THEME).orEmpty(), systemDark)
        val foreground = if (dark) Color.rgb(247, 247, 250) else Color.rgb(30, 31, 35)
        val muted = if (dark) Color.rgb(177, 179, 189) else Color.rgb(113, 115, 125)
        panel.background = rounded(if (dark) Color.rgb(34, 35, 42) else Color.WHITE, 22f)
        panel.elevation = dp(6).toFloat()
        panel.clipToOutline = true
        title.setTextColor(foreground)
        subtitle.setTextColor(muted)
        footerHint.setTextColor(muted)
        brand.setTextColor(muted)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        brand.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        footerHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        minimize.setImageDrawable(WindowIconDrawable(false, muted, density))
        close.setImageDrawable(WindowIconDrawable(true, muted, density))
        for (button in listOf(minimize, close)) {
            button.background = RippleDrawable(ColorStateList.valueOf(if (dark) 0x22FFFFFF else 0x11000000), null, rounded(Color.WHITE, 24f))
            button.setPadding(dp(15), dp(15), dp(15), dp(15))
        }
        action.setTextColor(Color.WHITE)
        action.background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), rounded(accent, 24f), null)
        signal.setDark(dark)
        bubble.setDark(dark)
        render()
    }

    private fun render() {
        bubble.visibility = if (mode == OverlayMode.Bubble) VISIBLE else GONE
        panel.visibility = if (mode == OverlayMode.Bubble) GONE else VISIBLE
        bubble.setActive(recording)
        signal.setActive(recording && mode == OverlayMode.Panel)
        bubble.contentDescription = context.getString(
            if (recording) R.string.recognition_float_bubble_listening else R.string.recognition_float_bubble_ready,
        )
        when (mode) {
            OverlayMode.Bubble -> Unit
            OverlayMode.Idle -> {
                brand.setText(R.string.music_recognition)
                title.setText(R.string.recognition_float_idle_title)
                subtitle.setText(R.string.recognition_float_enable_hint)
                action.setText(R.string.recognition_float_start)
                footerHint.setText(R.string.recognition_float_drag_hint)
            }
            OverlayMode.Panel -> {
                brand.setText(R.string.recognition_float_listening)
                title.setText(R.string.recognition_float_listening)
                subtitle.setText(R.string.recognition_recording_hint)
                footerHint.setText(R.string.recognition_float_drag_hint)
                action.setText(R.string.recognition_float_stop)
            }
            OverlayMode.Result -> {
                brand.setText(R.string.recognition_success)
                title.text = songTitle.ifBlank { context.getString(R.string.recognition_success) }
                subtitle.text = songArtist
                footerHint.text = context.getString(R.string.recognition_confidence, (confidence * 100).toInt().coerceIn(0, 100))
                action.setText(R.string.recognition_float_open_app)
            }
            OverlayMode.Message -> {
                brand.setText(if (messageError) R.string.recognition_float_error else R.string.music_recognition)
                title.text = messageTitle
                subtitle.text = messageSubtitle
                footerHint.setText(R.string.recognition_float_drag_hint)
                action.setText(R.string.recognition_action_retry)
            }
        }
    }

    // 仅接管达到阈值的拖动，按钮点击与读屏焦点仍由原生子控件处理。
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX; downY = event.rawY; lastX = downX; lastY = downY
                dragging = false
                dragEligible = mode == OverlayMode.Bubble || !isOnControl(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragEligible && abs(event.rawX - downX) + abs(event.rawY - downY) > touchSlop) {
                    dragging = true
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                if (dragEligible && !dragging && abs(event.rawX - downX) + abs(event.rawY - downY) > touchSlop) {
                    dragging = true
                }
                if (dragging) onDrag(event.rawX - lastX, event.rawY - lastY)
                lastX = event.rawX; lastY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) onDragEnd()
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun isOnControl(x: Float, y: Float): Boolean = listOf(minimize, close, action).any { control ->
        val rect = Rect()
        control.getDrawingRect(rect)
        offsetDescendantRectToMyCoords(control, rect)
        rect.contains(x.toInt(), y.toInt())
    }
    private fun label(size: Float, bold: Boolean = false) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        typeface = android.graphics.Typeface.create("sans-serif", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        ellipsize = TextUtils.TruncateAt.END
        maxLines = 1
        includeFontPadding = false
    }
    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun dp(value: Int) = (value * density).toInt()
    private fun dp(value: Float) = (value * density).toInt()
}

private class RecognitionSignalView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val accent = Color.rgb(230, 55, 71)
    private var active = false
    private var ticking = false
    private var dark = false
    private val frame = object : Runnable {
        override fun run() {
            invalidate()
            if (ticking) postDelayed(this, 33L)
        }
    }
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun setDark(dark: Boolean) { this.dark = dark; invalidate() }
    fun setActive(active: Boolean) { this.active = active; updateAnimation(); invalidate() }
    override fun onVisibilityAggregated(isVisible: Boolean) { super.onVisibilityAggregated(isVisible); updateAnimation() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); updateAnimation() }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation() }
    override fun onDetachedFromWindow() { ticking = false; removeCallbacks(frame); super.onDetachedFromWindow() }
    override fun getAccessibilityClassName(): CharSequence =
        if (isClickable) "android.widget.Button" else super.getAccessibilityClassName()
    private fun updateAnimation() {
        val animate = active && isAttachedToWindow && isShown && windowVisibility == VISIBLE
        if (ticking == animate) return
        ticking = animate
        removeCallbacks(frame)
        if (animate) postOnAnimation(frame)
    }
    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) * .45f
        paint.style = Paint.Style.FILL
        paint.color = if (dark) Color.rgb(46, 36, 42) else Color.rgb(255, 236, 239)
        canvas.drawCircle(cx, cy, r, paint)
        paint.color = accent
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = r * .12f
        if (active) {
            val phase = SystemClock.uptimeMillis() / 200.0
            repeat(5) { i ->
                val h = r * (.2f + .55f * abs(sin(phase + i * .8)).toFloat())
                val x = cx + (i - 2) * r * .27f
                canvas.drawLine(x, cy - h / 2, x, cy + h / 2, paint)
            }
        } else {
            canvas.drawCircle(cx - r * .19f, cy + r * .24f, r * .20f, paint)
            canvas.drawLine(cx - r * .02f, cy + r * .24f, cx - r * .02f, cy - r * .42f, paint)
            canvas.drawLine(cx - r * .02f, cy - r * .42f, cx + r * .34f, cy - r * .30f, paint)
        }
        if (active) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = resources.displayMetrics.density * 1.5f
            paint.alpha = 130
            canvas.drawCircle(cx, cy, r - paint.strokeWidth, paint)
            paint.alpha = 255
        }
    }
}

private class WindowIconDrawable(private val close: Boolean, private val color: Int, private val density: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = this@WindowIconDrawable.color
        strokeWidth = density * 1.6f
        strokeCap = Paint.Cap.ROUND
    }
    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val r = minOf(bounds.width(), bounds.height()) * .27f
        if (close) {
            canvas.drawLine(cx - r, cy - r, cx + r, cy + r, paint)
            canvas.drawLine(cx + r, cy - r, cx - r, cy + r, paint)
        } else canvas.drawLine(cx - r, cy, cx + r, cy, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
