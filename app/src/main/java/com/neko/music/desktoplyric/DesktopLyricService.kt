package com.neko.music.desktoplyric

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.neko.music.R
import com.neko.music.config.AppConfig
import com.neko.music.data.api.MusicApi
import com.neko.music.debug.NekoLog as Log
import com.neko.music.service.MusicPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private val syllableTimestampRegex = Regex("<(\\d{1,3}):(\\d{2})(?:\\.(\\d{1,5}))?>")

data class LrcLine(
    val time: Float, // 时间（秒）
    val text: String, // 歌词文本
    val translation: String? = null, // 翻译（可选）
    val romanization: String? = null, // 罗马音（可选）
    val syllables: List<LrcSyllable> = emptyList(), // 逐字时间轴（可选）
    val endTime: Float? = null, // 当前行结束时间（可选）
)

data class LrcSyllable(
    val time: Float,
    val text: String,
)

class DesktopLyricService : Service() {

    private var windowManager: WindowManager? = null
    private var lyricView: View? = null
    private var isViewAdded = false
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var updateJob: Job? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    
    // 歌词数据
    private var currentLyrics: List<LrcLine> = emptyList()
    private var currentProgress: Long = 0L
    private var currentMusicId: Int = -1
    private var lastRenderedLyricLine: LrcLine? = null
    private var lastReportedPositionMs: Long = -1L
    private var lastPositionSampleAtMs: Long = 0L
    private var wasPlaybackRunning = false

    private val lyricBaseColor = android.graphics.Color.argb(178, 255, 255, 255)
    private val karaokeHighlightColor = android.graphics.Color.rgb(255, 93, 162)

    // 拖动相关变量
    private var startX = 0f
    private var startY = 0f
    private var isDragging = false
    private var viewInitialX = 0
    private var viewInitialY = 0
    private var touchSlop = 0f
    private var isLocked = false
    private var unlockHotspotView: View? = null
    private var unlockHotspotParams: WindowManager.LayoutParams? = null
    private var isUnlockHotspotAdded = false
    private var controlsVisible = false
    private var controlsHideJob: Job? = null
    private var lastPlayingState: Boolean? = null
    private var longPressLockHandled = false
    private var dragLockHintShown = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val longPressLockRunnable = Runnable {
        if (!isDragging && !longPressLockHandled) {
            longPressLockHandled = true
            lyricView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            setLocked(!isLocked)
        }
    }

    // 位置保存相关的SharedPreferences
    private val positionPrefs by lazy {
        getSharedPreferences("desktop_lyric_position", Context.MODE_PRIVATE)
    }

    companion object {
        const val ACTION_SHOW = "com.neko.music.action.SHOW_DESKTOP_LYRIC"
        const val ACTION_HIDE = "com.neko.music.action.HIDE_DESKTOP_LYRIC"
        const val ACTION_UPDATE = "com.neko.music.action.UPDATE_DESKTOP_LYRIC"
        const val ACTION_UNLOCK = "com.neko.music.action.UNLOCK_DESKTOP_LYRIC"
        private const val PREFS_DESKTOP_LYRIC = "desktop_lyric"
        private const val KEY_X = "lyric_x_position"
        private const val KEY_Y = "lyric_y_position"
        private const val KEY_LOCKED = "lyric_locked"
        private const val CONTROLS_AUTO_HIDE_MS = 4_000L
        private var instance: DesktopLyricService? = null
        
        fun getInstance(): DesktopLyricService? {
            return instance
        }

    }

    override fun onCreate() {
        super.onCreate()
        Log.d("DesktopLyricService", "Service onCreate")
        instance = this

        // 在onCreate中初始化touchSlop，此时Context已经准备好
        touchSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        isLocked = positionPrefs.getBoolean(KEY_LOCKED, false)

        // 创建桌面歌词视图
        createLyricView()

        showLyricView()
        startLyricUpdate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showLyricView()
            ACTION_HIDE -> hideLyricView()
            ACTION_UPDATE -> updateLyricView()
            ACTION_UNLOCK -> setLocked(false)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createLyricView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val layoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        lyricView = layoutInflater.inflate(R.layout.desktop_lyric_layout, null)
        bindControlActions()
        lyricView?.setOnTouchListener { view, event -> handleLyricTouch(view, event) }
        updateLockUi()
    }

