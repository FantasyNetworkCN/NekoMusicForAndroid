package com.neko.music.floatwindow

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import com.neko.music.R
import com.neko.music.data.api.MusicApi
import com.neko.music.service.MusicPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FuckChinaOSFloatService : Service() {

    private var windowManager: WindowManager? = null
    private var floatView: View? = null
    private var isViewAdded = false
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var updateJob: Job? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    
    // 缓存当前显示的数据，避免不必要的更新
    private var cachedTitle: String? = null
    private var cachedArtist: String? = null
    private var cachedIsPlaying: Boolean? = null
    private var cachedCoverPath: String? = null
    
    // 歌词相关
    private var currentLyrics: List<com.neko.music.desktoplyric.LrcLine> = emptyList()
    private var currentMusicId: Int = -1
    
    // 拖动相关变量
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    
    // 位置保存相关的SharedPreferences
    private val positionPrefs by lazy {
        getSharedPreferences("float_window_position", Context.MODE_PRIVATE)
    }

    companion object {
        const val ACTION_SHOW = "com.neko.music.action.SHOW_FLOAT"
        const val ACTION_HIDE = "com.neko.music.action.HIDE_FLOAT"
        const val ACTION_UPDATE = "com.neko.music.action.UPDATE_FLOAT"
        private var instance: FuckChinaOSFloatService? = null
        
        fun getInstance(): FuckChinaOSFloatService? {
            return instance
        }
    }

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("FuckChinaOSFloatService", "Service onCreate")
        instance = this
        createFloatView()
        showFloatView()
        startProgressUpdate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showFloatView()
            ACTION_HIDE -> hideFloatView()
            ACTION_UPDATE -> updateFloatView()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createFloatView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatView = layoutInflater.inflate(R.layout.float_window_layout, null)

        // 设置点击事件
        val btnPlayPause = floatView?.findViewById<ImageButton>(R.id.float_play_pause)
        val btnPrevious = floatView?.findViewById<ImageButton>(R.id.float_previous)
        val btnNext = floatView?.findViewById<ImageButton>(R.id.float_next)
        val layoutFloat = floatView?.findViewById<android.widget.FrameLayout>(R.id.float_layout)
        val infoLayout = floatView?.findViewById<LinearLayout>(R.id.float_info_layout)
        val playAnimation = floatView?.findViewById<PlayAnimationView>(R.id.float_play_animation)
        val tvTitle = floatView?.findViewById<TextView>(R.id.float_title)
        val tvArtist = floatView?.findViewById<TextView>(R.id.float_artist)
        val tvLyric = floatView?.findViewById<TextView>(R.id.float_lyric)

        // 初始化 Marquee 效果，避免每次更新文本时重新设置导致频闪
        tvTitle?.isSelected = true
        tvArtist?.isSelected = true
        tvLyric?.isSelected = true

        btnPlayPause?.setOnClickListener {
            val playerManager = MusicPlayerManager.getInstance(this)
            if (playerManager.isPlaying.value) {
                playerManager.pause()
            } else {
                playerManager.togglePlayPause()
            }
        }

        btnPrevious?.setOnClickListener {
            MusicPlayerManager.getInstance(this).previous()
        }

        btnNext?.setOnClickListener {
            MusicPlayerManager.getInstance(this).next()
        }

        // 使用JNI实现拖动功能
        // 使用 DynamicIslandRenderer 进行高性能触摸处理
        try {
            com.neko.music.util.DynamicIslandRenderer.initialize()
        } catch (e: Exception) {
            android.util.Log.e("FuckChinaOSFloatService", "Failed to initialize JNI renderer", e)
        }

        layoutFloat?.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                try {
                    val action = when (event.action) {
                        MotionEvent.ACTION_DOWN -> com.neko.music.util.DynamicIslandRenderer.ACTION_DOWN
                        MotionEvent.ACTION_UP -> com.neko.music.util.DynamicIslandRenderer.ACTION_UP
                        MotionEvent.ACTION_MOVE -> com.neko.music.util.DynamicIslandRenderer.ACTION_MOVE
                        MotionEvent.ACTION_CANCEL -> com.neko.music.util.DynamicIslandRenderer.ACTION_CANCEL
                        else -> return false
                    }
                    
                    val result = com.neko.music.util.DynamicIslandRenderer.handleTouchEvent(
                        action, 
                        event.rawX, 
                        event.rawY
                    )
                    
                    when (result) {
                        com.neko.music.util.DynamicIslandRenderer.TouchResult.DRAGGING -> {
                            // 更新窗口位置
                            val (x, y) = com.neko.music.util.DynamicIslandRenderer.getPosition()
                            layoutParams?.x = x
                            layoutParams?.y = y
                            windowManager?.updateViewLayout(floatView, layoutParams)
                            return true
                        }
                        com.neko.music.util.DynamicIslandRenderer.TouchResult.DRAG_END -> {
                            // 拖动结束，更新最终位置并保存
                            val (x, y) = com.neko.music.util.DynamicIslandRenderer.getPosition()
                            layoutParams?.x = x
                            layoutParams?.y = y
                            windowManager?.updateViewLayout(floatView, layoutParams)
                            savePosition(x, y)
                            return true
                        }
                        com.neko.music.util.DynamicIslandRenderer.TouchResult.CLICK -> {
                            // 点击事件，返回false让子视图处理
                            return false
                        }
                        com.neko.music.util.DynamicIslandRenderer.TouchResult.NOT_HANDLED -> {
                            // 未处理，返回false让子视图处理
                            return false
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FuckChinaOSFloatService", "JNI touch handling error", e)
                    // JNI失败，使用备用逻辑
                    return false
                }
            }
        })
    }

    private fun showFloatView() {
        if (isViewAdded || floatView == null) return
        
        try {
            // 加载保存的位置，如果没有则使用JNI获取默认配置
            val savedPosition = loadPosition()
            val defaultPosition = if (savedPosition.first == -1) {
                try {
                    com.neko.music.util.DynamicIslandRenderer.getDefaultPosition()
                } catch (e: Exception) {
                    Pair(0, 80) // 默认值
                }
            } else {
                savedPosition
            }
            
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
            
            layoutParams?.gravity = Gravity.TOP or Gravity.START
            layoutParams?.x = defaultPosition.first
            layoutParams?.y = defaultPosition.second
            
            // 直接显示
            floatView?.alpha = 1f
            floatView?.scaleX = 1f
            floatView?.scaleY = 1f
            
            // 确保所有子视图都可见
            floatView?.findViewById<LinearLayout>(R.id.float_info_layout)?.visibility = View.VISIBLE
            floatView?.findViewById<android.widget.ImageView>(R.id.float_cover)?.visibility = View.VISIBLE
            floatView?.findViewById<ImageButton>(R.id.float_previous)?.visibility = View.VISIBLE
            floatView?.findViewById<ImageButton>(R.id.float_play_pause)?.visibility = View.VISIBLE
            floatView?.findViewById<ImageButton>(R.id.float_next)?.visibility = View.VISIBLE
            floatView?.findViewById<LinearLayout>(R.id.float_controls_layout)?.visibility = View.VISIBLE
            floatView?.findViewById<TextView>(R.id.float_lyric)?.visibility = View.VISIBLE
            
            windowManager?.addView(floatView, layoutParams)
            isViewAdded = true
            
            updateFloatView()
            android.util.Log.d("FuckChinaOSFloatService", "Float view added successfully at x=${defaultPosition.first}, y=${defaultPosition.second}")
        } catch (e: Exception) {
            android.util.Log.e("FuckChinaOSFloatService", "Error showing float view", e)
        }
    }

    private fun startProgressUpdate() {
        updateJob = serviceScope.launch {
            while (isActive) {
                updateFloatView()
                delay(500) // 每0.5秒更新一次
            }
        }
    }

    private fun hideFloatView() {
        if (!isViewAdded || floatView == null || windowManager == null) return
        
        try {
            // 保存当前位置
            savePosition(layoutParams?.x ?: 0, layoutParams?.y ?: 80)
            windowManager?.removeView(floatView)
            isViewAdded = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun savePosition(x: Int, y: Int) {
        positionPrefs.edit()
            .putInt("float_x_position", x)
            .putInt("float_y_position", y)
            .apply()
        android.util.Log.d("FuckChinaOSFloatService", "Saved position: x=$x, y=$y")
    }

    private fun loadPosition(): Pair<Int, Int> {
        val x = positionPrefs.getInt("float_x_position", -1)
        val y = positionPrefs.getInt("float_y_position", 80)
        return Pair(x, y)
    }

    private fun updateFloatView() {
        val playerManager = MusicPlayerManager.getInstance(this)
        
        val tvTitle = floatView?.findViewById<TextView>(R.id.float_title)
        val tvArtist = floatView?.findViewById<TextView>(R.id.float_artist)
        val tvLyric = floatView?.findViewById<TextView>(R.id.float_lyric)
        val btnPlayPause = floatView?.findViewById<ImageButton>(R.id.float_play_pause)
        val coverView = floatView?.findViewById<android.widget.ImageView>(R.id.float_cover)
        val playAnimation = floatView?.findViewById<PlayAnimationView>(R.id.float_play_animation)
        
        // 获取当前数据
        val currentTitle = playerManager.currentMusicTitle.value ?: "Neko云音乐"
        val currentArtist = playerManager.currentMusicArtist.value ?: "暂无播放"
        val currentIsPlaying = playerManager.isPlaying.value
        val currentCoverPath = playerManager.currentMusicCover.value
        val currentProgress = playerManager.currentPosition.value
        val currentTime = currentProgress / 1000f
        val musicId = playerManager.currentMusicId.value ?: -1
        
        // 只在数据变化时更新标题
        if (cachedTitle != currentTitle) {
            tvTitle?.text = currentTitle
            cachedTitle = currentTitle
        }
        
        // 只在数据变化时更新艺术家
        if (cachedArtist != currentArtist) {
            tvArtist?.text = currentArtist
            cachedArtist = currentArtist
        }
        
        // 只在数据变化时更新播放状态
        if (cachedIsPlaying != currentIsPlaying) {
            btnPlayPause?.setImageResource(
                if (currentIsPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
            )

            // 更新播放动画
            if (currentIsPlaying) {
                playAnimation?.visibility = View.VISIBLE
                playAnimation?.setPlaying(true)
            } else {
                playAnimation?.visibility = View.INVISIBLE
                playAnimation?.setPlaying(false)
            }
            cachedIsPlaying = currentIsPlaying
        }

        // 只在封面路径变化时更新封面
        if (cachedCoverPath != currentCoverPath) {
            if (currentCoverPath != null && currentCoverPath.isNotEmpty()) {
                if (currentCoverPath.startsWith("http")) {
                    // 网络URL，使用 Coil 加载
                    coverView?.load(currentCoverPath) {
                        placeholder(R.mipmap.ic_launcher)
                        error(R.mipmap.ic_launcher)
                        crossfade(true)
                    }
                } else {
                    // 本地路径，使用 Uri
                    try {
                        coverView?.setImageURI(android.net.Uri.parse(currentCoverPath))
                    } catch (e: Exception) {
                        coverView?.setImageResource(R.mipmap.ic_launcher)
                    }
                }
            } else {
                coverView?.setImageResource(R.mipmap.ic_launcher)
            }
            cachedCoverPath = currentCoverPath
        }
        
        // 如果音乐ID变化，重新加载歌词
        if (musicId != currentMusicId) {
            currentMusicId = musicId
            serviceScope.launch {
                loadLyrics(musicId)
            }
        }
        
        // 更新歌词显示
        if (currentLyrics.isNotEmpty()) {
            val currentLine = currentLyrics.lastOrNull { it.time <= currentTime }
            if (currentLine != null) {
                val currentIndex = currentLyrics.indexOf(currentLine)
                tvLyric?.text = currentLine.text

                // 同步到LyricScrollManager
                com.neko.music.util.LyricScrollManager.setCurrentLyricIndex(currentIndex, "float_window")
            } else {
                tvLyric?.text = ""
            }
        } else {
            tvLyric?.text = ""
        }
    }
    
    private suspend fun loadLyrics(musicId: Int) {
        if (musicId <= 0) {
            currentLyrics = emptyList()
            return
        }
        
        try {
            val musicApi = MusicApi(this)
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
                    currentLyrics = parseLrcLyrics(lyricsText)
                    android.util.Log.d("FuckChinaOSFloatService", "歌词加载成功，共 ${currentLyrics.size} 行")
                },
                onFailure = { error ->
                    android.util.Log.e("FuckChinaOSFloatService", "Failed to load lyrics", error)
                    currentLyrics = emptyList()
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("FuckChinaOSFloatService", "Error loading lyrics", e)
            currentLyrics = emptyList()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideFloatView()
        updateJob?.cancel()
        serviceScope.cancel()
        
        // 清理JNI资源
        try {
            com.neko.music.util.DynamicIslandRenderer.cleanup()
        } catch (e: Exception) {
            android.util.Log.e("FuckChinaOSFloatService", "Failed to cleanup JNI renderer", e)
        }
        
        instance = null
    }
}

// 辅助函数：解析LRC歌词
fun parseLrcLyrics(lrcText: String): List<com.neko.music.desktoplyric.LrcLine> {
    val lines = lrcText.lines()
    val result = mutableListOf<com.neko.music.desktoplyric.LrcLine>()
    var i = 0
    val timeRegex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\]")
    
    while (i < lines.size) {
        val line = lines[i]
        // 解析时间戳 [mm:ss.xx]
        val match = timeRegex.find(line)
        
        if (match != null) {
            val minutes = match.groupValues[1].toInt()
            val seconds = match.groupValues[2].toInt()
            val centiseconds = match.groupValues[3].toInt()
            val time = minutes * 60 + seconds + centiseconds / 100f
            
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
            
            result.add(com.neko.music.desktoplyric.LrcLine(time, text, translation))
            
            // 如果有翻译行，跳过它
            if (hasTranslation) {
                i++
            }
        }
        
        i++
    }
    
    return result
}