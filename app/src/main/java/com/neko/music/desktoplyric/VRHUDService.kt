package com.neko.music.desktoplyric

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
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import com.neko.music.R
import com.neko.music.data.api.MusicApi
import com.neko.music.service.MusicPlayerManager
import com.neko.music.util.DesktopLyricRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * VR HUD歌词服务 - 为VR场景优化的桌面歌词显示
 * 提供更好的视觉效果和性能优化
 */
class VRHUDService : Service() {

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
    
    // 上一句歌词数据
    private var previousText = ""
    private var previousTranslation = ""

    // 拖动相关变量
    private var startX = 0f
    private var startY = 0f
    private var isDragging = false
    private var viewInitialX = 0
    private var viewInitialY = 0
    private var touchSlop = 0f

    // 位置保存相关的SharedPreferences
    private val positionPrefs by lazy {
        getSharedPreferences("vr_hud_lyric_position", Context.MODE_PRIVATE)
    }

    companion object {
        const val ACTION_SHOW = "com.neko.music.action.SHOW_VR_HUD"
        const val ACTION_HIDE = "com.neko.music.action.HIDE_VR_HUD"
        const val ACTION_UPDATE = "com.neko.music.action.UPDATE_VR_HUD"
        private var instance: VRHUDService? = null
        
        fun getInstance(): VRHUDService? {
            return instance
        }
    }

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("VRHUDService", "Service onCreate")
        instance = this

        // 初始化touchSlop
        touchSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop.toFloat()

        // 初始化JNI渲染器
        if (useJNIRenderer) {
            try {
                DesktopLyricRenderer.initialize()
                android.util.Log.d("VRHUDService", "JNI renderer initialized successfully")
            } catch (e: Exception) {
                android.util.Log.e("VRHUDService", "Failed to initialize JNI renderer, falling back to Kotlin", e)
                useJNIRenderer = false
            }
        }

        // 创建VR HUD歌词视图
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

    private fun createLyricView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val layoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        lyricView = layoutInflater.inflate(R.layout.vr_hud_lyric_layout, null)
        
        // 添加拖动功能
        lyricView?.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        startY = event.rawY
                        isDragging = false
                        viewInitialX = layoutParams?.x ?: 0
                        viewInitialY = layoutParams?.y ?: 0
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - startX
                        val dy = event.rawY - startY
                        
