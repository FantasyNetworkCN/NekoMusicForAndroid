package com.neko.music

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import android.widget.LinearLayout

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neko.music.data.model.Music
import com.neko.music.service.MusicPlayerManager
import com.neko.music.ui.components.BottomNavigationBar
import com.neko.music.ui.components.BottomNavItem
import com.neko.music.ui.components.MiniPlayer
import com.neko.music.ui.components.PlaylistBottomSheet
import com.neko.music.ui.screens.HomeScreen
import com.neko.music.ui.screens.LoginScreen
import com.neko.music.ui.screens.ArtistDetailScreen
import com.neko.music.ui.screens.MineScreen
import com.neko.music.ui.screens.PlayerScreen
import com.neko.music.ui.screens.PlaylistScreen
import com.neko.music.ui.screens.RecentPlayScreen
import com.neko.music.ui.screens.RegisterScreen
import com.neko.music.ui.screens.SearchResultScreen
import com.neko.music.ui.screens.FavoriteScreen
import com.neko.music.ui.screens.AboutScreen
import com.neko.music.ui.screens.SettingsScreen
import com.neko.music.ui.screens.CacheManagementScreen
import com.neko.music.ui.screens.AccountInfoScreen
import com.neko.music.ui.screens.MyPlaylistsScreen
import com.neko.music.ui.screens.PlaylistDetailScreen
import com.neko.music.ui.screens.RankingScreen
import com.neko.music.ui.screens.LatestScreen
import com.neko.music.ui.screens.UploadedMusicScreen
import com.neko.music.ui.theme.Neko云音乐Theme
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val PREFS_NAME = "app_prefs"
    private val KEY_FIRST_LAUNCH = "first_launch"
    private val REQUEST_CODE_INSTALL_PERMISSION = 1001

    // 启动页状态
    private var showSplash by mutableStateOf(false)
    
    // VR模式状态
    private var isVRMode by mutableStateOf(false)
    private var glSurfaceView: android.opengl.GLSurfaceView? = null
    private var vrGLRenderer: VRGLRenderer? = null
    private var vrInitializationCompleted = false  // VR初始化是否完成
    private val vrHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // 安装权限请求结果回调
    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (packageManager.canRequestPackageInstalls()) {
                Log.d("MainActivity", "安装权限已授予")
            } else {
                Log.d("MainActivity", "安装权限被拒绝")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 检测是否为VR设备
        val isVRDevice = com.neko.music.util.DeviceDetector.isVRDevice()
        Log.d("MainActivity", "Device type: ${if (isVRDevice) "VR Headset" else "Normal Phone"}")
        
        // 根据设备类型设置屏幕方向
        requestedOrientation = if (isVRDevice) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        // VR设备检测
        if (isVRDevice) {
            Log.d("MainActivity", "VR device detected, checking VR mode preference")
            
            // 检查用户是否启用了VR模式
            val vrModePrefs = getSharedPreferences("vr_settings", Context.MODE_PRIVATE)
            val useVRMode = vrModePrefs.getBoolean("use_vr_mode", true) // 默认启用VR模式
            
            if (useVRMode) {
                Log.d("MainActivity", "VR mode enabled, attempting VR initialization")
                // 直接在MainActivity中启动VR模式（不使用预检查）
                isVRMode = true
                setupVRMode()
                return
            } else {
                Log.d("MainActivity", "VR mode disabled, using normal mode")
                // VR模式被禁用，使用普通模式
            }
        }

        // 应用语言设置
        applyLanguage()

        // 检查是否是首次启动
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true)

        if (isFirstLaunch) {
            // 首次启动，显示开屏
            prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
            showSplash = true
        }

        // 请求安装权限
        requestInstallPermission()

        // 启动音乐播放服务（前台服务，保持后台运行）
        MusicPlayerService.startService(this)

        // 检查所有开关状态并启动相应的服务
        checkAndStartServices()

        setContent {
            Neko云音乐Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    if (showSplash) {
                        SplashScreen(onAnimationComplete = {
                            showSplash = false
                        })
                    } else {
                        MainScreen()
                    }
                }
            }
        }
    }
    
    /**
     * 检查所有开关状态并启动相应的服务
     */
    private fun checkAndStartServices() {
        // 检查桌面歌词开关
        val desktopLyricPrefs = getSharedPreferences("desktop_lyric", Context.MODE_PRIVATE)
        val isDesktopLyricEnabled = desktopLyricPrefs.getBoolean("desktop_lyric_enabled", false)
        
        if (isDesktopLyricEnabled) {
            // 检查悬浮窗权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    val intent = Intent(this, com.neko.music.desktoplyric.DesktopLyricService::class.java)
                    intent.action = com.neko.music.desktoplyric.DesktopLyricService.ACTION_SHOW
                    startService(intent)
                    Log.d("MainActivity", "桌面歌词已开启，启动桌面歌词服务")
                } else {
                    Log.d("MainActivity", "桌面歌词已开启但没有悬浮窗权限")
                }
            }
        }
        
        // 检查灵动岛开关
        val floatPrefs = getSharedPreferences("float_window", Context.MODE_PRIVATE)
        val isFuckChinaOSEnabled = floatPrefs.getBoolean("fuck_china_os_enabled", false)
        
        if (isFuckChinaOSEnabled) {
            // 检查悬浮窗权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    val intent = Intent(this, com.neko.music.floatwindow.FuckChinaOSFloatService::class.java)
                    intent.action = com.neko.music.floatwindow.FuckChinaOSFloatService.ACTION_SHOW
                    startService(intent)
                    Log.d("MainActivity", "灵动岛已开启，启动灵动岛服务")
                } else {
                    Log.d("MainActivity", "灵动岛已开启但没有悬浮窗权限")
                }
            }
        }
    }

    /**
     * 设置VR模式
     */
    private fun setupVRMode() {
        Log.d("MainActivity", "Setting up VR mode")
        
        // 配置VR显示参数
        setupVRDisplay()

        // 创建布局
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)
            
            // 添加信息文本
            val infoTextView = android.widget.TextView(this@MainActivity).apply {
                text = "Neko云音乐 - VR模式"
                textSize = 32f
                setTextColor(android.graphics.Color.WHITE)
                gravity = android.view.Gravity.CENTER
                setPadding(40, 80, 40, 80)
            }
            addView(infoTextView)
            
            // 创建GLSurfaceView用于渲染
            glSurfaceView = VRGLSurfaceView(this@MainActivity)
            addView(glSurfaceView)
        }
        
        vrGLRenderer = VRGLRenderer()
        
        // 设置GLSurfaceView的渲染器
        glSurfaceView?.setRenderer(vrGLRenderer)
        
        setContentView(layout)
        
        // 延迟初始化VR HUD，确保Activity完全创建
        vrHandler.postDelayed({
            initializeVRHUD()
        }, 100L)
    }

    /**
     * 配置VR显示
     */
    private fun setupVRDisplay() {
        try {
            // 保持屏幕常亮
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // 设置为VR模式
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            }
            
            // 隐藏状态栏和导航栏
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
            
            Log.d("MainActivity", "VR display setup complete")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to setup VR display", e)
        }
    }

    /**
     * 初始化VR HUD
     */
    private fun initializeVRHUD() {
        Log.d("MainActivity", "Starting VR HUD initialization")
        
        Thread {
            try {
                // 初始化OpenXR HUD
                val displayMetrics = resources.displayMetrics
                var width = displayMetrics.widthPixels
                var height = displayMetrics.heightPixels
                
                // 检查尺寸是否有效（PICO设备启动瞬间可能返回0或100）
                if (width <= 100 || height <= 100) {
                    Log.w("MainActivity", "Invalid display metrics for VR setup: ${width}x${height}, using default values")
                    width = 1920
                    height = 1080
                }
                
                val success = com.neko.music.util.VRHUDRenderer.initialize(this@MainActivity, width, height)

                if (!success) {
                    Log.e("MainActivity", "Failed to initialize VR HUD renderer")
                    // 初始化失败，重置VR模式状态并回退到正常模式
                    vrHandler.post {
                        vrInitializationCompleted = true
                        fallbackToNormalMode("VR HUD initialization failed")
                    }
                    return@Thread
                }

                // 检查是否真正支持空间HUD（而不是简化模式）
                if (!com.neko.music.util.VRHUDRenderer.isSpatialHUDSupported()) {
                    Log.w("MainActivity", "VR HUD initialized but spatial HUD not supported, falling back to normal mode")
                    // 虽然初始化成功，但只支持简化模式，回退到正常模式
                    vrHandler.post {
                        vrInitializationCompleted = true
                        fallbackToNormalMode("Spatial HUD not supported")
                    }
                    return@Thread
                }

                Log.d("MainActivity", "VR HUD renderer initialized successfully")

                // 设置默认HUD位置（用户前方2米）
                com.neko.music.util.VRHUDRenderer.setInFront(2.0f, 0.0f)
                
                // 启用HUD可见性
                com.neko.music.util.VRHUDRenderer.setVisible(true)
                
                Log.d("MainActivity", "VR mode setup complete")
                
                // 标记VR初始化完成
                vrHandler.post {
                    vrInitializationCompleted = true
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error during VR HUD initialization", e)
                // 发生异常，回退到正常模式
                vrHandler.post {
                    vrInitializationCompleted = true
                    fallbackToNormalMode("VR HUD initialization exception: ${e.message}")
                }
            }
        }.start()
    }

    /**
     * 回退到正常模式
     */
    private fun fallbackToNormalMode(reason: String) {
        Log.d("MainActivity", "Falling back to normal mode: $reason")
        
        // 检查并启动灵动岛（如果开关已开启且现在有权限）
        val floatPrefs = getSharedPreferences("float_window", Context.MODE_PRIVATE)
        val isFuckChinaOSEnabled = floatPrefs.getBoolean("fuck_china_os_enabled", false)
        
        if (isFuckChinaOSEnabled) {
            // 检查悬浮窗权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    val intent = Intent(this, com.neko.music.floatwindow.FuckChinaOSFloatService::class.java)
                    intent.action = com.neko.music.floatwindow.FuckChinaOSFloatService.ACTION_SHOW
                    startService(intent)
                    Log.d("MainActivity", "灵动岛已开启，启动灵动岛服务")
                }
            }
        }
        
        // VR模式下恢复GLSurfaceView
        if (isVRMode) {
            glSurfaceView?.onResume()
            
            // 清理VR渲染器
            try {
                vrGLRenderer?.cleanup()
            } catch (e: Exception) {
                Log.w("MainActivity", "Error cleaning up VRGLRenderer during fallback", e)
            }
            vrGLRenderer = null
            
            // 清理VRHUDRenderer
            try {
                com.neko.music.util.VRHUDRenderer.cleanup()
            } catch (e: Exception) {
                Log.w("MainActivity", "Error cleaning up VRHUDRenderer during fallback", e)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error during VR cleanup", e)
        }
        
        // 重新初始化正常模式
        reinitializeNormalMode()
    }

    /**
     * 重新初始化正常模式
     */
    private fun reinitializeNormalMode() {
        Log.d("MainActivity", "Reinitializing normal mode")
        
        // 将耗时操作移到后台线程
        Thread {
            try {
                // 应用语言设置
                applyLanguage()

                // 检查是否是首次启动
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val isFirstLaunch = prefs.getBoolean(KEY_FIRST_LAUNCH, true)

                val shouldShowSplash = if (isFirstLaunch) {
                    // 首次启动，显示开屏
                    prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
                    true
                } else {
                    false
                }

                // 请求安装权限
                requestInstallPermission()

                // 启动音乐播放服务（前台服务，保持后台运行）
                MusicPlayerService.startService(this@MainActivity)

                // 检查所有开关状态并启动相应的服务
                checkAndStartServices()

                // 在主线程设置 Compose UI
                vrHandler.post {
                    try {
                        showSplash = shouldShowSplash
                        
                        // 设置Compose UI
                        setContent {
                            Neko云音乐Theme {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = Color.Transparent
                                ) {
                                    if (showSplash) {
                                        SplashScreen(onAnimationComplete = {
                                            showSplash = false
                                        })
                                    } else {
                                        MainScreen()
                                    }
                                }
                            }
                        }
                        
                        Log.d("MainActivity", "Normal mode reinitialized successfully")
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error setting content in reinitializeNormalMode", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error in reinitializeNormalMode", e)
                // 如果后台线程出错，在主线程恢复
                vrHandler.post {
                    finish()
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        
        // VR模式下恢复GLSurfaceView（只有在VR初始化完成后）
        if (isVRMode && vrInitializationCompleted && glSurfaceView != null && vrGLRenderer != null) {
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
                
                // 恢复时重新启用HUD
                vrHandler.postDelayed({
                    try {
                        com.neko.music.util.VRHUDRenderer.setVisible(true)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error setting HUD visible in onResume", e)
                    }
                }, 500)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error in VR mode onResume", e)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        
        // VR模式下暂停GLSurfaceView
        if (isVRMode) {
            glSurfaceView?.onPause()
            com.neko.music.util.VRHUDRenderer.setVisible(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // VR模式下清理资源
        if (isVRMode) {
            vrHandler.removeCallbacksAndMessages(null)
            
            try {
                vrGLRenderer?.cleanup()
                com.neko.music.util.VRHUDRenderer.cleanup()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error during VR cleanup", e)
            }
        }
    }

    /**
     * 自定义GLSurfaceView，支持VR渲染
     */
    private inner class VRGLSurfaceView(context: Activity) : android.opengl.GLSurfaceView(context) {
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
                setRenderMode(android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to set render mode", e)
            }
        }
    }

    /**
     * GL渲染器
     */
    private inner class VRGLRenderer : android.opengl.GLSurfaceView.Renderer {
        override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10, config: javax.microedition.khronos.egl.EGLConfig) {
            Log.d("MainActivity", "GL surface created")
            // 设置清除颜色为黑色
            gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        }

        override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10, width: Int, height: Int) {
            Log.d("MainActivity", "GL surface changed: ${width}x${height}")
            // 设置视口
            gl.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10) {
            // 清除屏幕
            gl.glClear(javax.microedition.khronos.opengles.GL10.GL_COLOR_BUFFER_BIT or javax.microedition.khronos.opengles.GL10.GL_DEPTH_BUFFER_BIT)
            
            // 每帧渲染VR HUD
            com.neko.music.util.VRHUDRenderer.renderFrame()
        }

        fun cleanup() {
            // 清理资源
        }
    }

    /**
     * 应用语言设置
     */
    private fun applyLanguage() {
        val languagePrefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val language = languagePrefs.getString("language", "system") ?: "system"
        
        val config = resources.configuration
        val locale = when (language) {
            "zh" -> java.util.Locale.SIMPLIFIED_CHINESE
            "en" -> java.util.Locale.ENGLISH
            else -> java.util.Locale.getDefault() // 跟随系统
        }
        
        java.util.Locale.setDefault(locale)
        config.setLocale(locale)
        
        // 更新resources的configuration
        resources.updateConfiguration(config, resources.displayMetrics)
        
        // 创建新的Configuration Context
        createConfigurationContext(config)
    }

    

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 从后台返回，不需要恢复播放，只检查收藏状态
        val context = this
        val playerManager = MusicPlayerManager.getInstance(context)
        playerManager.checkFavoriteStatus()
    }

    /**
     * 请求安装权限
     */
    private fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")
                )
                installPermissionLauncher.launch(intent)
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playerManager = MusicPlayerManager.getInstance(context)
    val scope = rememberCoroutineScope()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryFlow.collectAsState(initial = null)
    val currentRoute = navBackStackEntry?.destination?.route

    // 检查是否在播放页面
    val isPlayerScreen = currentRoute?.startsWith("player") == true

    // 获取播放器状态
    val isPlaying by playerManager.isPlaying.collectAsState()
    val currentMusicUrl by playerManager.currentMusicUrl.collectAsState()
    val currentMusicTitle by playerManager.currentMusicTitle.collectAsState()
    val currentMusicArtist by playerManager.currentMusicArtist.collectAsState()
    val currentMusicCover by playerManager.currentMusicCover.collectAsState()
    val currentMusicId by playerManager.currentMusicId.collectAsState()
    val currentPosition by playerManager.currentPosition.collectAsState()
    val duration by playerManager.duration.collectAsState()

    // 计算播放进度
    val progress =
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    // 更新播放进度
    androidx.compose.runtime.LaunchedEffect(currentPosition, duration) {
        if (duration > 0) {
            progress.floatValue = currentPosition.toFloat() / duration.toFloat()
        }
    }

    // 播放列表显示状态
    var showPlaylist by androidx.compose.runtime.remember { mutableStateOf(false) }

    // 底部控件可见性状态
    var showBottomControls by androidx.compose.runtime.remember { mutableStateOf(true) }

    // 登录和注册页面显示状态
    var showLoginScreen by androidx.compose.runtime.remember { mutableStateOf(false) }
    var showRegisterScreen by androidx.compose.runtime.remember { mutableStateOf(false) }
    var showLogoutDialog by androidx.compose.runtime.remember { mutableStateOf(false) }

    // 登录状态，用于触发界面更新
    var isLoggedIn by androidx.compose.runtime.remember { mutableStateOf(false) }
    var currentUsername by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    var currentUserId by androidx.compose.runtime.remember { mutableStateOf(-1) }
    var currentUserToken by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }

    // 初始化登录状态
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val tokenManager = com.neko.music.data.manager.TokenManager(context)
        isLoggedIn = tokenManager.isLoggedIn()
        currentUsername = tokenManager.getUsername()
        currentUserId = tokenManager.getUserId()
        currentUserToken = tokenManager.getToken()

        // 初始化收藏管理器
        playerManager.initializeFavoriteManager()
    }

    // 跟踪是否从播放页面返回
    var returningFromPlayer by androidx.compose.runtime.remember { mutableStateOf(false) }

    // 启动时恢复上次播放的音乐
    androidx.compose.runtime.LaunchedEffect(Unit) {
        scope.launch {
            // 检查是否已经有音乐在播放
            val currentMusicId = playerManager.currentMusicId.value
            val currentMusicTitle = playerManager.currentMusicTitle.value

            // 如果没有音乐信息，才恢复上次播放
            if (currentMusicId == null || currentMusicTitle == null || currentMusicTitle == "未知歌曲") {
                playerManager.restoreLastPlayed(context)
                // 等待音乐恢复播放后再检查收藏状态
                kotlinx.coroutines.delay(1000) // 等待1秒确保音乐信息已加载
            }

            // 检查收藏状态
            playerManager.checkFavoriteStatus()
        }
    }

    // 监听是否在播放页面，从播放页面返回时延迟显示底部控件
    androidx.compose.runtime.LaunchedEffect(isPlayerScreen) {
        if (isPlayerScreen) {
            // 进入播放页面，立即隐藏底部控件（无动画）
            showBottomControls = false
            returningFromPlayer = true
        } else if (returningFromPlayer) {
            // 从播放页面返回，延迟0.5秒后显示带动画
            kotlinx.coroutines.delay(500)
            showBottomControls = true
            returningFromPlayer = false
        }
    }

    // 检查是否在底部导航的三个主页面之一
    val isBottomNavItem = currentRoute == BottomNavItem.Home.route ||
                          currentRoute == BottomNavItem.Mine.route ||
                          currentRoute == BottomNavItem.MyPlaylists.route

    // 处理返回事件：Android 16 及以下版本自定义处理，Android 16 以上使用原生返回
    androidx.activity.compose.BackHandler(enabled = android.os.Build.VERSION.SDK_INT <= 16) {
        if (isBottomNavItem) {
            // 在主页、我的、我的歌单页面按返回键，退出到桌面（挂起到后台）
            (context as MainActivity).moveTaskToBack(false)
        } else {
            // 其他页面正常返回上一级
            navController.popBackStack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 主内容区域 - 铺满全屏
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Home.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                androidx.compose.animation.scaleIn(
                    initialScale = 0.95f,
                    animationSpec = tween(
                        durationMillis = 280,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = 280,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                )
            },
            exitTransition = {
                androidx.compose.animation.scaleOut(
                    targetScale = 1.05f,
                    animationSpec = tween(
                        durationMillis = 240,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = 240,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                )
            },
            popEnterTransition = {
                androidx.compose.animation.scaleIn(
                    initialScale = 1.05f,
                    animationSpec = tween(
                        durationMillis = 240,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = 240,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                )
            },
            popExitTransition = {
                androidx.compose.animation.scaleOut(
                    targetScale = 0.95f,
                    animationSpec = tween(
                        durationMillis = 280,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = 280,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                )
            }
        ) {
            composable(BottomNavItem.Home.route) {
                HomeScreen(
                    onSearchClick = {
                        Log.d("MainActivity", "导航到搜索页面")
                        // 清除保存的搜索状态，让用户看到一个干净的搜索界面
                        val searchStatePrefs = context.getSharedPreferences("search_state", android.content.Context.MODE_PRIVATE)
                        searchStatePrefs.edit()
                            .remove("last_search_query")
                            .remove("last_search_type")
                            .apply()
                        navController.navigate("search")
                    },
                    onNavigateToFavorite = {
                        navController.navigate("favorites")
                    },
                    onNavigateToPlaylist = { playlistId ->
                        Log.d("MainActivity", "导航到歌单详情: $playlistId")
                        // 获取歌单详情信息
                        val playlistManager = com.neko.music.data.manager.PlaylistManager.getInstance(context)
                        // 这里简化处理，直接导航到歌单详情页
                        // 实际应该先获取歌单信息再导航
                        navController.navigate("playlist_detail/$playlistId/歌单/null/null/null/-1/false")
                    },
                    onNavigateToRanking = {
                        Log.d("MainActivity", "导航到排行榜页面")
                        navController.navigate("ranking")
                    },
                    onNavigateToLatest = {
                        Log.d("MainActivity", "导航到最新音乐页面")
                        navController.navigate("latest")
                    }
                )
            }
            composable(BottomNavItem.Mine.route) {
                MineScreen(
                    onRecentPlayClick = {
                        navController.navigate("recent_play")
                    },
                    onLoginClick = {
                        showLoginScreen = true
                    },
                    onLogoutClick = {
                        showLogoutDialog = true
                    },
                    onFavoriteClick = {
                        navController.navigate("favorites")
                    },
                    onAboutClick = {
                        navController.navigate("about")
                    },
                    onNavigateToSettings = {
                        navController.navigate("settings")
                    },
                    onAccountInfoClick = {
                        navController.navigate("account_info")
                    },
                    onUploadClick = {
                        if (isLoggedIn) {
                            navController.navigate("uploaded_music")
                        } else {
                            showLoginScreen = true
                        }
                    },
                    isLoggedIn = isLoggedIn,
                    username = currentUsername,
                    userId = currentUserId,
                    onLoginSuccess = {
                        // 登录成功后更新状态
                        val tokenManager = com.neko.music.data.manager.TokenManager(context)
                        isLoggedIn = tokenManager.isLoggedIn()
                        currentUsername = tokenManager.getUsername()
                        currentUserId = tokenManager.getUserId()
                        currentUserToken = tokenManager.getToken()
                    },
                    token = currentUserToken
                )
            }
            composable(BottomNavItem.MyPlaylists.route) {
                MyPlaylistsScreen(
                    onNavigateToPlaylistDetail = { playlistId, playlistName, playlistCover, playlistDescription, creatorUsername, creatorUserId ->
                        val encodedName = java.net.URLEncoder.encode(playlistName, "UTF-8")
                        val encodedCover = if (playlistCover != null) java.net.URLEncoder.encode(playlistCover, "UTF-8") else "null"
                        val encodedDescription = java.net.URLEncoder.encode(playlistDescription ?: "", "UTF-8")
                        val encodedCreatorUsername = java.net.URLEncoder.encode(creatorUsername ?: "", "UTF-8")
                        navController.navigate("playlist_detail/$playlistId/$encodedName/$encodedCover/$encodedDescription/$encodedCreatorUsername/${creatorUserId ?: -1}/true")
                    },
                    onNavigateToFavorite = {
                        navController.navigate("favorites")
                    }
                )
            }
            composable("recent_play") {
                RecentPlayScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onMusicClick = { music ->
                        val id = music.id
                        val encodedTitle = java.net.URLEncoder.encode(music.title, "UTF-8")
                        val encodedArtist =
                            java.net.URLEncoder.encode(music.artist, "UTF-8")
                        navController.navigate("player/$id/$encodedTitle/$encodedArtist")
                    }
                )
            }
            composable("favorites") {
                FavoriteScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onMusicClick = { music ->
                        val id = music.id
                        val encodedTitle = java.net.URLEncoder.encode(music.title, "UTF-8")
                        val encodedArtist =
                            java.net.URLEncoder.encode(music.artist, "UTF-8")
                        navController.navigate("player/$id/$encodedTitle/$encodedArtist")
                    }
                )
            }
            composable("ranking") {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                RankingScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onNavigateToPlayer = { music ->
                        val encodedTitle = java.net.URLEncoder.encode(music.title, "UTF-8")
                        val encodedArtist = java.net.URLEncoder.encode(music.artist, "UTF-8")
                        navController.navigate("player/${music.id}/$encodedTitle/$encodedArtist")
                    }
                )
            }
            composable("latest") {
                LatestScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onNavigateToPlayer = { music ->
                        val encodedTitle = java.net.URLEncoder.encode(music.title, "UTF-8")
                        val encodedArtist = java.net.URLEncoder.encode(music.artist, "UTF-8")
                        navController.navigate("player/${music.id}/$encodedTitle/$encodedArtist")
                    }
                )
            }
            composable("uploaded_music") {
                val tokenManager = com.neko.music.data.manager.TokenManager(context)
                UploadedMusicScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onMusicClick = { music ->
                        val encodedTitle = java.net.URLEncoder.encode(music.title, "UTF-8")
                        val encodedArtist = java.net.URLEncoder.encode(music.artist, "UTF-8")
                        navController.navigate("player/${music.id}/$encodedTitle/$encodedArtist")
                    },
                    token = tokenManager.getToken(),
                    userId = tokenManager.getUserId()
                )
            }
            composable(
                route = "playlist_detail/{playlistId}/{playlistName}/{playlistCover}/{playlistDescription}/{creatorUsername}/{creatorUserId}/{isOwner}",
                arguments = listOf(
                    navArgument("playlistId") { type = NavType.IntType },
                    navArgument("playlistName") { type = NavType.StringType },
                    navArgument("playlistCover") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("playlistDescription") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("creatorUsername") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("creatorUserId") {
                        type = NavType.IntType
                        defaultValue = -1
                    },
                    navArgument("isOwner") {
                        type = NavType.BoolType
                        defaultValue = true
                    }
                )
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getInt("playlistId") ?: 0
                val playlistName = backStackEntry.arguments?.getString("playlistName") ?: ""
                val playlistCover = backStackEntry.arguments?.getString("playlistCover")
                val playlistDescription = backStackEntry.arguments?.getString("playlistDescription")
                val creatorUsername = backStackEntry.arguments?.getString("creatorUsername")
                val creatorUserId = backStackEntry.arguments?.getInt("creatorUserId")
                val isOwner = backStackEntry.arguments?.getBoolean("isOwner") ?: true
                PlaylistDetailScreen(
                    playlistId = playlistId,
                    playlistName = java.net.URLDecoder.decode(playlistName, "UTF-8"),
                    playlistCover = if (!playlistCover.isNullOrEmpty() && playlistCover != "null") {
                        java.net.URLDecoder.decode(playlistCover, "UTF-8")
                    } else {
                        null
                    },
                    playlistDescription = if (!playlistDescription.isNullOrEmpty()) {
                        java.net.URLDecoder.decode(playlistDescription, "UTF-8")
                    } else {
                        ""
                    },
                    creatorUsername = creatorUsername,
                    creatorUserId = if (creatorUserId == -1) null else creatorUserId,
                    isOwner = isOwner,
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onMusicClick = { music ->
                        val id = music.id
                        val encodedTitle = java.net.URLEncoder.encode(music.title, "UTF-8")
                        val encodedArtist =
                            java.net.URLEncoder.encode(music.artist, "UTF-8")
                        navController.navigate("player/$id/$encodedTitle/$encodedArtist")
                    },
                    onPlayAll = { musicList ->
                        // 清空播放列表并将歌单中的所有音乐添加进去
                        scope.launch {
                            try {
                                val musicApi = com.neko.music.data.api.MusicApi(context)
                                val playlistManager = com.neko.music.data.manager.PlaylistManager.getInstance(context)
                                
                                // 清空当前播放列表
                                playlistManager.clearPlaylist()
                                
                                // 按顺序添加音乐到播放列表
                                musicList.forEach { playlistMusic ->
                                    val url = musicApi.getMusicFileUrl(
                                        com.neko.music.data.model.Music(
                                            playlistMusic.id,
                                            playlistMusic.title,
                                            playlistMusic.artist,
                                            playlistMusic.coverPath ?: "",
                                            playlistMusic.duration,
                                            "",
                                            "",
                                            0,
                                            ""
                                        )
                                    )
                                    // coverFilePath设置为空字符串，让PlaylistScreen使用音乐ID生成封面URL
                                    playlistManager.addToPlaylist(
                                        com.neko.music.data.model.Music(
                                            playlistMusic.id,
                                            playlistMusic.title,
                                            playlistMusic.artist,
                                            "",
                                            playlistMusic.duration,
                                            url,
                                            "",
                                            0,
                                            ""
                                        )
                                    )
                                }
                                
                                // 播放第一首
                                if (musicList.isNotEmpty()) {
                                    val firstMusic = musicList[0]
                                    val url = musicApi.getMusicFileUrl(
                                        com.neko.music.data.model.Music(
                                            firstMusic.id,
                                            firstMusic.title,
                                            firstMusic.artist,
                                            firstMusic.coverPath ?: "",
                                            firstMusic.duration,
                                            "",
                                            "",
                                            0,
                                            ""
                                        )
                                    )
                                    val fullCoverUrl = "https://music.cnmsb.xin/api/music/cover/${firstMusic.id}"
                                    playerManager.playMusic(
                                        url,
                                        firstMusic.id,
                                        firstMusic.title,
                                        firstMusic.artist,
                                        firstMusic.coverPath ?: "",
                                        fullCoverUrl
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e("MainActivity", "播放全部失败", e)
                                android.widget.Toast.makeText(
                                    context,
                                    "播放失败: ${e.message}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
            }
            composable("about") {
                AboutScreen(
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
            composable("settings") {
                SettingsScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onNavigateToCache = {
                        navController.navigate("cache_management")
                    }
                )
            }
            composable("cache_management") {
                CacheManagementScreen(
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }
            composable("account_info") {
                AccountInfoScreen(
                    onBackClick = {
                        navController.popBackStack()
                    },
                    userId = currentUserId,
                    username = currentUsername ?: "",
                    email = com.neko.music.data.manager.TokenManager(context).getEmail()
                        ?: "",
                    onShowBottomControls = { show ->
                        showBottomControls = show
                    },
                    onAvatarUpdate = { imageData ->
                        scope.launch {
                            try {
                                Log.d(
                                    "MainActivity",
                                    "开始上传头像，图片大小: ${imageData.size} bytes"
                                )
                                val token =
                                    com.neko.music.data.manager.TokenManager(context)
                                        .getToken()
                                Log.d(
                                    "MainActivity",
                                    "Token: ${if (token != null) "已获取 (${token.length} 字符)" else "null"}"
                                )
                                val userApi = com.neko.music.data.api.UserApi(token)
                                Log.d("MainActivity", "UserApi 实例已创建")
                                val response = userApi.updateAvatar(imageData)
                                Log.d(
                                    "MainActivity",
                                    "头像上传响应: success=${response.success}, message=${response.message}"
                                )

                                if (response.success) {
                                    Log.d("MainActivity", "头像更新成功")
                                } else {
                                    Log.e(
                                        "MainActivity",
                                        "更新头像失败: ${response.message}"
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e("MainActivity", "上传头像失败", e)
                            }
                        }
                    },
                    onPasswordUpdate = { oldPassword, newPassword ->
                        try {
                            val userApi = com.neko.music.data.api.UserApi(
                                com.neko.music.data.manager.TokenManager(context)
                                    .getToken()
                            )
                            val response =
                                userApi.updatePassword(oldPassword, newPassword)

                            if (response.success) {
                                android.widget.Toast.makeText(
                                    context,
                                    "密码修改成功",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                true
                            } else {
                                // 失败时显示错误 Toast
                                android.widget.Toast.makeText(
                                    context,
                                    response.message,
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                false
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "修改密码失败", e)
                            android.widget.Toast.makeText(
                                context,
                                "修改密码失败: ${e.message}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            false
                        }
                    }
                )
            }
            composable(
                route = "search?query={query}",
                arguments = listOf(
                    navArgument("query") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val query = backStackEntry.arguments?.getString("query") ?: ""
                Log.d("MainActivity", "搜索页面加载，查询: $query")
                SearchResultScreen(
                    initialQuery = query,
                    onBackClick = {
                        Log.d("MainActivity", "从搜索页面返回")
                        navController.popBackStack()
                    },
                    onMusicClick = { music ->
                        Log.d("MainActivity", "点击音乐: ${music.title}")
                        val encodedTitle = java.net.URLEncoder.encode(music.title, "UTF-8")
                        val encodedArtist =
                            java.net.URLEncoder.encode(music.artist, "UTF-8")
                        navController.navigate("player/${music.id}/$encodedTitle/$encodedArtist")
                    },
                    onPlaylistClick = { playlistId, playlistName, playlistCover, playlistDescription, creatorUsername, creatorUserId ->
                        Log.d("MainActivity", "点击歌单: $playlistName (ID: $playlistId)")
                        val encodedName = java.net.URLEncoder.encode(playlistName, "UTF-8")
                        val encodedCover = if (playlistCover != null) java.net.URLEncoder.encode(playlistCover, "UTF-8") else "null"
                        val encodedDescription = java.net.URLEncoder.encode(playlistDescription ?: "", "UTF-8")
                        val encodedCreatorUsername = java.net.URLEncoder.encode(creatorUsername ?: "", "UTF-8")
                        navController.navigate("playlist_detail/$playlistId/$encodedName/$encodedCover/$encodedDescription/$encodedCreatorUsername/${creatorUserId ?: -1}/false")
                    },
                    onArtistClick = { artistName, musicCount, coverPath ->
                        Log.d("MainActivity", "点击歌手: $artistName")
                        val encodedName = java.net.URLEncoder.encode(artistName, "UTF-8")
                        val encodedCover = if (coverPath != null) java.net.URLEncoder.encode(coverPath, "UTF-8") else "null"
                        navController.navigate("artist_detail/$encodedName/$musicCount/$encodedCover")
                    }
                )
            }
            composable(
                route = "artist_detail/{artistName}/{musicCount}/{coverPath}",
                arguments = listOf(
                    navArgument("artistName") { type = NavType.StringType },
                    navArgument("musicCount") { type = NavType.IntType },
                    navArgument("coverPath") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val artistName = java.net.URLDecoder.decode(
                    backStackEntry.arguments?.getString("artistName") ?: "", "UTF-8"
                )
                val musicCount = backStackEntry.arguments?.getInt("musicCount") ?: 0
                val coverPath = backStackEntry.arguments?.getString("coverPath")
                val decodedCoverPath = if (!coverPath.isNullOrEmpty() && coverPath != "null") {
                    java.net.URLDecoder.decode(coverPath, "UTF-8")
                } else {
                    null
                }
                Log.d("MainActivity", "歌手详情页面加载: $artistName")
                ArtistDetailScreen(
                    artistName = artistName,
                    musicCount = musicCount,
                    coverPath = decodedCoverPath,
                    onBackClick = {
                        Log.d("MainActivity", "从歌手详情页面返回")
                        navController.popBackStack()
                    },
                    onMusicClick = { music ->
                        val encodedTitle = java.net.URLEncoder.encode(music.title, "UTF-8")
                        val encodedArtist =
                            java.net.URLEncoder.encode(music.artist, "UTF-8")
                        navController.navigate("player/${music.id}/$encodedTitle/$encodedArtist")
                    }
                )
            }
            composable(
                route = "player/{id}/{title}/{artist}",
                arguments = listOf(
                    navArgument("id") { type = NavType.IntType },
                    navArgument("title") { type = NavType.StringType },
                    navArgument("artist") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getInt("id") ?: 0
                val title = java.net.URLDecoder.decode(
                    backStackEntry.arguments?.getString("title") ?: "", "UTF-8"
                )
                val artist = java.net.URLDecoder.decode(
                    backStackEntry.arguments?.getString("artist") ?: "", "UTF-8"
                )
                val music = Music(id, title, artist, "", 0, "", "", 0, "")
                Log.d("MainActivity", "播放页面加载: $title")
                PlayerScreen(
                    music = music,
                    onBackClick = {
                        Log.d("MainActivity", "从播放页面返回")
                        navController.popBackStack()
                    },
                    onPlaylistClick = {
                        showPlaylist = true
                    }
                )
            }
        }

        // 只在非播放页面显示迷你播放器和底部导航栏 - 悬浮在底部

                if (!isPlayerScreen && showBottomControls) {

                    // MiniPlayer - 悬浮在底部

                    androidx.compose.animation.AnimatedVisibility(

                        visible = true,

                        enter = if (returningFromPlayer) {

                            androidx.compose.animation.slideInVertically(

                                initialOffsetY = { fullHeight -> fullHeight },

                                animationSpec = tween(

                                    durationMillis = 200,

                                    easing = androidx.compose.animation.core.FastOutSlowInEasing

                                )

                            )

                        } else {

                            androidx.compose.animation.fadeIn(animationSpec = tween(durationMillis = 0))

                        },

                        label = "miniPlayer"

                    ) {

                        androidx.compose.ui.layout.Layout(

                            content = {

                                MiniPlayer(

                                    isPlaying = isPlaying,

                                    songTitle = currentMusicTitle ?: "暂无播放",

                                    artist = currentMusicArtist ?: "",

                                    coverUrl = currentMusicCover,

                                    progress = progress.floatValue,

                                    onPlayPauseClick = {

                                        playerManager.togglePlayPause()

                                    },

                                    onPlayerClick = {

                                        // 跳转到播放页面，传递当前音乐ID

                                        val id = currentMusicId ?: 0

                                        val encodedTitle = java.net.URLEncoder.encode(

                                            currentMusicTitle ?: "未知歌曲", "UTF-8"

                                        )

                                        val encodedArtist = java.net.URLEncoder.encode(

                                            currentMusicArtist ?: "未知歌手", "UTF-8"

                                        )

                                        navController.navigate("player/$id/$encodedTitle/$encodedArtist")

                                    },

                                    onPlaylistClick = {

                                        showPlaylist = true

                                    }

                                )

                            },

                            measurePolicy = { measurables, constraints ->

                                val placeable = measurables.first().measure(

                                    constraints.copy(

                                        maxWidth = constraints.maxWidth - 32.dp.roundToPx() // 减去左右padding

                                    )

                                )

                                layout(placeable.width, placeable.height) {

                                    // 距离底部80dp，为导航菜单留出空间

                                    placeable.place(16.dp.roundToPx(), constraints.maxHeight - placeable.height - 80.dp.roundToPx())

                                }

                            }

                        )

                    }

        

                    // BottomNavigationBar - 悬浮在底部

                    androidx.compose.animation.AnimatedVisibility(

                        visible = true,

                        enter = if (returningFromPlayer) {

                            androidx.compose.animation.slideInVertically(

                                initialOffsetY = { fullHeight -> fullHeight },

                                animationSpec = tween(

                                    durationMillis = 200,

                                    easing = androidx.compose.animation.core.FastOutSlowInEasing

                                )

                            )

                        } else {

                            androidx.compose.animation.fadeIn(animationSpec = tween(durationMillis = 0))

                        },

                        label = "bottomNavigation"

                    ) {

                        androidx.compose.ui.layout.Layout(

                            content = {

                                BottomNavigationBar(navController = navController)

                            },

                            measurePolicy = { measurables, constraints ->

                                val placeable = measurables.first().measure(

                                    constraints.copy(

                                        maxWidth = constraints.maxWidth - 32.dp.roundToPx() // 减去左右padding

                                    )

                                )

                                layout(placeable.width, placeable.height) {

                                    placeable.place(16.dp.roundToPx(), constraints.maxHeight - placeable.height - 16.dp.roundToPx())

                                }

                            }

                        )

                    }

                }

        // 播放列表弹窗（在所有控件之上，覆盖显示）
                Box(modifier = Modifier.zIndex(1f)) {
                    PlaylistScreen(
                        isVisible = showPlaylist,
                        currentMusicId = currentMusicId,
                        onBackClick = {
                            showPlaylist = false
                        },
                        onMusicClick = { music ->
                            // 播放选中的音乐
                            scope.launch {
                                val musicApi = com.neko.music.data.api.MusicApi(context)
                                val url = musicApi.getMusicFileUrl(music)
                                val fullCoverUrl = if (!music.coverFilePath.isNullOrEmpty()) {
                                    "https://music.cnmsb.xin${music.coverFilePath}"
                                } else {
                                    "https://music.cnmsb.xin/api/music/cover/${music.id}"
                                }
                                playerManager.playMusic(
                                    url,
                                    music.id,
                                    music.title,
                                    music.artist,
                                    music.coverFilePath ?: "",
                                    fullCoverUrl
                                )
                            }
                            showPlaylist = false
                        }
                    )
                }                        
        // 登录和注册页面（在最顶层显示）
        AnimatedVisibility(
                visible = showLoginScreen,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(durationMillis = 150)
                ) + fadeIn(animationSpec = tween(durationMillis = 150)),
                exit = slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(durationMillis = 150)
                ) + fadeOut(animationSpec = tween(durationMillis = 150)),
                modifier = Modifier.zIndex(Float.MAX_VALUE)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LoginScreen(
                        onLoginSuccess = {
                            showLoginScreen = false
                            // 更新登录状态
                            val tokenManager = com.neko.music.data.manager.TokenManager(context)
                            isLoggedIn = tokenManager.isLoggedIn()
                            currentUsername = tokenManager.getUsername()
                            currentUserId = tokenManager.getUserId()
                            currentUserToken = tokenManager.getToken()
                        },
                        onBackClick = {
                            showLoginScreen = false
                        },
                        onRegisterClick = {
                            showLoginScreen = false
                            showRegisterScreen = true
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = showRegisterScreen,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(durationMillis = 150)
                ) + fadeIn(animationSpec = tween(durationMillis = 150)),
                exit = slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(durationMillis = 150)
                ) + fadeOut(animationSpec = tween(durationMillis = 150)),
                modifier = Modifier.zIndex(Float.MAX_VALUE)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RegisterScreen(
                        onRegisterSuccess = {
                            showRegisterScreen = false
                        },
                        onBackClick = {
                            showRegisterScreen = false
                        },
                        onLoginClick = {
                            showRegisterScreen = false
                            showLoginScreen = true
                        }
                    )
                }
            }

            // 退出登录确认对话框
            if (showLogoutDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showLogoutDialog = false },
                    title = { Text("退出登录") },
                    text = { Text("确定要退出登录吗？") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                // 清除登录状态
                                val tokenManager = com.neko.music.data.manager.TokenManager(context)
                                tokenManager.clearToken()
                                // 更新UI状态
                                isLoggedIn = false
                                currentUsername = null
                                currentUserId = -1
                                showLogoutDialog = false
                            }
                        ) {
                            Text("确定")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { showLogoutDialog = false }
                        ) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    }

// ==================== 启动页相关组件 ====================

@Composable
fun SplashScreen(onAnimationComplete: () -> Unit) {
    val scale = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val alpha = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 300, delayMillis = 30)
        )
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 300, delayMillis = 30)
        )
        kotlinx.coroutines.delay(1500)
        onAnimationComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        com.neko.music.ui.theme.DeepBlue,
                        com.neko.music.ui.theme.RoseRed
                    )
                )
            ),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            LogoIcon(
                scale = scale.value,
                alpha = alpha.value
            )

            Spacer(modifier = Modifier.height(32.dp))

            AppTitle(
                alpha = alpha.value
            )

            Spacer(modifier = Modifier.height(16.dp))

            AppSubtitle(
                alpha = alpha.value
            )

            Spacer(modifier = Modifier.height(24.dp))

            LoadingDot(
                alpha = alpha.value
            )
        }
    }
}

@Composable
fun LogoIcon(scale: Float, alpha: Float) {
    Box(
        modifier = Modifier
            .size(120.dp)
            .scale(scale)
            .alpha(alpha)
            .background(
                color = Color.White,
                shape = androidx.compose.foundation.shape.CircleShape
            ),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(
            text = "♪",
            fontSize = 64.sp,
            modifier = Modifier.alpha(alpha)
        )
    }
}

@Composable
fun AppTitle(alpha: Float) {
    Text(
        text = "Neko云音乐",
        fontSize = 32.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        color = Color.White,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.alpha(alpha)
    )
}

@Composable
fun AppSubtitle(alpha: Float) {
    Text(
        text = androidx.compose.ui.res.stringResource(id = R.string.splash_slogan),
        fontSize = 16.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        color = Color.White.copy(alpha = 0.8f),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.alpha(alpha)
    )
}

@Composable
fun LoadingDot(alpha: Float) {
    Box(
        modifier = Modifier
            .size(4.dp)
            .background(
                color = Color.White,
                shape = androidx.compose.foundation.shape.CircleShape
            )
            .alpha(alpha)
    )
}

