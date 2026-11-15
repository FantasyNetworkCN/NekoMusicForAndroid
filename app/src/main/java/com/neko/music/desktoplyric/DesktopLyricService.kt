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
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.neko.music.R
import com.neko.music.config.AppConfig
import com.neko.music.data.api.MusicApi
import com.neko.music.debug.NekoLog as Log
import com.neko.music.service.MusicPlayerManager
import com.neko.music.util.DesktopLyricRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

data class LrcLine(
    val time: Float, // 时间（秒）
    val text: String, // 歌词文本
    val translation: String? = null // 翻译（可选）
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
    
    // 使用JNI渲染器
    private var useJNIRenderer = true

    // 拖动相关变量
    private var startX = 0f
    private var startY = 0f
    private var isDragging = false
    private var viewInitialX = 0
    private var viewInitialY = 0
    private var touchSlop = 0f
    private var isLocked = false
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

        // 初始化JNI渲染器
        if (useJNIRenderer) {
            try {
                DesktopLyricRenderer.initialize()
                Log.d("DesktopLyricService", "JNI renderer initialized successfully")
            } catch (e: Exception) {
                Log.e("DesktopLyricService", "Failed to initialize JNI renderer, falling back to Kotlin", e)
                useJNIRenderer = false
            }
        }

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
    }

    private fun setLocked(locked: Boolean) {
        isLocked = locked
        positionPrefs.edit().putBoolean(KEY_LOCKED, locked).apply()
        updateLockUi()
        showControls()
        Toast.makeText(
            this,
            if (locked) R.string.desktop_lyric_locked else R.string.desktop_lyric_unlocked,
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun updateLockUi() {
        val lockButton = lyricView?.findViewById<ImageButton>(R.id.desktop_lyric_lock)
        lockButton?.setImageResource(
            if (isLocked) R.drawable.ic_desktop_lyric_lock else R.drawable.ic_desktop_lyric_unlock
        )
        lockButton?.contentDescription = getString(
            if (isLocked) R.string.desktop_lyric_unlock else R.string.desktop_lyric_lock
        )
        lyricView?.findViewById<ImageView>(R.id.desktop_lyric_lock_badge)?.visibility =
            if (isLocked) View.VISIBLE else View.GONE
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
            updatePlaybackControls(MusicPlayerManager.getInstance(this))
            lyricView?.post { clampPositionToScreen() }

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
                delay(1000) // 每1秒更新一次，减少频繁更新导致的surface断开问题
            }
        }
    }

    private fun hideLyricView() {
        if (!isViewAdded) return

        if (lyricView == null || windowManager == null) return

        try {
            cancelControlsAutoHide()
            hideControls()
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
            serviceScope.launch {
                loadLyrics(musicId)
            }
        }

        // 获取当前播放进度
        currentProgress = playerManager.currentPosition.value
        val currentTime = currentProgress / 1000f

        updatePlaybackControls(playerManager)

        // 使用原有的TextView更新歌词显示
        val tvLyric = lyricView?.findViewById<TextView>(R.id.desktop_lyric_text)
        val tvTranslation = lyricView?.findViewById<TextView>(R.id.desktop_lyric_translation)
        updateLyricViewPhone(tvLyric, tvTranslation, currentTime)
    }

    /**
     * 更新歌词显示
     */
    private fun updateLyricViewPhone(tvLyric: TextView?, tvTranslation: TextView?, currentTime: Float) {
        if (useJNIRenderer) {
            // 使用JNI渲染器
            try {
                val jsonData = DesktopLyricRenderer.getCurrentLyric(currentTime)
                val json = JSONObject(jsonData)
                val hasLyric = json.optBoolean("hasLyric", false)

                val newText = if (hasLyric) json.optString("text", "暂无歌词") else "暂无歌词"
                val newTranslation = if (hasLyric) json.optString("translation", "") else ""

                // 如果歌词内容变化，添加滚动动画效果
                val currentText = tvLyric?.text
                if (currentText != newText) {
                    // 保存当前文本（用于动画）
                    val previousText = currentText.toString()
                    val previousTranslation = tvTranslation?.text?.toString() ?: ""

                    // 临时显示上一句歌词，准备滚动动画
                    tvLyric?.text = previousText
                    tvTranslation?.text = previousTranslation

                    // 第一步：当前歌词向下滚动并淡出
                    lyricView?.animate()
                        ?.translationY(20f)
                        ?.alpha(0.3f)
                        ?.setDuration(150)
                        ?.withEndAction {
                            // 更新文本
                            tvLyric?.text = newText
                            tvTranslation?.text = newTranslation

                            // 重置位置到上方
                            lyricView?.translationY = -20f
                            lyricView?.alpha = 0.3f

                            // 第二步：新歌词从上方滚动进来并淡入
                            lyricView?.animate()
                                ?.translationY(0f)
                                ?.alpha(1f)
                                ?.setDuration(150)
                                ?.start()
                        }
                        ?.start()
                } else {
                    tvLyric.text = newText
                    tvTranslation?.text = newTranslation
                }
            } catch (e: Exception) {
                Log.e("DesktopLyricService", "JNI renderer error, falling back to Kotlin", e)
                useJNIRenderer = false
                // 回退到Kotlin实现
                updateLyricViewKotlin(tvLyric, tvTranslation, currentTime)
            }
        } else {
            // 使用Kotlin实现
            updateLyricViewKotlin(tvLyric, tvTranslation, currentTime)
        }
    }
    
    private fun updateLyricViewKotlin(tvLyric: TextView?, tvTranslation: TextView?, currentTime: Float) {
        val (lyricText, translationText) = getCurrentLyricKotlin(currentTime)
        tvLyric?.text = lyricText
        tvTranslation?.text = translationText
    }

    /**
     * 使用Kotlin实现获取当前歌词（用于HUD和回退）
     */
    private fun getCurrentLyricKotlin(currentTime: Float): Pair<String, String> {
        if (currentLyrics.isNotEmpty()) {
            val currentLine = currentLyrics.lastOrNull { it.time <= currentTime }

            if (currentLine != null) {
                return Pair(currentLine.text, currentLine.translation ?: "")
            }
        }
        return Pair("暂无歌词", "")
    }

    private suspend fun loadLyrics(musicId: Int) {
        if (musicId <= 0) {
            currentLyrics = emptyList()
            if (useJNIRenderer) {
                DesktopLyricRenderer.parseLyrics("", -1)
            }
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
            
            // 获取歌词
            val result = musicApi.getMusicLyrics(currentMusic)
            result.fold(
                onSuccess = { lyricsText ->
                    // 使用JNI渲染器
                    if (useJNIRenderer) {
                        try {
                            DesktopLyricRenderer.parseLyrics(lyricsText, musicId)
                            currentLyrics = emptyList() // 清空Kotlin数据
                        } catch (e: Exception) {
                            Log.e("DesktopLyricService", "JNI parse error, using Kotlin", e)
                            useJNIRenderer = false
                            currentLyrics = parseLrcLyrics(lyricsText)
                        }
                    } else {
                        currentLyrics = parseLrcLyrics(lyricsText)
                    }
                },
                onFailure = { error ->
                    Log.e("DesktopLyricService", "Failed to load lyrics", error)
                    currentLyrics = emptyList()
                    if (useJNIRenderer) {
                        DesktopLyricRenderer.parseLyrics("", -1)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("DesktopLyricService", "Error loading lyrics", e)
            currentLyrics = emptyList()
            if (useJNIRenderer) {
                DesktopLyricRenderer.parseLyrics("", -1)
            }
        }
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
                
                // 提取歌词文本
                var text = line.substring(match.range.last + 1).trim()
                
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
                
                result.add(LrcLine(time, text, translation))

                // 如果有翻译行，跳过它
                if (hasTranslation) {
                    i++
                }
            }

            i++
        }
        
        return result
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(longPressLockRunnable)
        cancelControlsAutoHide()
        hideLyricView()
        updateJob?.cancel()
        serviceScope.cancel()

        // 清理JNI渲染器
        if (useJNIRenderer) {
            try {
                DesktopLyricRenderer.cleanup()
            } catch (e: Exception) {
                Log.e("DesktopLyricService", "Error cleaning up JNI renderer", e)
            }
        }

        instance = null
    }
}
