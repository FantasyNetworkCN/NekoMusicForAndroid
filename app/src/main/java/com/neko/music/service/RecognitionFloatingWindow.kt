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
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.neko.music.data.api.MusicApi
import com.neko.music.ui.theme.resolveAppColorScheme
import com.neko.music.util.UrlConfig
import com.neko.music.util.recognitionCoverUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
        onExpand = { state.expand(); applyState() },
        onCollapse = ::collapse,
        onAction = {
            when (state.mode) {
                OverlayMode.Panel -> onStopRecognition()
                OverlayMode.Result -> onOpenApp()
                else -> onStartRecognition()
            }
        },
        onClose = onClose,
        onRetry = onStartRecognition,
        onDrag = ::dragWindow,
        onDragEnd = ::snapToEdge,
    )
    private var attached = false
    private var visible = false
    private val state = RecognitionOverlayState()
    private val imageScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var coverJob: Job? = null
    private var coverKey: String? = null
    private var edgeAnimation: ValueAnimator? = null
    private var modeAnimation: ValueAnimator? = null
    private var expandedFraction = 0f
    private val configurationListener = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            modeAnimation?.cancel()
            modeAnimation = null
            view.refreshAppearance()
            resizeAndClamp()
            expandedFraction = if (state.mode == OverlayMode.Bubble) 0f else 1f
            view.setExpansion(expandedFraction, placement(bubble = false))
        }
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onLowMemory() = Unit
    }

    val isVisible: Boolean get() = attached && visible

    private fun ensureVisible() {
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
    }

    fun showBubble() {
        ensureVisible()
        applyState()
    }

    fun collapse() {
        state.collapse()
        applyState()
    }

    fun showIdle() {
        state.stop()
        ensureVisible()
        applyState()
    }

    fun hide() {
        visible = false
        edgeAnimation?.cancel()
        modeAnimation?.cancel()
        modeAnimation = null
        view.visibility = View.GONE
    }

    fun remove() {
        edgeAnimation?.cancel()
        modeAnimation?.cancel()
        coverJob?.cancel()
        imageScope.cancel()
        if (attached) {
            appContext.unregisterComponentCallbacks(configurationListener)
            runCatching { manager.removeView(view) }
        }
        attached = false
        visible = false
    }

    fun showRecording() {
        state.start()
        ensureVisible()
        applyState()
    }

    fun showMatch(match: MusicApi.RecognitionMatch, expand: Boolean = true) {
        state.match(match)
        if (!expand) state.collapse()
        view.showMatch(match.music.title, match.music.artist, match.confidence)
        val url = recognitionCoverUrl(match.music)
        if (coverKey != url) {
            coverKey = url
            coverJob?.cancel()
            view.setCover(null)
            coverJob = imageScope.launch {
                val result = runCatching {
                    val loader = SingletonImageLoader.get(appContext)
                    val initial = loader.execute(
                        ImageRequest.Builder(appContext).data(url).size(dp(128)).build(),
                    )
                    val fallback = UrlConfig.getMusicCoverUrl(match.music.id)
                    if (initial !is SuccessResult && url != fallback) {
                        loader.execute(ImageRequest.Builder(appContext).data(fallback).size(dp(128)).build())
                    } else initial
                }.onFailure { Log.w("RecognitionFloatingWindow", "加载识曲封面失败", it) }.getOrNull()
                if (coverKey == url && result is SuccessResult) {
                    view.setCover(result.image.asDrawable(appContext.resources))
                }
            }
        }
        if (expand) ensureVisible()
        applyState()
    }

    fun showMessage(title: String, subtitle: String, isError: Boolean = false) {
        state.message()
        view.showMessage(title, subtitle, isError)
        ensureVisible()
        applyState()
    }

    private fun applyState() {
        edgeAnimation?.cancel()
        modeAnimation?.cancel()
        modeAnimation = null
        view.setRecordingActive(state.recording)
        view.setOverlayMode(state.mode)
        view.refreshAppearance()
        val from = RecognitionOverlayPlacement(params.x, params.y, params.width, params.height)
        var target = placement()
        val bubble = state.mode == OverlayMode.Bubble
        if (bubble) {
            val left = placement(x = Int.MIN_VALUE).x
            val right = placement(x = Int.MAX_VALUE).x
            target = placement(x = if (abs(params.x - left) < abs(params.x - right)) left else right)
        }
        val endFraction = if (bubble) 0f else 1f
        val panelBounds = placement(bubble = false)
        if (!attached || !visible || (from == target && expandedFraction == endFraction)) {
            applyPlacement(target)
            expandedFraction = endFraction
            view.setExpansion(expandedFraction, panelBounds)
            return
        }
        val initialFraction = expandedFraction
        view.setExpansion(expandedFraction, panelBounds)
        modeAnimation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                if (attached && visible) {
                    val progress = it.animatedValue as Float
                    expandedFraction = initialFraction + (endFraction - initialFraction) * progress
                    applyPlacement(interpolateRecognitionOverlay(from, target, progress))
                    view.setExpansion(expandedFraction, panelBounds)
                }
            }
            start()
        }
    }

    private fun placement(
        x: Int = params.x,
        y: Int = params.y,
        bubble: Boolean = state.mode == OverlayMode.Bubble,
    ): RecognitionOverlayPlacement {
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
        modeAnimation?.cancel()
        modeAnimation = null
        if (attached) applyPlacement(placement(params.x + dx.toInt(), params.y + dy.toInt()))
    }

    private fun snapToEdge() {
        if (!attached || state.mode != OverlayMode.Bubble) return
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

private typealias OverlayMode = RecognitionOverlayMode

private class RecognitionOverlayView(
    context: Context,
    private val onExpand: () -> Unit,
    private val onCollapse: () -> Unit,
    private val onAction: () -> Unit,
    private val onClose: () -> Unit,
    private val onRetry: () -> Unit,
    private val onDrag: (Float, Float) -> Unit,
    private val onDragEnd: () -> Unit,
) : FrameLayout(context) {
    private var accent = Color.rgb(230, 57, 80)
    private val density = resources.displayMetrics.density
    private val bubble = RecognitionSignalView(context)
    private val panel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val title = label(16f, bold = true)
    private val subtitle = label(12f)
    private val brand = label(11f, bold = true)
    private val footerHint = label(11f)
    private val action = label(13f, bold = true)
    private val retry = label(13f, bold = true)
    private val minimize = ImageButton(context)
    private val close = ImageButton(context)
    private val signal = RecognitionSignalView(context)
    private val cover = ImageView(context)
    private val bubbleCover = ImageView(context)
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
    private var expansion = 0f
    private var panelWidth = 0
    private var panelHeight = 0

    init {
        clipChildren = false
        addView(bubble, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(bubbleCover, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            setMargins(dp(4), dp(4), dp(4), dp(4))
        })
        bubbleCover.scaleType = ImageView.ScaleType.CENTER_CROP
        bubbleCover.background = rounded(Color.TRANSPARENT, 28f)
        bubbleCover.clipToOutline = true
        bubbleCover.contentDescription = null
        bubbleCover.setOnClickListener { onExpand() }
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
        val artwork = FrameLayout(context)
        artwork.addView(signal, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        cover.scaleType = ImageView.ScaleType.CENTER_CROP
        cover.background = rounded(Color.TRANSPARENT, 14f)
        cover.clipToOutline = true
        artwork.addView(cover, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        cover.contentDescription = null
        body.addView(artwork, LinearLayout.LayoutParams(dp(54), dp(54)))
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
        retry.gravity = Gravity.CENTER
        retry.setText(R.string.recognition_action_again)
        retry.isFocusable = true
        retry.setOnClickListener { onRetry() }
        footer.addView(retry, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(8) })
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
    fun setExpansion(fraction: Float, panelBounds: RecognitionOverlayPlacement) {
        expansion = fraction.coerceIn(0f, 1f)
        panelWidth = panelBounds.width
        panelHeight = panelBounds.height
        val panelParams = panel.layoutParams
        if (panelParams.width != panelWidth || panelParams.height != panelHeight) {
            panelParams.width = panelWidth
            panelParams.height = panelHeight
            panel.layoutParams = panelParams
        }
        updateExpansion()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateExpansion()
    }

    private fun updateExpansion() {
        if (panelWidth <= 0 || panelHeight <= 0) return
        panel.pivotX = 0f
        panel.pivotY = 0f
        panel.scaleX = width.toFloat() / panelWidth
        panel.scaleY = height.toFloat() / panelHeight
        panel.alpha = ((expansion - .12f) / .88f).coerceIn(0f, 1f)
        panel.visibility = if (expansion > 0f) VISIBLE else GONE
        val showCover = !recording && songTitle.isNotBlank()
        val bubbleAlpha = (1f - expansion * 2f).coerceIn(0f, 1f)
        for (item in listOf(bubble, bubbleCover)) {
            val params = item.layoutParams as LayoutParams
            val side = dp(if (item === bubbleCover) 48 else 56)
            if (params.width != side || params.height != side || params.gravity != Gravity.CENTER) {
                params.width = side
                params.height = side
                params.setMargins(0, 0, 0, 0)
                params.gravity = Gravity.CENTER
                item.layoutParams = params
            }
            item.alpha = bubbleAlpha
            item.visibility = if (bubbleAlpha > 0f && ((item === bubbleCover) == showCover)) VISIBLE else GONE
        }
    }
    fun setRecordingActive(active: Boolean) {
        if (recording == active) return
        recording = active
        render()
    }
    fun setCover(drawable: Drawable?) {
        cover.setImageDrawable(drawable?.constantState?.newDrawable(resources)?.mutate()
            ?: drawable ?: context.getDrawable(R.drawable.music))
        bubbleCover.setImageDrawable(drawable?.constantState?.newDrawable(resources)?.mutate()
            ?: drawable ?: context.getDrawable(R.drawable.music))
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
        val colors = resolveAppColorScheme(context, dark, settings.getBoolean(AppConfig.PrefConfig.KEY_DYNAMIC_COLOR, true))
        accent = colors.primary.toArgb()
        val foreground = colors.onSurface.toArgb()
        val muted = colors.onSurfaceVariant.toArgb()
        val frosted = settings.getBoolean(AppConfig.PrefConfig.KEY_LIQUID_GLASS_HARDWARE_EFFECTS, true)
        panel.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
            colors.surfaceContainerHigh.copy(alpha = if (frosted) .96f else 1f).toArgb(),
            colors.surfaceContainer.copy(alpha = if (frosted) .94f else 1f).toArgb(),
        )).apply {
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), colors.outlineVariant.copy(alpha = .5f).toArgb())
        }
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
        action.setTextColor(colors.onPrimary.toArgb())
        action.background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), rounded(accent, 24f), null)
        retry.setTextColor(colors.onPrimaryContainer.toArgb())
        retry.background = RippleDrawable(ColorStateList.valueOf(0x22FFFFFF), rounded(colors.primaryContainer.toArgb(), 24f), null)
        signal.setColors(colors.primaryContainer.toArgb(), accent)
        bubble.setColors(colors.primaryContainer.toArgb(), accent)
        render()
    }

    private fun render() {
        val displayResult = mode == OverlayMode.Result
        retry.visibility = if (displayResult) VISIBLE else GONE
        footerHint.visibility = if (displayResult) GONE else VISIBLE
        val collapsedResult = mode == OverlayMode.Bubble && !recording && songTitle.isNotBlank()
        bubble.visibility = if (mode == OverlayMode.Bubble && !collapsedResult) VISIBLE else GONE
        bubbleCover.visibility = if (collapsedResult) VISIBLE else GONE
        cover.visibility = if (displayResult) VISIBLE else GONE
        signal.visibility = if (displayResult) GONE else VISIBLE
        bubbleCover.contentDescription = context.getString(R.string.recognition_float_result_description, songTitle, songArtist)
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
        updateExpansion()
    }

    // 仅接管达到阈值的拖动，按钮点击与读屏焦点仍由原生子控件处理。
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (expansion > 0f && expansion < 1f) return true
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
        if (expansion > 0f && expansion < 1f) return true
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

    private fun isOnControl(x: Float, y: Float): Boolean = listOf(minimize, close, action, retry).filter { it.visibility == VISIBLE }.any { control ->
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
    private var accent = Color.rgb(230, 57, 80)
    private var active = false
    private var ticking = false
    private var discColor = Color.rgb(255, 236, 239)
    private val frame = object : Runnable {
        override fun run() {
            invalidate()
            if (ticking) postDelayed(this, 33L)
        }
    }
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun setColors(background: Int, accent: Int) { discColor = background; this.accent = accent; invalidate() }
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
        paint.color = discColor
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
