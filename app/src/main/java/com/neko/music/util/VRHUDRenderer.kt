package com.neko.music.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import java.nio.ByteBuffer

/**
 * VR HUD渲染器 - 使用Oculus SDK实现真正的3D空间HUD
 * 
 * 特性：
 * - 固定在VR空间中，不随头部移动而消失
 * - 支持自定义3D位置（X、Y、Z坐标）
 * - 支持自定义旋转（四元数）
 * - 支持自定义尺寸（宽度和高度，单位：米）
 * - 使用OVROverlay API创建真正的VR覆盖层
 * 
 * 坐标系：
 * - X轴：左右方向（正数向右）
 * - Y轴：上下方向（正数向上）
 * - Z轴：前后方向（负数向前）
 * 
 * 默认位置：前方2米，水平居中，视线高度
 */
object VRHUDRenderer {
    private const val TAG = "VRHUDRenderer"

    private var isInitialized = false
    private var isSpatialHUDSupported = false

    // 用于文本渲染的Bitmap
    private var hudBitmap: Bitmap? = null
    private var hudCanvas: Canvas? = null
    private var lyricPaint: Paint? = null
    private var translationPaint: Paint? = null

    // HUD的3D空间参数（单位：米）
    private var hudPosition = Triple(0.0f, 0.0f, -2.0f)  // (x, y, z)
    private var hudSize = Pair(1.0f, 0.5f)              // (width, height)
    private var hudRotation = Quaternion(0.0f, 0.0f, 0.0f, 1.0f)  // (qx, qy, qz, qw)

    /**
     * 四元数表示3D旋转
     */
    data class Quaternion(var x: Float, var y: Float, var z: Float, var w: Float)

    /**
     * 初始化3D空间HUD
     */
    fun initialize(context: Context, displayWidth: Int, displayHeight: Int): Boolean {
        if (isInitialized) return true

        try {
            // 检查是否支持3D空间HUD
            isSpatialHUDSupported = nativeIsSpatialHUDSupported()
            Log.d(TAG, "3D Spatial HUD supported: $isSpatialHUDSupported")

            if (!isSpatialHUDSupported) {
                Log.e(TAG, "3D Spatial HUD not supported - Oculus SDK unavailable")
                return false
            }

            // 设置Android上下文（OpenXR Android平台必需）
            // OpenXR 可能需要 Activity Context，直接传入原始 context
            Log.d(TAG, "Setting Android context: ${context.javaClass.simpleName}")
            nativeSetAndroidContext(context)

            // 初始化JNI层
            val result = nativeInitialize(displayWidth, displayHeight)
            if (!result) {
                Log.e(TAG, "Failed to initialize native VR HUD")
                return false
            }

            // 创建文本渲染所需的Bitmap和Paint
            createTextRenderResources()

            // 应用默认的3D空间位置
            setPosition(hudPosition.first, hudPosition.second, hudPosition.third)
            setSize(hudSize.first, hudSize.second)

            isInitialized = true
            Log.d(TAG, "3D Spatial HUD initialized successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing VR HUD", e)
            return false
        }
    }

    /**
     * 创建文本渲染资源
     */
    private fun createTextRenderResources() {
        hudBitmap = Bitmap.createBitmap(1024, 512, Bitmap.Config.ARGB_8888)
        hudCanvas = Canvas(hudBitmap!!)

        // 歌词文本Paint（更粗更大，适应VR环境）
        lyricPaint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textSize = 56f
            color = Color.WHITE
            setShadowLayer(15f, 0f, 0f, Color.BLACK)
        }

