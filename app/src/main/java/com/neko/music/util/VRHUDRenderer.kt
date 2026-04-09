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
 * VR HUD渲染器 - 使用JNI + SurfaceControl实现真正的全局VR HUD
 * 
 * 注意：此方案需要系统权限或调试模式才能实现真正的跨应用全局显示
 * 普通应用在VR设备上只能实现应用内HUD
 * 
 * 对于Quest/PICO等VR设备，要实现真正的全局HUD，需要：
 * 1. 系统签名应用（OEM合作）
 * 2. Root权限
 * 3. 或者使用VR SDK提供的专用API（如Oculus Overlay API）
 */
object VRHUDRenderer {
    private const val TAG = "VRHUDRenderer"
    
    private var isInitialized = false
    private var isGlobalHUDSupported = false
    
    // 用于文本渲染的Bitmap
    private var hudBitmap: Bitmap? = null
    private var hudCanvas: Canvas? = null
    private var lyricPaint: Paint? = null
    private var translationPaint: Paint? = null
    
    // 初始化
    fun initialize(context: Context, displayWidth: Int, displayHeight: Int): Boolean {
        if (isInitialized) return true
        
        try {
            // 检查是否支持全局HUD
            isGlobalHUDSupported = nativeIsGlobalHUDSupported()
            Log.d(TAG, "Global HUD supported: $isGlobalHUDSupported")
            
            // 初始化JNI层
            val result = nativeInitialize(displayWidth, displayHeight)
            if (!result) {
                Log.e(TAG, "Failed to initialize native VR HUD")
                return false
            }
            
            // 创建文本渲染所需的Bitmap和Paint
            createTextRenderResources()
            
            isInitialized = true
            Log.d(TAG, "VR HUD initialized successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing VR HUD", e)
            return false
        }
    }
    
    // 创建文本渲染资源
    private fun createTextRenderResources() {
        hudBitmap = Bitmap.createBitmap(800, 200, Bitmap.Config.ARGB_8888)
        hudCanvas = Canvas(hudBitmap!!)
        
        // 歌词文本Paint
        lyricPaint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textSize = 48f
            color = Color.WHITE
            setShadowLayer(12f, 0f, 0f, Color.BLACK)
        }
        
        // 翻译文本Paint
        translationPaint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT
            textSize = 32f
            color = Color.parseColor("#DDDDDD")
            setShadowLayer(8f, 0f, 0f, Color.BLACK)
        }
    }
    
    // 更新歌词
    fun updateLyric(lyric: String, translation: String = "") {
        if (!isInitialized) return
        
        try {
            // 在Bitmap上绘制歌词
            renderTextToBitmap(lyric, translation)
            
            // 将Bitmap数据传递给JNI层
            val bitmapData = getBitmapData()
            nativeUpdateLyric(lyric, translation)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating lyric", e)
        }
    }
    
    // 渲染文本到Bitmap
    private fun renderTextToBitmap(lyric: String, translation: String) {
        hudCanvas?.drawColor(Color.parseColor("#60000000"))
        
        hudCanvas?.let { canvas ->
            // 绘制歌词
            lyricPaint?.let { paint ->
                val text = if (lyric.isEmpty()) "暂无歌词" else lyric
                val textWidth = paint.measureText(text)
                val x = (canvas.width - textWidth) / 2f
                canvas.drawText(text, x, 80f, paint)
            }
            
            // 绘制翻译
            if (translation.isNotEmpty()) {
                translationPaint?.let { paint ->
                    val textWidth = paint.measureText(translation)
                    val x = (canvas.width - textWidth) / 2f
                    canvas.drawText(translation, x, 140f, paint)
                }
            }
        }
    }
    
    // 获取Bitmap数据
    private fun getBitmapData(): ByteArray {
        hudBitmap?.let { bitmap ->
            val buffer = ByteBuffer.allocate(bitmap.byteCount)
            bitmap.copyPixelsToBuffer(buffer)
            buffer.rewind()
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            return data
        }
        return ByteArray(0)
    }
    
    // 设置可见性
    fun setVisible(visible: Boolean) {
        if (!isInitialized) return
        nativeSetVisible(visible)
    }
    
    // 设置位置
    fun setPosition(x: Float, y: Float) {
        if (!isInitialized) return
        nativeSetPosition(x, y)
    }
    
    // 清理资源
    fun cleanup() {
        if (!isInitialized) return
        
        nativeCleanup()
        
        hudBitmap?.recycle()
        hudBitmap = null
        hudCanvas = null
        lyricPaint = null
        translationPaint = null
        
        isInitialized = false
    }
    
    // 检查是否支持全局HUD
    fun isGlobalHUDSupported(): Boolean = isGlobalHUDSupported
    
    // JNI方法
    private external fun nativeInitialize(displayWidth: Int, displayHeight: Int): Boolean
    private external fun nativeUpdateLyric(lyric: String, translation: String)
    private external fun nativeSetVisible(visible: Boolean)
    private external fun nativeSetPosition(x: Float, y: Float)
    private external fun nativeCleanup()
    private external fun nativeIsGlobalHUDSupported(): Boolean
    
    init {
        System.loadLibrary("VRHUDRenderer")
    }
}