                        if (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop) {
                            isDragging = true
                            layoutParams?.x = viewInitialX + dx.toInt()
                            layoutParams?.y = viewInitialY + dy.toInt()
                            windowManager?.updateViewLayout(lyricView, layoutParams)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        // 拖动结束，保存位置
                        if (isDragging) {
                            savePosition()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun showLyricView() {
        if (isViewAdded) return

        if (lyricView == null) return

        try {
            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
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

            layoutParams?.gravity = Gravity.CENTER

            // 加载保存的位置
            val savedPosition = loadPosition()
            layoutParams?.y = savedPosition

            lyricView?.alpha = 1f
            lyricView?.scaleX = 1f
            lyricView?.scaleY = 1f

            windowManager?.addView(lyricView, layoutParams)
            isViewAdded = true

            updateLyricView()
            android.util.Log.d("VRHUDService", "VR HUD view added successfully at y=$savedPosition")
        } catch (e: Exception) {
            android.util.Log.e("VRHUDService", "Error showing VR HUD view", e)
        }
    }

    private fun startLyricUpdate() {
        updateJob = serviceScope.launch {
            while (isActive) {
                updateLyricView()
                delay(500) // 每0.5秒更新一次，提供更流畅的VR体验
            }
        }
    }

    private fun hideLyricView() {
        if (!isViewAdded) return

        if (lyricView == null || windowManager == null) return

        try {
            // 保存当前位置
            savePosition()
            windowManager?.removeView(lyricView)
            isViewAdded = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun savePosition() {
        val y = layoutParams?.y ?: 100
        positionPrefs.edit().putInt("vr_hud_lyric_y_position", y).apply()
        android.util.Log.d("VRHUDService", "Saved position: y=$y")
    }

    private fun loadPosition(): Int {
        return positionPrefs.getInt("vr_hud_lyric_y_position", 0) // 默认居中
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

        // 获取VR HUD视图组件
        val tvCurrent = lyricView?.findViewById<TextView>(R.id.vr_hud_lyric_current)
        val tvTranslation = lyricView?.findViewById<TextView>(R.id.vr_hud_lyric_translation)
        val tvPrevious = lyricView?.findViewById<TextView>(R.id.vr_hud_lyric_previous)
        val tvNext = lyricView?.findViewById<TextView>(R.id.vr_hud_lyric_next)

        updateLyricViewVRHUD(tvCurrent, tvTranslation, tvPrevious, tvNext, currentTime)
    }

    /**
     * 更新VR HUD歌词显示 - 带有3D效果和流畅动画
     */
    private fun updateLyricViewVRHUD(
        tvCurrent: TextView?,
        tvTranslation: TextView?,
        tvPrevious: TextView?,
        tvNext: TextView?,
        currentTime: Float
    ) {
        if (useJNIRenderer) {
            // 使用JNI渲染器获取VR HUD数据
            try {
                val jsonData = DesktopLyricRenderer.getVRHUDContext(currentTime, 2)
                val jsonArray = JSONArray(jsonData)
                
                // 找到当前歌词的索引
                var currentIndex = -1
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    if (item.optBoolean("isCurrent", false)) {
                        currentIndex = i
                        break
                    }
                }
                
                if (currentIndex >= 0) {
                    // 获取当前歌词
                    val currentItem = jsonArray.getJSONObject(currentIndex)
                    val newText = currentItem.optString("text", "暂无歌词")
                    val newTranslation = currentItem.optString("translation", "")
                    
                    // 获取前一句歌词
                    val previousText = if (currentIndex > 0) {
                        jsonArray.getJSONObject(currentIndex - 1).optString("text", "")
                    } else ""
                    
                    // 获取后一句歌词
                    val nextText = if (currentIndex < jsonArray.length() - 1) {
                        jsonArray.getJSONObject(currentIndex + 1).optString("text", "")
                    } else ""
                    
                    // 更新显示
                    updateWithVRAnimation(tvCurrent, tvTranslation, tvPrevious, tvNext, 
                        newText, newTranslation, previousText, nextText)
                } else {
                    // 没有歌词
                    updateWithVRAnimation(tvCurrent, tvTranslation, tvPrevious, tvNext,
                        "暂无歌词", "", "", "")
                }
            } catch (e: Exception) {
                android.util.Log.e("VRHUDService", "JNI renderer error, falling back to Kotlin", e)
                useJNIRenderer = false
                // 回退到Kotlin实现
                updateLyricViewKotlin(tvCurrent, tvTranslation, tvPrevious, tvNext, currentTime)
            }
        } else {
            // 使用Kotlin实现
            updateLyricViewKotlin(tvCurrent, tvTranslation, tvPrevious, tvNext, currentTime)
        }
    }
    
    /**
     * 使用VR动画效果更新歌词
     */
    private fun updateWithVRAnimation(
        tvCurrent: TextView?,
        tvTranslation: TextView?,
        tvPrevious: TextView?,
        tvNext: TextView?,
        newText: String,
        newTranslation: String,
        previousText: String,
        nextText: String
    ) {
        val currentText = tvCurrent?.text?.toString() ?: ""
        
        // 如果歌词内容变化，添加VR动画效果
        if (currentText != newText) {
            // 保存上一句歌词
            this.previousText = currentText
            this.previousTranslation = tvTranslation?.text?.toString() ?: ""
            
            // 第一步：当前歌词缩放并淡出
            lyricView?.animate()
                ?.scaleX(0.8f)
                ?.scaleY(0.8f)
                ?.alpha(0.3f)
                ?.setDuration(200)
                ?.withEndAction {
                    // 更新文本
                    tvCurrent?.text = newText
                    tvTranslation?.text = newTranslation
                    tvPrevious?.text = previousText
                    tvNext?.text = nextText
                    
                    // 重置缩放和透明度
                    lyricView?.scaleX = 1.2f
                    lyricView?.scaleY = 1.2f
                    lyricView?.alpha = 0.3f
                    
                    // 第二步：新歌词从放大状态缩小并淡入
                    lyricView?.animate()
                        ?.scaleX(1f)
                        ?.scaleY(1f)
                        ?.alpha(1f)
                        ?.setDuration(200)
                        ?.setInterpolator(AccelerateDecelerateInterpolator())
                        ?.start()
                }
                ?.start()
        } else {
            // 歌词没有变化，只更新前后歌词
            tvPrevious?.text = previousText
            tvNext?.text = nextText
        }
    }
    
    /**
     * 使用Kotlin实现获取当前歌词（用于回退）
     */
    private fun updateLyricViewKotlin(
        tvCurrent: TextView?,
        tvTranslation: TextView?,
        tvPrevious: TextView?,
        tvNext: TextView?,
        currentTime: Float
    ) {
        val (lyricText, translationText) = getCurrentLyricKotlin(currentTime)
        tvCurrent?.text = lyricText
        tvTranslation?.text = translationText
        
        // 获取前后歌词
        val (prevText, nextText) = getPreviousAndNextLyrics(currentTime)
        tvPrevious?.text = prevText
        tvNext?.text = nextText
    }

    /**
     * 使用Kotlin实现获取当前歌词
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
    
    /**
     * 获取前后歌词
     */
    private fun getPreviousAndNextLyrics(currentTime: Float): Pair<String, String> {
        if (currentLyrics.isEmpty()) {
            return Pair("", "")
        }
        
        // 找到当前歌词的索引
        var currentIndex = -1
        for (i in currentLyrics.indices) {
            if (currentLyrics[i].time <= currentTime) {
                currentIndex = i
            } else {
                break
            }
        }
        
        val previousText = if (currentIndex > 0) {
            currentLyrics[currentIndex - 1].text
        } else ""
        
        val nextText = if (currentIndex >= 0 && currentIndex < currentLyrics.size - 1) {
            currentLyrics[currentIndex + 1].text
        } else ""
        
        return Pair(previousText, nextText)
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
                            android.util.Log.e("VRHUDService", "JNI parse error, using Kotlin", e)
                            useJNIRenderer = false
                            currentLyrics = parseLrcLyrics(lyricsText)
                        }
                    } else {
                        currentLyrics = parseLrcLyrics(lyricsText)
                    }
                },
                onFailure = { error ->
                    android.util.Log.e("VRHUDService", "Failed to load lyrics", error)
                    currentLyrics = emptyList()
                    if (useJNIRenderer) {
                        DesktopLyricRenderer.parseLyrics("", -1)
                    }
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("VRHUDService", "Error loading lyrics", e)
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
        hideLyricView()
        updateJob?.cancel()
        serviceScope.cancel()

        // 清理JNI渲染器
        if (useJNIRenderer) {
            try {
                DesktopLyricRenderer.cleanup()
            } catch (e: Exception) {
                android.util.Log.e("VRHUDService", "Error cleaning up JNI renderer", e)
            }
        }

        instance = null
    }
}