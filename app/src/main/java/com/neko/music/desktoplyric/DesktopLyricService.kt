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

    // VR HUD管理器
    private var vrHUDManager: VRHUDLyricManager? = null
    private var isVRDevice = false

    // 拖动相关变量
    private var startX = 0f
    private var startY = 0f
    private var isDragging = false
    private var viewInitialX = 0
    private var viewInitialY = 0
    private var touchSlop = 0f

    // 位置保存相关的SharedPreferences
    private val positionPrefs by lazy {
        getSharedPreferences("desktop_lyric_position", Context.MODE_PRIVATE)
    }

    companion object {
        const val ACTION_SHOW = "com.neko.music.action.SHOW_DESKTOP_LYRIC"
        const val ACTION_HIDE = "com.neko.music.action.HIDE_DESKTOP_LYRIC"
        const val ACTION_UPDATE = "com.neko.music.action.UPDATE_DESKTOP_LYRIC"
        private var instance: DesktopLyricService? = null
        
        fun getInstance(): DesktopLyricService? {
            return instance
        }
    }

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("DesktopLyricService", "Service onCreate")
        instance = this

        // 检测设备类型
        isVRDevice = com.neko.music.util.DeviceDetector.isVRDevice()
        android.util.Log.d("DesktopLyricService", "Device type: ${if (isVRDevice) "VR Headset" else "Normal Phone"}")
        android.util.Log.d("DesktopLyricService", com.neko.music.util.DeviceDetector.getDeviceInfo())

        // 在onCreate中初始化touchSlop，此时Context已经准备好
        touchSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        
        // 初始化JNI渲染器
        if (useJNIRenderer) {
            try {
                DesktopLyricRenderer.initialize()
                android.util.Log.d("DesktopLyricService", "JNI renderer initialized successfully")
            } catch (e: Exception) {
                android.util.Log.e("DesktopLyricService", "Failed to initialize JNI renderer, falling back to Kotlin", e)
                useJNIRenderer = false
            }
        }

        // 根据设备类型创建对应的视图
        if (isVRDevice) {
            // VR设备：使用HUD管理器
            vrHUDManager = VRHUDLyricManager.getInstance(this)
        } else {
            // 普通手机：使用原有的桌面歌词视图
            createLyricView()
        }

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
        lyricView = layoutInflater.inflate(R.layout.desktop_lyric_layout, null)
        
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

        if (isVRDevice) {
            // VR设备：使用HUD管理器
            vrHUDManager?.show()
            isViewAdded = true
            updateLyricView()
            android.util.Log.d("DesktopLyricService", "VR HUD shown successfully")
        } else {
            // 普通手机：使用原有的桌面歌词视图
            if ( lyricView == null) return

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

            layoutParams?.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

            // 加载保存的位置
            val savedPosition = loadPosition()
            layoutParams?.y = savedPosition

            lyricView?.alpha = 1f
            lyricView?.scaleX = 1f
            lyricView?.scaleY = 1f

            windowManager?.addView(lyricView, layoutParams)
            isViewAdded = true

            updateLyricView()
            android.util.Log.d("DesktopLyricService", "Lyric view added successfully at y=$savedPosition")
            } catch (e: Exception) {
                android.util.Log.e("DesktopLyricService", "Error showing lyric view", e)
            }
        }
    }

    private fun startLyricUpdate() {
        updateJob = serviceScope.launch {
            while (isActive) {
                updateLyricView()
                delay(500) // 每0.5秒更新一次
            }
        }
    }

    private fun hideLyricView() {
        if (!isViewAdded) return

        if (isVRDevice) {
            // VR设备：隐藏HUD
            vrHUDManager?.hide()
            isViewAdded = false
        } else {
            // 普通手机：移除原有视图
            if (lyricView == null || windowManager == null) return

            try {
                // 保存当前位置
            savePosition()
            windowManager?.removeView(lyricView)
            isViewAdded = false
        } catch (e: Exception) {
            e.printStackTrace()}
        }
    }

    private fun savePosition() {
        val y = layoutParams?.y ?: 100
        positionPrefs.edit().putInt("lyric_y_position", y).apply()
        android.util.Log.d("DesktopLyricService", "Saved position: y=$y")
    }

    private fun loadPosition(): Int {
        return positionPrefs.getInt("lyric_y_position", 100) // 默认距离顶部100像素
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

        // 根据设备类型更新歌词显示
        if (isVRDevice) {
            // VR设备：使用HUD管理器
            updateLyricViewVR(currentTime)
        } else {
            // 普通手机：使用原有的TextView
            val tvLyric = lyricView?.findViewById<TextView>(R.id.desktop_lyric_text)
            val tvTranslation = lyricView?.findViewById<TextView>(R.id.desktop_lyric_translation)
            updateLyricViewPhone(tvLyric, tvTranslation, currentTime)
        }
    }

    /**
     * 更新VR设备的歌词显示
     */
    private fun updateLyricViewVR(currentTime: Float) {
        var lyricText = ""
        var translationText = ""

        if (useJNIRenderer) {
            // 使用JNI渲染器
            try {
                val jsonData = DesktopLyricRenderer.getCurrentLyric(currentTime)
                val json = JSONObject(jsonData)
                val hasLyric = json.optBoolean("hasLyric", false)
if (hasLyric) {
                    lyricText = json.optString("text", "暂无歌词")
                    translationText = json.optString("translation", "")
                } else {
                    lyricText = "暂无歌词"
                    translationText = ""
                }
            } catch (e: Exception) {
                android.util.Log.e("DesktopLyricService", "JNI renderer error, falling back to Kotlin", e)
                useJNIRenderer = false
                // 回退到Kotlin实现
                val kotlinResult = getCurrentLyricKotlin(currentTime)
                lyricText = kotlinResult.first
                translationText = kotlinResult.second
            }
        } else {
            // 使用Kotlin实现
            val kotlinResult = getCurrentLyricKotlin(currentTime)
            lyricText = kotlinResult.first
            translationText = kotlinResult.second
        }

        // 更新HUD
        vrHUDManager?.updateLyric(lyricText, translationText)
    }

    /**
     * 更新普通手机的歌词显示
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
                    tvLyric?.text = newText
                    tvTranslation?.text = newTranslation
                }
            } catch (e: Exception) {
                android.util.Log.e("DesktopLyricService", "JNI renderer error, falling back to Kotlin", e)
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
                val newText = currentLine.text
                val newTranslation = currentLine.translation ?: ""

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
                    tvLyric?.text = newText
                    tvTranslation?.text = newTranslation
                }
            } else {
                tvLyric?.text = "暂无歌词"
                tvTranslation?.text = ""
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
                            android.util.Log.e("DesktopLyricService", "JNI parse error, using Kotlin", e)
                            useJNIRenderer = false
                            currentLyrics = parseLrcLyrics(lyricsText)
                        }
                    } else {
                        currentLyrics = parseLrcLyrics(lyricsText)
                    }
                },
                onFailure = { error ->
                    android.util.Log.e("DesktopLyricService", "Failed to load lyrics", error)
                    currentLyrics = emptyList()
                    if (useJNIRenderer) {
                        DesktopLyricRenderer.parseLyrics("", -1)
                    }
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("DesktopLyricService", "Error loading lyrics", e)
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

        // 清理VR HUD管理器
        if (isVRDevice) {
            VRHUDLyricManager.destroy()
            vrHUDManager = null
        }

        // 清理JNI渲染器
        if (useJNIRenderer) {
            try {
                DesktopLyricRenderer.cleanup()
            } catch (e: Exception) {
                android.util.Log.e("DesktopLyricService", "Error cleaning up JNI renderer", e)
            }
        }

        instance = null
    }
}
