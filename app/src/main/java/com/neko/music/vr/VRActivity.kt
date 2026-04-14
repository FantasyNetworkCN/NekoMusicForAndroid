package com.neko.music.vr

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.neko.music.desktoplyric.VRHUDService
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * VR音乐活动 - 提供沉浸式VR音乐体验
 * 支持头显跟随的HUD歌词显示
 * 使用纯Android/OpenGL实现，无需外部VR SDK
 */
class VRActivity : AppCompatActivity() {

    private var glSurfaceView: GLSurfaceView? = null
    private var isVRHUDEnabled = true
    
    companion object {
        const val TAG = "VRActivity"
        const val ACTION_SHOW_VR_HUD = "com.neko.music.action.SHOW_VR_HUD"
        const val ACTION_HIDE_VR_HUD = "com.neko.music.action.HIDE_VR_HUD"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "VRActivity onCreate")
        
        // 初始化VR视图
        initializeVRView()
        
        // 启动VR HUD服务
        startVRHUDService()
    }
    
    /**
     * 初始化VR视图
     */
    private fun initializeVRView() {
        glSurfaceView = GLSurfaceView(this)
        
        // 设置OpenGL ES 2.0
        glSurfaceView?.setEGLContextClientVersion(2)
        
        // 设置渲染器
        glSurfaceView?.setRenderer(VRRenderer())
        
        // 设置渲染模式（连续渲染）
        glSurfaceView?.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        
        // 设置内容
        setContentView(glSurfaceView)
        
        Log.d(TAG, "VR view initialized")
    }
    
    /**
     * 启动VR HUD服务
     */
    private fun startVRHUDService() {
        val intent = android.content.Intent(this, VRHUDService::class.java)
        intent.action = VRHUDService.ACTION_SHOW
        startService(intent)
        Log.d(TAG, "VR HUD service started")
    }
    
    /**
     * 停止VR HUD服务
     */
    private fun stopVRHUDService() {
        val intent = android.content.Intent(this, VRHUDService::class.java)
        intent.action = VRHUDService.ACTION_HIDE
        stopService(intent)
        Log.d(TAG, "VR HUD service stopped")
    }
    
    /**
     * 切换VR HUD显示状态
     */
    fun toggleVRHUD() {
        isVRHUDEnabled = !isVRHUDEnabled
        if (isVRHUDEnabled) {
            startVRHUDService()
        } else {
            stopVRHUDService()
        }
    }
    
    override fun onPause() {
        super.onPause()
        glSurfaceView?.onPause()
        Log.d(TAG, "VRActivity onPause")
    }
    
    override fun onResume() {
        super.onResume()
        glSurfaceView?.onResume()
        Log.d(TAG, "VRActivity onResume")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopVRHUDService()
        glSurfaceView = null
        Log.d(TAG, "VRActivity onDestroy")
    }
    
    /**
     * VR渲染器 - 处理VR场景的渲染
     */
    private inner class VRRenderer : GLSurfaceView.Renderer {
        
        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
            Log.d(TAG, "VR surface created")
            // 初始化OpenGL资源
            gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        }
        
        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
            Log.d(TAG, "VR surface changed: ${width}x${height}")
            gl.glViewport(0, 0, width, height)
        }
        
        override fun onDrawFrame(gl: GL10) {
            // 清除屏幕
            gl.glClear(GL10.GL_COLOR_BUFFER_BIT or GL10.GL_DEPTH_BUFFER_BIT)
            
            // 渲染VR场景
            // 这里可以添加3D内容渲染
        }
    }
}