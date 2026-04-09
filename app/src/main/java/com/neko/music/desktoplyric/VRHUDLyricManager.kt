package com.neko.music.desktoplyric

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.TextView
import com.neko.music.R

/**
 * VR HUD歌词管理器
 * 支持双模式：
 * - VR模式：使用OpenXR在3D空间中显示
 * - 非VR模式：使用Android WindowManager Overlay
 * 
 * 实现方式：
 * - VR设备：自动使用OpenXR 3D空间渲染
 * - 非VR设备：使用Android Overlay API
 * 
 * 特性：
 * - VR环境中真正的3D空间定位
 * - 非VR环境中悬浮窗显示
 * - 支持自定义位置和大小
 */
class VRHUDLyricManager private constructor(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var lyricView: android.view.View? = null
    private var isViewAdded = false

    private var currentLyric = ""
    private var currentTranslation = ""

    // VR HUD渲染器
    private var vrHUDRenderer: com.neko.music.util.VRHUDRenderer? = null
    private var useVRMode = false

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
        initializeHUD()
    }

    /**
     * 初始化HUD
     */
    private fun initializeHUD() {
        // 检测设备类型
        useVRMode = com.neko.music.util.DeviceDetector.isVRDevice()
        
        if (useVRMode) {
            // VR设备：尝试使用OpenXR
            android.util.Log.d("VRHUDLyricManager", "Initializing VR HUD with OpenXR")
            vrHUDRenderer = com.neko.music.util.VRHUDRenderer
            
            val displayMetrics = context.resources.displayMetrics
            val success = vrHUDRenderer?.initialize(context, displayMetrics.widthPixels, displayMetrics.heightPixels) ?: false
            
            if (success) {
                // 检查是否真的支持3D空间HUD（OpenXR初始化成功）
                val spatialSupported = vrHUDRenderer?.isSpatialHUDSupported() ?: false
                if (spatialSupported) {
                    // 设置默认位置（用户前方2米）
                    vrHUDRenderer?.setInFront(2.0f, 0.0f)
                    android.util.Log.d("VRHUDLyricManager", "VR HUD initialized successfully with OpenXR")
                } else {
                    android.util.Log.d("VRHUDLyricManager", "VR device but 3D spatial HUD not supported, falling back to Overlay")
                    useVRMode = false
                    initializeOverlayHUD()
                }
            } else {
                android.util.Log.e("VRHUDLyricManager", "Failed to initialize VR HUD, falling back to Overlay")
                useVRMode = false
                initializeOverlayHUD()
            }
        } else {
            // 非VR设备：使用Overlay
            android.util.Log.d("VRHUDLyricManager", "Initializing Overlay HUD")
            initializeOverlayHUD()
        }
    }

    /**
     * 初始化Overlay HUD（非VR模式或VR模式回退）
     */
    private fun initializeOverlayHUD() {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val layoutInflater = LayoutInflater.from(context)
        lyricView = layoutInflater.inflate(R.layout.desktop_lyric_layout, null)
        
        // 配置View
        lyricView?.alpha = 0.95f
        lyricView?.scaleX = 1.2f
        lyricView?.scaleY = 1.2f
        
        android.util.Log.d("VRHUDLyricManager", "Overlay HUD initialized successfully")
    }

    /**
     * 检查3D空间HUD是否可用
     */
    fun is3DSpatialHUDAvailable(): Boolean {
        // VR设备应该都支持Overlay
        return com.neko.music.util.DeviceDetector.isVRDevice()
    }

    /**
     * 显示HUD
     */
    fun show() {
        if (useVRMode) {
            // VR模式：使用OpenXR
            vrHUDRenderer?.setVisible(true)
            android.util.Log.d("VRHUDLyricManager", "VR HUD shown with OpenXR")
            return
        }

        // Overlay模式
        if (isViewAdded) {
            android.util.Log.d("VRHUDLyricManager", "Overlay HUD already shown")
            return
        }

        if (lyricView == null || windowManager == null) {
            android.util.Log.e("VRHUDLyricManager", "Cannot show Overlay HUD - view or windowManager is null")
            return
        }

        try {
            val layoutParams = WindowManager.LayoutParams(
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

            layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutParams.y = 200 // 距离顶部200像素

            windowManager?.addView(lyricView, layoutParams)
            isViewAdded = true

            updateLyric(currentLyric, currentTranslation)
            android.util.Log.d("VRHUDLyricManager", "Overlay HUD shown successfully")
        } catch (e: Exception) {
            android.util.Log.e("VRHUDLyricManager", "Error showing Overlay HUD", e)
        }
    }

    /**
     * 隐藏HUD
     */
    fun hide() {
        if (useVRMode) {
            // VR模式：使用OpenXR
            vrHUDRenderer?.setVisible(false)
            android.util.Log.d("VRHUDLyricManager", "VR HUD hidden with OpenXR")
            return
        }

        // Overlay模式
        if (!isViewAdded) return

        if (lyricView == null || windowManager == null) return

        try {
            windowManager?.removeView(lyricView)
            isViewAdded = false
            android.util.Log.d("VRHUDLyricManager", "Overlay HUD hidden")
        } catch (e: Exception) {
            android.util.Log.e("VRHUDLyricManager", "Error hiding Overlay HUD", e)
        }
    }

    /**
     * 更新歌词
     */
    fun updateLyric(lyric: String, translation: String = "") {
        currentLyric = lyric
        currentTranslation = translation

        if (useVRMode) {
            // VR模式：更新OpenXR HUD
            vrHUDRenderer?.updateLyric(lyric, translation)
            android.util.Log.d("VRHUDLyricManager", "Updated VR lyric: $lyric")
            return
        }

        // Overlay模式
        if (!isViewAdded || lyricView == null) return

        val tvLyric = lyricView?.findViewById<TextView>(R.id.desktop_lyric_text)
        val tvTranslation = lyricView?.findViewById<TextView>(R.id.desktop_lyric_translation)

        tvLyric?.text = if (lyric.isEmpty()) "暂无歌词" else lyric
        tvTranslation?.text = translation
    }

    /**
     * 设置HUD位置
     */
    fun setPosition(x: Float, y: Float, z: Float) {
        if (useVRMode) {
            // VR模式：设置3D空间位置
            vrHUDRenderer?.setPosition(x, y, z)
            android.util.Log.d("VRHUDLyricManager", "Set VR position: ($x, $y, $z) meters")
        } else {
            // Overlay模式：不支持真正的3D定位
            android.util.Log.d("VRHUDLyricManager", "Overlay doesn't support 3D positioning")
        }
    }

    /**
     * 设置HUD在用户前方
     */
    fun setInFront(distance: Float = 2.0f, yOffset: Float = 0.0f) {
        if (useVRMode) {
            // VR模式：设置3D空间位置
            vrHUDRenderer?.setInFront(distance, yOffset)
            android.util.Log.d("VRHUDLyricManager", "Set VR in front: distance=$distance, yOffset=$yOffset")
        } else {
            // Overlay模式：可以调整垂直位置
            android.util.Log.d("VRHUDLyricManager", "Overlay Y position adjustment not implemented")
        }
    }

    /**
     * 设置HUD旋转
     */
    fun setRotation(w: Float, x: Float, y: Float, z: Float) {
        if (useVRMode) {
            // VR模式：设置3D旋转
            vrHUDRenderer?.setRotation(w, x, y, z)
            android.util.Log.d("VRHUDLyricManager", "Set VR rotation: ($w, $x, $y, $z)")
        } else {
            // Overlay模式：不支持旋转
            android.util.Log.d("VRHUDLyricManager", "Overlay doesn't support rotation")
        }
    }

    /**
     * 设置HUD尺寸
     */
    fun setSize(width: Float, height: Float) {
        if (useVRMode) {
            // VR模式：设置3D尺寸
            vrHUDRenderer?.setSize(width, height)
            android.util.Log.d("VRHUDLyricManager", "Set VR size: ${width}m x ${height}m")
        } else {
            // Overlay模式：通过scale改变尺寸
            lyricView?.scaleX = width
            lyricView?.scaleY = height
            android.util.Log.d("VRHUDLyricManager", "Set Overlay scale: ${width}x${height}")
        }
    }

    /**
     * 获取当前HUD模式
     */
    fun getHUDMode(): String {
        return if (useVRMode) {
            "OpenXR 3D Spatial HUD"
        } else {
            "Android Overlay"
        }
    }

    /**
     * 检查是否支持全局HUD
     */
    fun isGlobalHUDSupported(): Boolean {
        return is3DSpatialHUDAvailable()
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        hide()
        if (useVRMode) {
            vrHUDRenderer?.cleanup()
        }
        lyricView = null
        windowManager = null
    }
}