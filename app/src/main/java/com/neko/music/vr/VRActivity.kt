package com.neko.music.vr

import android.app.Activity
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.neko.music.util.VRHUDRenderer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * VR渲染Activity
 * 使用OpenGL ES在VR环境中渲染歌词HUD
 */
class VRActivity : Activity() {

    private var glSurfaceView: GLSurfaceView? = null
    private var renderer: VRGLRenderer? = null
    private var infoTextView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "VRActivity"
        private const val VR_INITIALIZATION_DELAY = 100L // VR初始化延迟（毫秒）
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "VRActivity onCreate - Creating VR environment")

        // 检查是否为VR设备
        if (!com.neko.music.util.DeviceDetector.isVRDevice()) {
            Log.w(TAG, "Not a VR device, VRActivity should not be launched")
            finish()
            return
        }

        // 配置VR显示参数
        setupVRDisplay()

        // 创建布局
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)
            
            // 添加信息文本
            infoTextView = TextView(this@VRActivity).apply {
                text = "Neko云音乐 - VR模式"
                textSize = 32f
                setTextColor(android.graphics.Color.WHITE)
                gravity = android.view.Gravity.CENTER
                setPadding(40, 80, 40, 80)
            }
            addView(infoTextView)
            
            // 创建GLSurfaceView用于渲染
            glSurfaceView = VRGLSurfaceView(this@VRActivity)
            addView(glSurfaceView)
        }
        
        renderer = VRGLRenderer()
        glSurfaceView?.setEGLContextClientVersion(2)
        glSurfaceView?.setRenderer(renderer)
        
        setContentView(layout)
        
        // 延迟初始化VR HUD，确保Activity完全创建
        handler.postDelayed({
            initializeVRHUD()
        }, VR_INITIALIZATION_DELAY)
    }

    private fun setupVRDisplay() {
        try {
            // 保持屏幕常亮
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // 设置为VR模式
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            }
            
            // 隐藏状态栏和导航栏
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
            
            Log.d(TAG, "VR display setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup VR display", e)
        }
    }

    private fun initializeVRHUD() {
        Log.d(TAG, "Starting VR HUD initialization")
        
        Thread {
            try {
                // 初始化OpenXR HUD
                val displayMetrics = resources.displayMetrics
                val success = VRHUDRenderer.initialize(this@VRActivity, displayMetrics.widthPixels, displayMetrics.heightPixels)

                if (!success) {
                    Log.e(TAG, "Failed to initialize VR HUD renderer")
                    runOnUiThread {
                        infoTextView?.text = "VR模式不可用\nOpenXR初始化失败\n使用简化模式"
                    }
                    return@Thread
                }

                Log.d(TAG, "VR HUD renderer initialized successfully")

                // 设置默认HUD位置（用户前方2米）
                VRHUDRenderer.setInFront(2.0f, 0.0f)
                
                // 启用HUD可见性
                VRHUDRenderer.setVisible(true)
                
                runOnUiThread {
                    infoTextView?.text = "Neko云音乐 - VR模式\n3D空间HUD已启用"
                }
                
                Log.d(TAG, "VRActivity setup complete")
            } catch (e: Exception) {
                Log.e(TAG, "Error during VR HUD initialization", e)
                runOnUiThread {
                    infoTextView?.text = "VR模式初始化错误: ${e.message}"
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        
        try {
            glSurfaceView?.onResume()
            
            // 重新设置VR显示模式
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        } catch (e: Exception) {
            Log.e(TAG, "Error in onResume", e)
        }
        
        // 恢复时重新启用HUD
        handler.postDelayed({
            VRHUDRenderer.setVisible(true)
        }, 500)
        
        Log.d(TAG, "VRActivity resumed")
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView?.onPause()
        
        // 暂停时禁用HUD
        VRHUDRenderer.setVisible(false)
        
        Log.d(TAG, "VRActivity paused")
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // 清理资源
        handler.removeCallbacksAndMessages(null)
        
        try {
            renderer?.cleanup()
            VRHUDRenderer.cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
        
        Log.d(TAG, "VRActivity destroyed")
    }

    /**
     * 自定义GLSurfaceView，支持VR渲染
     */
    private inner class VRGLSurfaceView(context: Activity) : GLSurfaceView(context) {
        init {
            // 配置为不透明背景
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            // 启用深度测试
            setPreserveEGLContextOnPause(true)
        }
        
        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            // 在attach到窗口后设置渲染模式
            try {
                setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set render mode", e)
            }
        }
    }

    /**
     * GL渲染器
     */
    private inner class VRGLRenderer : GLSurfaceView.Renderer {
        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
            Log.d(TAG, "GL surface created")
            // 设置清除颜色为黑色
            gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        }

        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
            Log.d(TAG, "GL surface changed: ${width}x${height}")
            // 设置视口
            gl.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10) {
            // 清除屏幕
            gl.glClear(GL10.GL_COLOR_BUFFER_BIT or GL10.GL_DEPTH_BUFFER_BIT)
            
            // 每帧渲染VR HUD
            VRHUDRenderer.renderFrame()
        }

        fun cleanup() {
            // 清理资源
        }
    }
}