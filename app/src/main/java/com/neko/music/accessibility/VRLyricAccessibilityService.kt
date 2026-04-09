package com.neko.music.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView
import com.neko.music.util.DeviceDetector

/**
 * VR歌词无障碍服务
 * 
 * 使用AccessibilityService实现真正的VR全局HUD
 * 
 * 技术原理：
 * - TYPE_ACCESSIBILITY_OVERLAY可以穿透VR合成器的限制
 * - FLAG_NOT_TOUCHABLE避免干扰VR交互
 * - 在VR游戏场景中保持显示
 * 
 * 注意：需要在系统设置中手动启用此服务
 */
class VRLyricAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var hudView: FrameLayout? = null
    private var lyricTextView: TextView? = null
    private var translationTextView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isViewAdded = false

    companion object {
        private const val TAG = "VRLyricAccessibility"
        private const val ACTION_UPDATE_LYRIC = "com.neko.music.action.UPDATE_LYRIC"
        private const val ACTION_SHOW_HUD = "com.neko.music.action.SHOW_HUD"
        private const val ACTION_HIDE_HUD = "com.neko.music.action.HIDE_HUD"
        
        private const val EXTRA_LYRIC = "lyric"
        private const val EXTRA_TRANSLATION = "translation"

        @Volatile
        private var instance: VRLyricAccessibilityService? = null

        fun getInstance(): VRLyricAccessibilityService? = instance

        fun isServiceEnabled(context: Context): Boolean {
            val enabledServices = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            val serviceName = "${context.packageName}/${VRLyricAccessibilityService::class.java.name}"
            return enabledServices?.contains(serviceName) == true
        }

        fun updateLyric(lyric: String, translation: String = "") {
            instance?.updateLyric(lyric, translation)
        }

        fun showHUD() {
            instance?.showHUD()
        }

        fun hideHUD() {
            instance?.hideHUD()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        android.util.Log.d(TAG, "VRLyricAccessibilityService created")

        if (DeviceDetector.isVRDevice()) {
            android.util.Log.d(TAG, "运行在VR设备中")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        android.util.Log.d(TAG, "VRLyricAccessibilityService connected")
        
        // 创建HUD视图
        createHUDView()
        // 显示HUD
        showHUD()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 监听系统事件，可以用于检测VR模式变化
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                android.util.Log.d(TAG, "Window state changed: ${event.className}")
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 可以检测到VR应用切换
            }
        }
    }

    override fun onInterrupt() {
        android.util.Log.d(TAG, "VRLyricAccessibilityService interrupted")
        hideHUD()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_UPDATE_LYRIC -> {
                    val lyric = intent.getStringExtra(EXTRA_LYRIC) ?: ""
                    val translation = intent.getStringExtra(EXTRA_TRANSLATION) ?: ""
                    updateLyric(lyric, translation)
                }
                ACTION_SHOW_HUD -> showHUD()
                ACTION_HIDE_HUD -> hideHUD()
            }
        }
        return START_STICKY
    }

    /**
     * 创建HUD视图
     * 使用TYPE_ACCESSIBILITY_OVERLAY实现全局显示
     */
    private fun createHUDView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        hudView = FrameLayout(this).apply {
            // 半透明黑色背景
            setBackgroundColor(android.graphics.Color.parseColor("#60000000"))
            setPadding(40, 24, 40, 24)
        }

        // 歌词文本
        lyricTextView = TextView(this).apply {
            textSize = 32f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(android.graphics.Color.WHITE)
            // 强烈的文字阴影，确保在VR环境中清晰可见
            setShadowLayer(12f, 0f, 0f, android.graphics.Color.BLACK)
            gravity = Gravity.CENTER
            maxWidth = 800
        }

        // 翻译文本
        translationTextView = TextView(this).apply {
            textSize = 22f
            setTypeface(Typeface.DEFAULT)
            setTextColor(android.graphics.Color.parseColor("#DDDDDD"))
            setShadowLayer(8f, 0f, 0f, android.graphics.Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 0)
            maxWidth = 800
        }

        hudView?.addView(lyricTextView)
        hudView?.addView(translationTextView)

        // 创建布局参数
        layoutParams = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT

            // 使用TYPE_ACCESSIBILITY_OVERLAY，这是少数能穿透VR合成器的窗口类型
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            // Accessibility窗口专用flag设置
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or          // 不获取焦点
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or          // 不响应触摸，避免干扰VR交互
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or        // 布局在整个屏幕
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or     // 硬件加速，提升性能
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or               // 全屏模式
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS           // 不受布局限制

            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 240 // 距离顶部的距离（VR环境中需要更大的距离）
        }
    }

    /**
     * 显示HUD
     */
    private fun showHUD() {
        if (isViewAdded || hudView == null) return

        try {
            windowManager?.addView(hudView, layoutParams)
            isViewAdded = true
            android.util.Log.d(TAG, "VR HUD view added successfully (AccessibilityService)")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error showing VR HUD view", e)
        }
    }

    /**
     * 隐藏HUD
     */
    private fun hideHUD() {
        if (!isViewAdded || hudView == null) return

        try {
            windowManager?.removeView(hudView)
            isViewAdded = false
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error hiding VR HUD view", e)
        }
    }

    /**
     * 更新歌词
     */
    private fun updateLyric(lyric: String, translation: String = "") {
        lyricTextView?.text = if (lyric.isEmpty()) "暂无歌词" else lyric
        translationTextView?.text = translation
        translationTextView?.visibility = if (translation.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    /**
     * 设置HUD位置
     */
    fun setPosition(y: Int) {
        layoutParams?.y = y
        if (isViewAdded) {
            windowManager?.updateViewLayout(hudView, layoutParams)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideHUD()
        instance = null
        android.util.Log.d(TAG, "VRLyricAccessibilityService destroyed")
    }
}