package com.neko.music.desktoplyric

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.neko.music.accessibility.VRLyricAccessibilityService
import com.neko.music.util.DeviceDetector
import com.neko.music.util.VRHUDRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * VR HUD歌词管理器
 * 专门为VR设备优化的歌词显示方案
 * 
 * 支持三种实现方式（按优先级排序）：
 * 1. AccessibilityService（真正的全局HUD）- 需要用户手动启用
 * 2. SurfaceControl全局HUD - 需要系统权限，部分设备支持
 * 3. 应用内HUD（WindowManager）- 始终可用
 * 
 * 工作原理：
 * - 启动时自动检测可用的HUD方案
 * - 优先使用最全局的方案
 * - 不支持时自动回退到次优方案
 * 
 * VR设备特性：
 * - AccessibilityService可以穿透VR合成器限制
 * - TYPE_ACCESSIBILITY_OVERLAY不响应触摸，避免干扰VR交互
 * - FLAG_HARDWARE_ACCELERATED提升渲染性能
 * - 在VR游戏场景中保持显示（AccessibilityService方案）
 */
class VRHUDLyricManager private constructor(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var hudView: FrameLayout? = null
    private var lyricTextView: TextView? = null
    private var translationTextView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isViewAdded = false

    private var updateJob: Job? = null
    private var currentLyric = ""
    private var currentTranslation = ""

    // HUD实现方式
    private enum class HUDMode {
        ACCESSIBILITY_SERVICE,  // 无障碍服务（真正的全局HUD）
        GLOBAL_NATIVE,          // 全局HUD（SurfaceControl，需要系统权限）
        APP_INTERNAL            // 应用内HUD（WindowManager）
    }

    private var hudMode: HUDMode = HUDMode.APP_INTERNAL

    companion object {
        @Volatile
        private var instance: VRHUDLyricManager? = null

        fun getInstance(context: Context): VRHUDLyricManager {
            return instance ?: synchronized(this) {
                instance ?: VRHUDLyricManager(context.applicationContext).also { instance = it }
            }
        }

        fun destroy() {
            instance?.hide()
            instance?.cleanup()
            instance = null
        }
    }

    init {
        // 检测可用的HUD方案
        detectAvailableHUDMode()
        // 创建HUD视图
        createHUDView()
    }

    /**
     * 检测可用的HUD方案
     */
    private fun detectAvailableHUDMode() {
        // 1. 检查AccessibilityService是否可用（最优先）
        if (VRLyricAccessibilityService.isServiceEnabled(context)) {
            hudMode = HUDMode.ACCESSIBILITY_SERVICE
            android.util.Log.d("VRHUDLyricManager", "Using AccessibilityService mode (true global HUD)")
            return
        }

        // 2. 检查SurfaceControl是否可用
        try {
            val globalHUDSupported = VRHUDRenderer.isGlobalHUDSupported()
            if (globalHUDSupported) {
                hudMode = HUDMode.GLOBAL_NATIVE
                android.util.Log.d("VRHUDLyricManager", "Using global HUD mode (SurfaceControl)")
                // 初始化全局HUD渲染器
                val displayMetrics = context.resources.displayMetrics
                VRHUDRenderer.initialize(context, displayMetrics.widthPixels, displayMetrics.heightPixels)
                return
            }
        } catch (e: Exception) {
            android.util.Log.w("VRHUDLyricManager", "Failed to check SurfaceControl support", e)
        }

        // 3. 回退到应用内HUD
        hudMode = HUDMode.APP_INTERNAL
        android.util.Log.d("VRHUDLyricManager", "Using app-internal HUD mode (WindowManager)")
    }

    /**
     * 检查AccessibilityService是否可用
     */
    fun isAccessibilityServiceAvailable(): Boolean {
        return VRLyricAccessibilityService.isServiceEnabled(context)
    }

    /**
     * 打开无障碍服务设置
     */
    fun openAccessibilitySettings() {
        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    /**
     * 创建HUD视图（仅应用内模式使用）
     */
    private fun createHUDView() {
        hudView = FrameLayout(context).apply {
            // 半透明黑色背景，增强歌词可读性
            setBackgroundColor(Color.parseColor("#60000000"))
            setPadding(40, 24, 40, 24)
        }

        // 歌词文本
        lyricTextView = TextView(context).apply {
            textSize = 32f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.WHITE)
            // 强烈的文字阴影，确保在VR环境中清晰可见
            setShadowLayer(12f, 0f, 0f, Color.BLACK)
            gravity = Gravity.CENTER
            maxWidth = 800
        }

        // 翻译文本
        translationTextView = TextView(context).apply {
            textSize = 22f
            setTypeface(Typeface.DEFAULT)
            setTextColor(Color.parseColor("#DDDDDD"))
            setShadowLayer(8f, 0f, 0f, Color.BLACK)
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

            // VR设备使用TYPE_APPLICATION_OVERLAY，不需要用户授权
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            // VR设备专用flag设置
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or          // 不获取焦点
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or          // 不响应触摸，避免干扰VR交互
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or        // 布局在整个屏幕
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or     // 硬件加速，提升性能
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or               // 全屏模式
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS           // 不受布局限制

            format = android.graphics.PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 240 // 距离顶部的距离（VR环境中需要更大的距离）
        }
    }

    /**
     * 显示HUD
     */
    fun show() {
        when (hudMode) {
            HUDMode.ACCESSIBILITY_SERVICE -> {
                // 使用AccessibilityService
                VRLyricAccessibilityService.showHUD()
                android.util.Log.d("VRHUDLyricManager", "AccessibilityService HUD shown")
            }
            HUDMode.GLOBAL_NATIVE -> {
                // 使用SurfaceControl全局HUD
                VRHUDRenderer.setVisible(true)
                android.util.Log.d("VRHUDLyricManager", "Global HUD shown (SurfaceControl)")
            }
            HUDMode.APP_INTERNAL -> {
                // 使用应用内HUD
                if (isViewAdded || hudView == null) return

                try {
                    windowManager.addView(hudView, layoutParams)
                    isViewAdded = true
                    startUpdateJob()
                    android.util.Log.d("VRHUDLyricManager", "App-internal HUD view added successfully")
                } catch (e: Exception) {
                    android.util.Log.e("VRHUDLyricManager", "Error showing app-internal HUD view", e)
                }
            }
        }
    }

    /**
     * 隐藏HUD
     */
    fun hide() {
        when (hudMode) {
            HUDMode.ACCESSIBILITY_SERVICE -> {
                // 隐藏AccessibilityService HUD
                VRLyricAccessibilityService.hideHUD()
                android.util.Log.d("VRHUDLyricManager", "AccessibilityService HUD hidden")
            }
            HUDMode.GLOBAL_NATIVE -> {
                // 隐藏SurfaceControl全局HUD
                VRHUDRenderer.setVisible(false)
                android.util.Log.d("VRHUDLyricManager", "Global HUD hidden")
            }
            HUDMode.APP_INTERNAL -> {
                // 隐藏应用内HUD
                if (!isViewAdded || hudView == null) return

                try {
                    updateJob?.cancel()
                    windowManager.removeView(hudView)
                    isViewAdded = false
                } catch (e: Exception) {
                    android.util.Log.e("VRHUDLyricManager", "Error hiding app-internal HUD view", e)
                }
            }
        }
    }

    /**
     * 更新歌词
     */
    fun updateLyric(lyric: String, translation: String = "") {
        currentLyric = lyric
        currentTranslation = translation

        when (hudMode) {
            HUDMode.ACCESSIBILITY_SERVICE -> {
                // 使用AccessibilityService
                VRLyricAccessibilityService.updateLyric(lyric, translation)
            }
            HUDMode.GLOBAL_NATIVE -> {
                // 使用SurfaceControl全局HUD
                VRHUDRenderer.updateLyric(lyric, translation)
            }
            HUDMode.APP_INTERNAL -> {
                // 使用应用内HUD
                lyricTextView?.text = if (lyric.isEmpty()) "暂无歌词" else lyric
                translationTextView?.text = translation
                translationTextView?.visibility = if (translation.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            }
        }
    }

    /**
     * 启动更新任务
     * VR环境需要更高的更新频率以保证流畅性
     */
    private fun startUpdateJob() {
        updateJob = scope.launch {
            while (isActive) {
                delay(50) // VR环境：50ms更新频率（20fps），保证流畅性
            }
        }
    }

    /**
     * 设置HUD位置
     */
    fun setPosition(y: Int) {
        when (hudMode) {
            HUDMode.ACCESSIBILITY_SERVICE -> {
                // AccessibilityService位置
                VRLyricAccessibilityService.getInstance()?.setPosition(y)
            }
            HUDMode.GLOBAL_NATIVE -> {
                // SurfaceControl全局HUD位置（0-1归一化坐标）
                val displayMetrics = context.resources.displayMetrics
                val normalizedY = y.toFloat() / displayMetrics.heightPixels
                VRHUDRenderer.setPosition(0.5f, normalizedY)
            }
            HUDMode.APP_INTERNAL -> {
                // 应用内HUD位置
                layoutParams?.y = y
                if (isViewAdded) {
                    windowManager.updateViewLayout(hudView, layoutParams)
                }
            }
        }
    }

    /**
     * 设置歌词大小
     */
    fun setLyricSize(size: Float) {
        if (hudMode == HUDMode.APP_INTERNAL) {
            lyricTextView?.textSize = size
            translationTextView?.textSize = size * 0.65f
        }
    }

    /**
     * 设置歌词颜色
     */
    fun setLyricColor(color: Int) {
        if (hudMode == HUDMode.APP_INTERNAL) {
            lyricTextView?.setTextColor(color)
        }
    }

    /**
     * 设置背景透明度
     */
    fun setBackgroundAlpha(alpha: Int) {
        if (hudMode == HUDMode.APP_INTERNAL) {
            val bgColor = Color.parseColor("#60000000")
            val newBgColor = Color.argb(alpha, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
            hudView?.setBackgroundColor(newBgColor)
        }
    }

    /**
     * 设置阴影强度
     */
    fun setShadowRadius(radius: Float) {
        if (hudMode == HUDMode.APP_INTERNAL) {
            lyricTextView?.setShadowLayer(radius, 0f, 0f, Color.BLACK)
            translationTextView?.setShadowLayer(radius * 0.7f, 0f, 0f, Color.BLACK)
        }
    }

    /**
     * 获取当前HUD模式
     */
    fun getHUDMode(): String {
        return when (hudMode) {
            HUDMode.ACCESSIBILITY_SERVICE -> "AccessibilityService (True Global HUD)"
            HUDMode.GLOBAL_NATIVE -> "Global HUD (SurfaceControl)"
            HUDMode.APP_INTERNAL -> "App-internal HUD (WindowManager)"
        }
    }

    /**
     * 检查是否支持全局HUD
     */
    fun isGlobalHUDSupported(): Boolean {
        return hudMode == HUDMode.ACCESSIBILITY_SERVICE || hudMode == HUDMode.GLOBAL_NATIVE
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        hide()
        if (hudMode == HUDMode.GLOBAL_NATIVE) {
            VRHUDRenderer.cleanup()
        }
    }
}