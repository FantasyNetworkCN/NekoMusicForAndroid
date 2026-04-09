package com.neko.music.vr

import android.app.Activity
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import com.neko.music.util.VRHUDRenderer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * VR渲染Activity
 * 使用OpenXR在VR环境中渲染歌词HUD
 */
class VRActivity : Activity() {

    private var glSurfaceView: GLSurfaceView? = null
    private var renderer: VRGLRenderer? = null

    companion object {
        private const val TAG = "VRActivity"
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

        // 初始化OpenXR HUD
        val vrHUDRenderer = VRHUDRenderer
        val displayMetrics = resources.displayMetrics
        val success = vrHUDRenderer.initialize(this, displayMetrics.widthPixels, displayMetrics.heightPixels)

        if (!success) {
            Log.e(TAG, "Failed to initialize VR HUD renderer")
            finish()
            return
        }

        Log.d(TAG, "VR HUD renderer initialized successfully")

        // 设置默认HUD位置（用户前方2米）
        vrHUDRenderer.setInFront(2.0f, 0.0f)

        // 创建GLSurfaceView用于渲染
        glSurfaceView = VRGLSurfaceView(this)
        renderer = VRGLRenderer()
        glSurfaceView?.setEGLContextClientVersion(2)
        glSurfaceView?.setRenderer(renderer)
        
        setContentView(glSurfaceView)
        
        Log.d(TAG, "VRActivity setup complete")
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView?.onResume()
        Log.d(TAG, "VRActivity resumed")
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView?.onPause()
        Log.d(TAG, "VRActivity paused")
    }

    override fun onDestroy() {
        super.onDestroy()
        renderer?.cleanup()
        VRHUDRenderer.cleanup()
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
    }

    /**
     * GL渲染器
     */
    private inner class VRGLRenderer : GLSurfaceView.Renderer {
        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
            Log.d(TAG, "GL surface created")
        }

        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
            Log.d(TAG, "GL surface changed: ${width}x${height}")
        }

        override fun onDrawFrame(gl: GL10) {
            // 每帧渲染VR HUD
            VRHUDRenderer.renderFrame()
        }

        fun cleanup() {
            // 清理资源
        }
    }
}