    private fun handleLyricTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mainHandler.removeCallbacks(longPressLockRunnable)
                cancelControlsAutoHide()
                startX = event.rawX
                startY = event.rawY
                isDragging = false
                longPressLockHandled = false
                dragLockHintShown = false
                viewInitialX = layoutParams?.x ?: 0
                viewInitialY = layoutParams?.y ?: 0
                mainHandler.postDelayed(
                    longPressLockRunnable,
                    android.view.ViewConfiguration.getLongPressTimeout().toLong(),
                )
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - startX
                val dy = event.rawY - startY
                val moved = kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop
                if (moved && !longPressLockHandled) {
                    mainHandler.removeCallbacks(longPressLockRunnable)
                    if (isLocked) {
                        if (!dragLockHintShown) {
                            dragLockHintShown = true
                            Toast.makeText(
                                this,
                                R.string.desktop_lyric_drag_locked,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } else {
                        isDragging = true
                        moveLyricView(dx, dy)
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(longPressLockRunnable)
                if (isDragging) {
                    savePosition()
                } else if (!longPressLockHandled) {
                    view.performClick()
                    toggleControls()
                }
                isDragging = false
                scheduleControlsAutoHide()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(longPressLockRunnable)
                isDragging = false
                scheduleControlsAutoHide()
                return true
            }
        }
        return false
    }

    private fun bindControlActions() {
        lyricView?.findViewById<ImageButton>(R.id.desktop_lyric_lock)?.setOnClickListener {
            setLocked(!isLocked)
            extendControlsVisibility()
        }
        lyricView?.findViewById<ImageButton>(R.id.desktop_lyric_previous)?.setOnClickListener {
            MusicPlayerManager.getInstance(this).previous()
            extendControlsVisibility()
        }
        lyricView?.findViewById<ImageButton>(R.id.desktop_lyric_play_pause)?.setOnClickListener {
            val playerManager = MusicPlayerManager.getInstance(this)
            playerManager.togglePlayPause()
            mainHandler.postDelayed(
                { updatePlaybackControls(playerManager) },
                250L,
            )
            extendControlsVisibility()
        }
        lyricView?.findViewById<ImageButton>(R.id.desktop_lyric_next)?.setOnClickListener {
            MusicPlayerManager.getInstance(this).next()
            extendControlsVisibility()
        }
        lyricView?.findViewById<ImageButton>(R.id.desktop_lyric_close)?.setOnClickListener {
            getSharedPreferences(PREFS_DESKTOP_LYRIC, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(AppConfig.PrefConfig.KEY_DESKTOP_LYRIC, false)
                .apply()
            hideLyricView()
        }
    }

    private fun moveLyricView(dx: Float, dy: Float) {
        val params = layoutParams ?: return
        val view = lyricView ?: return
        val metrics = resources.displayMetrics
        val viewWidth = view.width.coerceAtLeast(1)
        val viewHeight = view.height.coerceAtLeast(1)
        val maxHorizontalOffset = (metrics.widthPixels / 2 - viewWidth / 2).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - viewHeight).coerceAtLeast(0)

        params.x = (viewInitialX + dx.toInt()).coerceIn(-maxHorizontalOffset, maxHorizontalOffset)
        params.y = (viewInitialY + dy.toInt()).coerceIn(0, maxY)
        windowManager?.updateViewLayout(view, params)
    }

    private fun clampPositionToScreen() {
        val params = layoutParams ?: return
        val view = lyricView ?: return
        if (!isViewAdded) return
        val metrics = resources.displayMetrics
        val viewWidth = view.width.coerceAtLeast(1)
        val viewHeight = view.height.coerceAtLeast(1)
        val maxHorizontalOffset = (metrics.widthPixels / 2 - viewWidth / 2).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - viewHeight).coerceAtLeast(0)
        params.x = params.x.coerceIn(-maxHorizontalOffset, maxHorizontalOffset)
        params.y = params.y.coerceIn(0, maxY)
        windowManager?.updateViewLayout(view, params)
        syncUnlockHotspotPosition()
    }