        // 翻译文本Paint
        translationPaint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT
            textSize = 36f
            color = Color.parseColor("#DDDDDD")
            setShadowLayer(10f, 0f, 0f, Color.BLACK)
        }
    }

    /**
     * 更新歌词（在3D空间HUD上显示）
     */
    fun updateLyric(lyric: String, translation: String = "") {
        if (!isInitialized) return

        try {
            // 在Bitmap上绘制歌词
            renderTextToBitmap(lyric, translation)

            // 将歌词传递给JNI层
            nativeUpdateLyric(lyric, translation)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating lyric", e)
        }
    }

    /**
     * 渲染文本到Bitmap
     */
    private fun renderTextToBitmap(lyric: String, translation: String) {
        hudCanvas?.drawColor(Color.parseColor("#60000000"))

        hudCanvas?.let { canvas ->
            // 绘制歌词
            lyricPaint?.let { paint ->
                val text = if (lyric.isEmpty()) "暂无歌词" else lyric
                val textWidth = paint.measureText(text)
                val x = (canvas.width - textWidth) / 2f
                canvas.drawText(text, x, 180f, paint)
            }

            // 绘制翻译
            if (translation.isNotEmpty()) {
                translationPaint?.let { paint ->
                    val textWidth = paint.measureText(translation)
                    val x = (canvas.width - textWidth) / 2f
                    canvas.drawText(translation, x, 280f, paint)
                }
            }
        }
    }

    /**
     * 设置HUD的可见性
     */
    fun setVisible(visible: Boolean) {
        if (!isInitialized) return
        nativeSetVisible(visible)
        Log.d(TAG, "HUD visibility: $visible")
    }

    /**
     * 设置HUD的3D空间位置（单位：米）
     * @param x 左右方向（正数向右）
     * @param y 上下方向（正数向上）
     * @param z 前后方向（负数向前）
     */
    fun setPosition(x: Float, y: Float, z: Float) {
        if (!isInitialized) return
        nativeSetPosition(x, y, z)
        hudPosition = Triple(x, y, z)
        Log.d(TAG, "HUD 3D position: ($x, $y, $z) meters")
    }

    /**
     * 设置HUD的尺寸（单位：米）
     * @param width 宽度（米）
     * @param height 高度（米）
     */
    fun setSize(width: Float, height: Float) {
        if (!isInitialized) return
        nativeSetSize(width, height)
        hudSize = Pair(width, height)
        Log.d(TAG, "HUD size: ${width}m x ${height}m")
    }

    /**
     * 设置HUD的旋转（四元数）
     * @param qx, qy, qz, qw 四元数分量
     */
    fun setRotation(qx: Float, qy: Float, qz: Float, qw: Float) {
        if (!isInitialized) return
        nativeSetRotation(qx, qy, qz, qw)
        hudRotation = Quaternion(qx, qy, qz, qw)
        Log.d(TAG, "HUD rotation: ($qx, $qy, $qz, $qw)")
    }

    /**
     * 将HUD设置在用户前方（默认位置）
     * @param distance 距离眼睛的距离（米），默认2米
     * @param yOffset 垂直偏移（米），正数向上
     */
    fun setInFront(distance: Float = 2.0f, yOffset: Float = 0.0f) {
        setPosition(0.0f, yOffset, -distance)
        // 默认面向前方（无旋转）
        setRotation(0.0f, 0.0f, 0.0f, 1.0f)
    }

    /**
     * 获取当前帧时间（用于渲染同步）
     */
    fun getDisplayTime(): Double {
        if (!isInitialized) return 0.0
        return nativeGetDisplayTime()
    }

    /**
     * 渲染当前帧（在每一帧调用）
     */
    fun renderFrame() {
        if (!isInitialized) return
        nativeRenderFrame()
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        if (!isInitialized) return

        nativeCleanup()

        hudBitmap?.recycle()
        hudBitmap = null
        hudCanvas = null
        lyricPaint = null
        translationPaint = null

        isInitialized = false
        Log.d(TAG, "3D Spatial HUD cleaned up")
    }

    /**
     * 检查是否支持3D空间HUD
     */
    fun isSpatialHUDSupported(): Boolean = isSpatialHUDSupported

    // JNI方法声明
    private external fun nativeSetAndroidContext(context: Context)
    private external fun nativeInitialize(displayWidth: Int, displayHeight: Int): Boolean
    private external fun nativeUpdateLyric(lyric: String, translation: String)
    private external fun nativeSetVisible(visible: Boolean)
    private external fun nativeSetPosition(x: Float, y: Float, z: Float)
    private external fun nativeSetInFront(distance: Float, yOffset: Float)
    private external fun nativeSetOrientation(w: Float, x: Float, y: Float, z: Float)
    private external fun nativeSetSize(width: Float, height: Float)
    private external fun nativeSetRotation(qx: Float, qy: Float, qz: Float, qw: Float)
    private external fun nativeGetDisplayTime(): Double
    private external fun nativeRenderFrame()
    private external fun nativeCleanup()
    private external fun nativeIsSpatialHUDSupported(): Boolean

    init {
        System.loadLibrary("VRHUDRenderer")
    }
}