    private fun setLocked(locked: Boolean) {
        isLocked = locked
        positionPrefs.edit().putBoolean(KEY_LOCKED, locked).apply()
        updateLockUi()
        applyWindowTouchability()
        if (locked) {
            hideControls()
            showUnlockHotspot()
        } else {
            hideUnlockHotspot()
            showControls()
        }
        Toast.makeText(
            this,
            if (locked) R.string.desktop_lyric_locked else R.string.desktop_lyric_unlocked,
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun applyWindowTouchability() {
        val params = layoutParams ?: return
        params.flags = if (isLocked) {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        if (isViewAdded) {
            lyricView?.let { windowManager?.updateViewLayout(it, params) }
        }
    }

    private fun showUnlockHotspot() {
        if (!isViewAdded || !isLocked) return
        val manager = windowManager ?: return
        val view = unlockHotspotView ?: createUnlockHotspotView().also {
            unlockHotspotView = it
        }
        if (!isUnlockHotspotAdded) {
            val params = unlockHotspotParams ?: return
            try {
                manager.addView(view, params)
                isUnlockHotspotAdded = true
            } catch (e: Exception) {
                Log.e("DesktopLyricService", "添加歌词解锁热区失败", e)
            }
        }
        lyricView?.post { syncUnlockHotspotPosition() }
    }

    private fun hideUnlockHotspot() {
        if (!isUnlockHotspotAdded) return
        try {
            unlockHotspotView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            Log.e("DesktopLyricService", "移除歌词解锁热区失败", e)
        }
        isUnlockHotspotAdded = false
    }

    private fun createUnlockHotspotView(): View {
        val density = resources.displayMetrics.density
        val size = (30f * density).toInt().coerceAtLeast(1)
        val margin = (4f * density).toInt()
        val view = ImageView(this).apply {
            setImageResource(R.drawable.ic_desktop_lyric_lock)
            setBackgroundResource(R.drawable.desktop_lyric_control_background)
            contentDescription = getString(R.string.desktop_lyric_unlock)
            alpha = 0.86f
            setPadding(margin * 2, margin * 2, margin * 2, margin * 2)
            isLongClickable = true
            setOnClickListener {
                Toast.makeText(
                    this@DesktopLyricService,
                    R.string.desktop_lyric_drag_locked,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            setOnLongClickListener {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                setLocked(false)
                true
            }
        }
        unlockHotspotParams = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        return view
    }

    private fun syncUnlockHotspotPosition() {
        if (!isUnlockHotspotAdded || !isLocked) return
        val mainParams = layoutParams ?: return
        val hotspotParams = unlockHotspotParams ?: return
        val mainView = lyricView ?: return
        val mainWidth = mainView.width.coerceAtLeast(1)
        val hotspotSize = hotspotParams.width.coerceAtLeast(1)
        val margin = (4f * resources.displayMetrics.density).toInt()
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        hotspotParams.x = mainParams.x + mainWidth / 2 - hotspotSize / 2 - margin
        hotspotParams.y = (mainParams.y + margin)
            .coerceIn(0, (screenHeight - hotspotSize).coerceAtLeast(0))
        hotspotParams.x = hotspotParams.x.coerceIn(
            -screenWidth / 2,
            screenWidth / 2 - hotspotSize,
        )
        unlockHotspotView?.let {
            try {
                windowManager?.updateViewLayout(it, hotspotParams)
            } catch (e: Exception) {
                Log.e("DesktopLyricService", "更新歌词解锁热区位置失败", e)
            }
        }
    }

    private fun updateLockUi() {
        val lockButton = lyricView?.findViewById<ImageButton>(R.id.desktop_lyric_lock)
        lockButton?.setImageResource(
            if (isLocked) R.drawable.ic_desktop_lyric_lock else R.drawable.ic_desktop_lyric_unlock
        )
        lockButton?.contentDescription = getString(
            if (isLocked) R.string.desktop_lyric_unlock else R.string.desktop_lyric_lock
        )
        // 锁定时主窗口完全穿透，锁定标识由独立的可触摸长按热区显示。
        lyricView?.findViewById<ImageView>(R.id.desktop_lyric_lock_badge)?.visibility = View.GONE
    }

    private fun updatePlaybackControls(playerManager: MusicPlayerManager) {
        val playing = playerManager.isPlaying.value
        if (playing == lastPlayingState) return
        lastPlayingState = playing
        lyricView?.findViewById<ImageButton>(R.id.desktop_lyric_play_pause)?.apply {
            setImageResource(
                if (playing) R.drawable.ic_desktop_lyric_pause else R.drawable.ic_desktop_lyric_play
            )
            contentDescription = getString(
                if (playing) R.string.desktop_lyric_pause else R.string.desktop_lyric_play
            )
        }
    }

    private fun toggleControls() {
        if (controlsVisible) hideControls() else showControls()
    }

    private fun showControls() {
        if (!isViewAdded) return
        val controls = lyricView?.findViewById<LinearLayout>(R.id.desktop_lyric_controls) ?: return
        controlsVisible = true
        controls.visibility = View.VISIBLE
        scheduleControlsAutoHide()
        lyricView?.post { clampPositionToScreen() }
    }

    private fun hideControls() {
        cancelControlsAutoHide()
        val controls = lyricView?.findViewById<LinearLayout>(R.id.desktop_lyric_controls) ?: return
        controlsVisible = false
        controls.visibility = View.GONE
        lyricView?.post { clampPositionToScreen() }
    }

    private fun extendControlsVisibility() {
        cancelControlsAutoHide()
        scheduleControlsAutoHide()
    }

    private fun scheduleControlsAutoHide() {
        cancelControlsAutoHide()
        if (!controlsVisible) return
        controlsHideJob = serviceScope.launch {
            delay(CONTROLS_AUTO_HIDE_MS)
            hideControls()
        }
    }

    private fun cancelControlsAutoHide() {
        controlsHideJob?.cancel()
        controlsHideJob = null
    }

    private fun showLyricView() {
        if (isViewAdded) return

        if (lyricView == null) return

        try {
            // 宽度用 WRAP_CONTENT，避免整屏横条挡住底部导航/手势区；仅歌词文字区域参与触摸
            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )

            layoutParams?.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

            // 加载保存的位置
            layoutParams?.x = positionPrefs.getInt(KEY_X, 0)
            layoutParams?.y = positionPrefs.getInt(KEY_Y, 100)

            lyricView?.alpha = 1f
            lyricView?.scaleX = 1f
            lyricView?.scaleY = 1f

            windowManager?.addView(lyricView, layoutParams)
            isViewAdded = true
            controlsVisible = false
            lyricView?.findViewById<LinearLayout>(R.id.desktop_lyric_controls)?.visibility = View.GONE
            updateLockUi()
            applyWindowTouchability()
            updatePlaybackControls(MusicPlayerManager.getInstance(this))
            lyricView?.post { clampPositionToScreen() }
            if (isLocked) {
                lyricView?.post { showUnlockHotspot() }
            }

            updateLyricView()
            Log.d(
                "DesktopLyricService",
                "Lyric view added successfully at x=${layoutParams?.x}, y=${layoutParams?.y}",
            )
        } catch (e: Exception) {
            Log.e("DesktopLyricService", "Error showing lyric view", e)
        }
    }

    private fun startLyricUpdate() {
        updateJob = serviceScope.launch {
            while (isActive) {
                updateLyricView()
                delay(33)
            }
        }
    }

    private fun hideLyricView() {
        if (!isViewAdded) return

        if (lyricView == null || windowManager == null) return

        try {
            cancelControlsAutoHide()
            hideControls()
            hideUnlockHotspot()
            // 保存当前位置
            savePosition()
            windowManager?.removeView(lyricView)
            isViewAdded = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun savePosition() {
        val x = layoutParams?.x ?: 0
        val y = layoutParams?.y ?: 100
        positionPrefs.edit()
            .putInt(KEY_X, x)
            .putInt(KEY_Y, y)
            .apply()
        Log.d("DesktopLyricService", "Saved position: x=$x, y=$y")
    }

    private fun updateLyricView() {
        val playerManager = MusicPlayerManager.getInstance(this)

        // 获取当前音乐ID
        val musicId = playerManager.currentMusicId.value ?: -1

        // 如果音乐ID变化，重新加载歌词
        if (musicId != currentMusicId) {
            currentMusicId = musicId
            lastReportedPositionMs = -1L
            lastRenderedLyricLine = null
            currentLyrics = emptyList()
            serviceScope.launch {
                loadLyrics(musicId)
            }
        }

        // 播放器位置约 10Hz 更新；桌面歌词在两次回调之间按墙钟补帧，避免逐字扫光卡顿。
        val reportedPosition = playerManager.currentPosition.value
        val now = SystemClock.elapsedRealtime()
        val playbackRunning = playerManager.isPlaying.value && !playerManager.isBuffering.value
        if (reportedPosition != lastReportedPositionMs || !wasPlaybackRunning) {
            lastReportedPositionMs = reportedPosition
            lastPositionSampleAtMs = now
        }
        val elapsed = if (playbackRunning) {
            (now - lastPositionSampleAtMs).coerceAtLeast(0L)
        } else {
            0L
        }
        wasPlaybackRunning = playbackRunning
        currentProgress = reportedPosition
        val currentTime = (reportedPosition + elapsed) / 1000f

        updatePlaybackControls(playerManager)

        val tvLyric = lyricView?.findViewById<TextView>(R.id.desktop_lyric_text)
        val tvLyricScroll = lyricView?.findViewById<HorizontalScrollView>(
            R.id.desktop_lyric_text_scroll
        )
        val tvRomanization = lyricView?.findViewById<TextView>(R.id.desktop_lyric_romanization)
        val tvTranslation = lyricView?.findViewById<TextView>(R.id.desktop_lyric_translation)
        updateLyricViewPhone(
            tvLyric,
            tvLyricScroll,
            tvRomanization,
            tvTranslation,
            currentTime,
        )
    }

    /**
     * 更新当前歌词、罗马音、翻译和逐字高亮。
     */
    private fun updateLyricViewPhone(
        tvLyric: TextView?,
        tvLyricScroll: HorizontalScrollView?,
        tvRomanization: TextView?,
        tvTranslation: TextView?,
        currentTime: Float,
    ) {
        val currentIndex = currentLyrics.indexOfLast { it.time <= currentTime }
        val currentLine = currentLyrics.getOrNull(currentIndex)
        val newText = currentLine?.text?.takeIf { it.isNotBlank() } ?: "暂无歌词"
        val textChanged = tvLyric?.text?.toString() != newText
        val lineChanged = lastRenderedLyricLine != currentLine

        if (textChanged) {
            val spannable = SpannableString(newText)
            tvLyric?.setText(spannable, TextView.BufferType.SPANNABLE)
            configureLyricViewport(tvLyric, tvLyricScroll, newText)
            lyricView?.animate()?.cancel()
            lyricView?.alpha = 0.58f
            lyricView?.translationY = 8f
            lyricView?.animate()
                ?.alpha(1f)
                ?.translationY(0f)
                ?.setDuration(180)
                ?.start()
        }

        if (lineChanged) {
            lastRenderedLyricLine = currentLine
            updateSecondaryLyricText(tvRomanization, currentLine?.romanization)
            updateSecondaryLyricText(tvTranslation, currentLine?.translation)
            val maxWidth = maxLyricWidthPx()
            tvRomanization?.maxWidth = maxWidth
            tvTranslation?.maxWidth = maxWidth
            lyricView?.post { clampPositionToScreen() }
        }

        if (currentLine != null && currentLine.text.isNotBlank()) {
            applyKaraokeSpans(
                tvLyric,
                tvLyricScroll,
                currentLine,
                currentIndex,
                currentTime,
            )
        }
    }

    private fun updateSecondaryLyricText(view: TextView?, value: String?) {
        val text = value?.trim().orEmpty()
        view?.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        if (text.isNotEmpty() && view?.text?.toString() != text) {
            view?.text = text
        }
    }

    private fun maxLyricWidthPx(): Int {
        val metrics = resources.displayMetrics
        return min(
            (metrics.widthPixels * 0.86f).toInt(),
            (420f * metrics.density).toInt(),
        ).coerceAtLeast((180f * metrics.density).toInt())
    }

    private fun configureLyricViewport(
        tvLyric: TextView?,
        tvLyricScroll: HorizontalScrollView?,
        text: String,
    ) {
        if (tvLyric == null || tvLyricScroll == null) return
        val contentWidth = (
            tvLyric.paint.measureText(text) +
                tvLyric.paddingLeft +
                tvLyric.paddingRight +
                2f * resources.displayMetrics.density
            ).toInt().coerceAtLeast(1)
        val viewportWidth = min(contentWidth, maxLyricWidthPx())
        tvLyricScroll.layoutParams = tvLyricScroll.layoutParams.apply {
            width = viewportWidth
        }
        tvLyricScroll.scrollTo(0, 0)
    }

    private fun updateHorizontalLyricScroll(
        tvLyric: TextView?,
        tvLyricScroll: HorizontalScrollView?,
        wordProgress: FloatArray?,
        lineProgress: Float,
    ) {
        if (tvLyric == null || tvLyricScroll == null) return
        val viewportWidth = tvLyricScroll.width
        val contentWidth = tvLyric.width
        if (viewportWidth <= 0 || contentWidth <= viewportWidth) {
            if (tvLyricScroll.scrollX != 0) tvLyricScroll.scrollTo(0, 0)
            return
        }
        val maxScroll = (contentWidth - viewportWidth).toFloat()
        val targetScroll = if (wordProgress != null && wordProgress.isNotEmpty()) {
            val layout = tvLyric.layout ?: return
            val lastSungIndex = wordProgress.indexOfLast { it > 0f }
            val targetOffset = (lastSungIndex.coerceAtLeast(0) + 1)
                .coerceAtMost(tvLyric.text.length)
            val characterX = layout.getPrimaryHorizontal(targetOffset)
            (characterX - viewportWidth * 0.70f).coerceIn(0f, maxScroll)
        } else {
            maxScroll * lineProgress.coerceIn(0f, 1f)
        }
        tvLyricScroll.scrollTo(targetScroll.roundToInt(), 0)
    }

    private fun applyKaraokeSpans(
        tvLyric: TextView?,
        tvLyricScroll: HorizontalScrollView?,
        line: LrcLine,
        currentIndex: Int,
        currentTime: Float,
    ) {
        val text = tvLyric?.text ?: return
        val spannable = text as? Spannable ?: SpannableString(text).also {
            tvLyric.setText(it, TextView.BufferType.SPANNABLE)
        }
        if (spannable.length != line.text.length) return

        spannable.getSpans(0, spannable.length, ForegroundColorSpan::class.java)
            .forEach(spannable::removeSpan)

        val endTime = line.endTime
            ?: currentLyrics.getOrNull(currentIndex + 1)?.time
            ?: (line.time + 4f)
        val lineProgress = ((currentTime - line.time) /
            (endTime - line.time).coerceAtLeast(0.2f)).coerceIn(0f, 1f)
        if (line.syllables.isEmpty()) {
            updateHorizontalLyricScroll(
                tvLyric,
                tvLyricScroll,
                wordProgress = null,
                lineProgress = lineProgress,
            )
            tvLyric.invalidate()
            return
        }

        val progress = calculateKaraokeProgress(line, currentTime, endTime)
        progress.forEachIndexed { index, value ->
            if (index < spannable.length) {
                spannable.setSpan(
                    ForegroundColorSpan(
                        if (value <= 0f) {
                            lyricBaseColor
                        } else {
                            blendColor(lyricBaseColor, karaokeHighlightColor, value)
                        }
                    ),
                    index,
                    index + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        updateHorizontalLyricScroll(
            tvLyric,
            tvLyricScroll,
            wordProgress = progress,
            lineProgress = lineProgress,
        )
        tvLyric.invalidate()
    }

    private fun calculateKaraokeProgress(
        line: LrcLine,
        currentTime: Float,
        endTime: Float,
    ): FloatArray {
        val length = line.text.length
        if (length == 0) return FloatArray(0)
        val progress = FloatArray(length)
        val safeEnd = endTime.coerceAtLeast(line.time + 0.2f)

        var offset = 0
        line.syllables.forEachIndexed { syllableIndex, syllable ->
            val start = min(offset, length)
            val end = min(offset + syllable.text.length, length)
            val syllableEnd = line.syllables.getOrNull(syllableIndex + 1)?.time ?: safeEnd
            val syllableProgress = ((currentTime - syllable.time) /
                (syllableEnd - syllable.time).coerceAtLeast(0.12f)).coerceIn(0f, 1f)
            val charCount = (end - start).coerceAtLeast(0)
            if (charCount > 0) {
                for (index in start until end) {
                    progress[index] = (
                        syllableProgress * charCount - (index - start)
                        ).coerceIn(0f, 1f)
                }
            }
            offset += syllable.text.length
        }
        if (offset < length) {
            val fallback = ((currentTime - line.time) /
                (safeEnd - line.time).coerceAtLeast(0.2f)).coerceIn(0f, 1f)
            for (index in offset until length) {
                progress[index] = fallback
            }
        }
        return progress
    }

    private fun blendColor(base: Int, highlight: Int, fraction: Float): Int {
        val value = fraction.coerceIn(0f, 1f)
        return android.graphics.Color.argb(
            (android.graphics.Color.alpha(base) +
                (android.graphics.Color.alpha(highlight) - android.graphics.Color.alpha(base)) * value)
                .roundToInt(),
            (android.graphics.Color.red(base) +
                (android.graphics.Color.red(highlight) - android.graphics.Color.red(base)) * value)
                .roundToInt(),
            (android.graphics.Color.green(base) +
                (android.graphics.Color.green(highlight) - android.graphics.Color.green(base)) * value)
                .roundToInt(),
            (android.graphics.Color.blue(base) +
                (android.graphics.Color.blue(highlight) - android.graphics.Color.blue(base)) * value)
                .roundToInt(),
        )
    }

    private suspend fun loadLyrics(musicId: Int) {
        if (musicId <= 0) {
            currentLyrics = emptyList()
            lastRenderedLyricLine = null
            return
        }
        
        try {
            val musicApi = MusicApi(this)
            
            // 从MusicPlayerManager获取当前音乐信息
            val playerManager = MusicPlayerManager.getInstance(this)
            
            // 构建一个简单的Music对象
            val currentMusic = com.neko.music.data.model.Music(
                id = musicId,
                title = playerManager.currentMusicTitle.value ?: "",
                artist = playerManager.currentMusicArtist.value ?: "",
                album = "",
                duration = 0,
                filePath = playerManager.currentMusicUrl.value,
                coverFilePath = playerManager.currentMusicCover.value
            )
            
            // 优先使用服务端字级歌词，同时保留普通 LRC 回退。
            val result = musicApi.getMusicLyricsPayload(currentMusic)
            result.fold(
                onSuccess = { payload ->
                    currentLyrics = if (payload.richLines.isNotEmpty()) {
                        payload.richLines.map { line ->
                            LrcLine(
                                time = line.startMs / 1000f,
                                text = cleanDesktopLyricText(line.text),
                                translation = cleanDesktopLyricText(line.translation)
                                    .takeIf { it.isNotEmpty() },
                                romanization = cleanDesktopLyricText(line.romanization)
                                    .takeIf { it.isNotEmpty() },
                                syllables = line.words.map { word ->
                                    LrcSyllable(
                                        time = word.startMs / 1000f,
                                        text = word.text,
                                    )
                                },
                                endTime = line.endMs
                                    .takeIf { it > line.startMs }
                                    ?.div(1000f),
                            )
                        }
                    } else {
                        parseLrcLyrics(payload.lyrics)
                    }
                    lastRenderedLyricLine = null
                    Log.d(
                        "DesktopLyricService",
                        "桌面歌词加载成功: lines=${currentLyrics.size}, " +
                            "rich=${payload.richLines.size}",
                    )
                },
                onFailure = { error ->
                    Log.e("DesktopLyricService", "Failed to load lyrics", error)
                    currentLyrics = emptyList()
                    lastRenderedLyricLine = null
                }
            )
        } catch (e: Exception) {
            Log.e("DesktopLyricService", "Error loading lyrics", e)
            currentLyrics = emptyList()
            lastRenderedLyricLine = null
        }
    }

    private fun cleanDesktopLyricText(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        return if (trimmed.equals("null", ignoreCase = true)) "" else trimmed
    }

    private fun parseLrcLyrics(lrcText: String): List<LrcLine> {
        val lines = lrcText.lines()
        val result = mutableListOf<LrcLine>()
        var i = 0
        val timeRegex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{1,5})\\]")

        while (i < lines.size) {
            val line = lines[i]
            // 解析时间戳 [mm:ss.xxx]（支持1-5位毫秒）
            val match = timeRegex.find(line)
            
            if (match != null) {
                val minutes = match.groupValues[1].toInt()
                val seconds = match.groupValues[2].toInt()
                val millisecondsStr = match.groupValues[3]

                // 根据毫秒位数计算时间
                val milliseconds = millisecondsStr.toFloat()
                val time = minutes * 60 + seconds + milliseconds / 1000f
                
                // 提取歌词文本和增强 LRC 的逐字时间戳
                val rawText = line.substring(match.range.last + 1).trim()
                val (text, syllables) = parseSyllableTimestamps(rawText, time)
                
                // 检查是否有翻译（下一行可能包含翻译）
                var translation: String? = null
                var hasTranslation = false
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim()
                    // 翻译行通常以 { } 包裹，且不包含时间戳
                    if (nextLine.startsWith("{") && nextLine.endsWith("}") && !timeRegex.containsMatchIn(nextLine)) {
                        hasTranslation = true
                        // 提取花括号内的内容
                        var content = nextLine.substring(1, nextLine.length - 1)
                        // 去掉转义字符和引号
                        content = content.replace("\\", "").replace("\"", "").replace("'", "")
                        translation = content.trim()
                    }
                }
                
                result.add(
                    LrcLine(
                        time = time,
                        text = text,
                        translation = translation,
                        syllables = syllables,
                    )
                )

                // 如果有翻译行，跳过它
                if (hasTranslation) {
                    i++
                }
            }

            i++
        }
        
        return result
    }

    private fun parseSyllableTimestamps(
        rawText: String,
        fallbackTime: Float,
    ): Pair<String, List<LrcSyllable>> {
        val matches = syllableTimestampRegex.findAll(rawText).toList()
        if (matches.isEmpty()) return rawText to emptyList()

        val syllables = mutableListOf<LrcSyllable>()
        val leadingText = rawText.substring(0, matches.first().range.first)
        if (leadingText.isNotBlank()) {
            syllables += LrcSyllable(fallbackTime, leadingText)
        }

        matches.forEachIndexed { index, match ->
            val textStart = match.range.last + 1
            val textEnd = matches.getOrNull(index + 1)?.range?.first ?: rawText.length
            val syllableText = rawText.substring(textStart, textEnd)
            if (syllableText.isEmpty()) return@forEachIndexed

            val minutes = match.groupValues[1].toInt()
            val seconds = match.groupValues[2].toInt()
            val fractionText = match.groupValues[3]
            val fraction = if (fractionText.isEmpty()) {
                0f
            } else {
                fractionText.toFloat() / 10f.pow(fractionText.length)
            }
            syllables += LrcSyllable(minutes * 60 + seconds + fraction, syllableText)
        }

        val displayText = syllables.joinToString(separator = "") { it.text }
        return if (displayText.isBlank()) {
            rawText.replace(syllableTimestampRegex, "").trim() to emptyList()
        } else {
            displayText to syllables
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(longPressLockRunnable)
        cancelControlsAutoHide()
        hideLyricView()
        updateJob?.cancel()
        serviceScope.cancel()

        instance = null
    }
}
