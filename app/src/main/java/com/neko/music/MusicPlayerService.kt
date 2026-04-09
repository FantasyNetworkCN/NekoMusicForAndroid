package com.neko.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.neko.music.R
import com.neko.music.service.MusicPlayerManager
import kotlinx.coroutines.launch

class MusicPlayerService : Service() {

    private lateinit var playerManager: MusicPlayerManager
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private var isForeground = false
    
    // 防止快速连续点击桌面歌词按钮
    private var isProcessingLyricToggle = false

    companion object {
        private const val CHANNEL_ID = "music_player_channel"
        private const val NOTIFICATION_ID = 1

        fun startService(context: Context) {
            val intent = Intent(context, MusicPlayerService::class.java)
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        playerManager = MusicPlayerManager.getInstance(this)
        createNotificationChannel()

        // 确保 MediaSession 已初始化
        playerManager.ensureMediaSessionInitialized(this)

        // 初始化桌面歌词状态
        val lyricPrefs = getSharedPreferences("desktop_lyric", Context.MODE_PRIVATE)
        val lyricEnabled = lyricPrefs.getBoolean("desktop_lyric_enabled", false)
        playerManager.updateDesktopLyricState(lyricEnabled)

        // 初始化焦点锁定状态
        val focusLockPrefs = getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
        val focusLockEnabled = focusLockPrefs.getBoolean("focus_lock_enabled", false)
        playerManager.updateAudioAttributes(focusLockEnabled)

        // 启动前台服务以确保后台播放正常
        startForeground(NOTIFICATION_ID, createMusicNotification())

        // 监听定时关闭剩余时间变化
        kotlinx.coroutines.GlobalScope.launch {
            playerManager.sleepTimerRemainingSeconds.collect { remainingSeconds ->
                updateMusicNotification()
            }
        }

        // 监听播放状态变化
        kotlinx.coroutines.GlobalScope.launch {
            playerManager.isPlaying.collect { isPlaying ->
                updateMusicNotification()
                // 发送广播更新桌面组件
                val updateIntent = Intent(this@MusicPlayerService, com.neko.music.widget.MusicWidgetProvider::class.java).apply {
                    action = com.neko.music.widget.MusicWidgetProvider.ACTION_UPDATE_WIDGET
                }
                sendBroadcast(updateIntent)
            }
        }

        // 监听当前音乐变化
        kotlinx.coroutines.GlobalScope.launch {
            playerManager.currentMusicTitle.collect {
                updateMusicNotification()
                // 发送广播更新桌面组件
                val updateIntent = Intent(this@MusicPlayerService, com.neko.music.widget.MusicWidgetProvider::class.java).apply {
                    action = com.neko.music.widget.MusicWidgetProvider.ACTION_UPDATE_WIDGET
                }
                sendBroadcast(updateIntent)
            }
        }

        // 监听播放位置变化，实时更新进度条
        kotlinx.coroutines.GlobalScope.launch {
            playerManager.currentPosition.collect {
                // 发送广播更新桌面组件
                val updateIntent = Intent(this@MusicPlayerService, com.neko.music.widget.MusicWidgetProvider::class.java).apply {
                    action = com.neko.music.widget.MusicWidgetProvider.ACTION_UPDATE_WIDGET
                }
                sendBroadcast(updateIntent)
                
                // 更新悬浮窗
                val floatPrefs = getSharedPreferences("float_window", Context.MODE_PRIVATE)
                if (floatPrefs.getBoolean("fuck_china_os_enabled", false)) {
                    val floatUpdateIntent = Intent(this@MusicPlayerService, com.neko.music.floatwindow.FuckChinaOSFloatService::class.java).apply {
                        action = com.neko.music.floatwindow.FuckChinaOSFloatService.ACTION_UPDATE
                    }
                    startService(floatUpdateIntent)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                "ACTION_PLAY" -> {
                    if (!playerManager.isPlaying.value) {
                        playerManager.togglePlayPause()
                    }
                    // 更新通知栏
                    updateMusicNotification()
                }
                "ACTION_PAUSE" -> {
                    if (playerManager.isPlaying.value) {
                        playerManager.pause()
                    }
                    // 更新通知栏
                    updateMusicNotification()
                }
                "ACTION_PREVIOUS" -> {
                    playerManager.previous()
                    // 更新通知栏
                    updateMusicNotification()
                }
                "ACTION_NEXT" -> {
                    playerManager.next()
                    // 更新通知栏
                    updateMusicNotification()
                }
                "ACTION_TOGGLE_LYRIC" -> {
                    // 防止快速连续点击
                    if (isProcessingLyricToggle) {
                        android.util.Log.d("MusicPlayerService", "正在处理桌面歌词切换，忽略此次点击")
                        return@let
                    }
                    
                    isProcessingLyricToggle = true
                    
                    // 切换桌面歌词显示状态
                    val lyricPrefs = getSharedPreferences("desktop_lyric", Context.MODE_PRIVATE)
                    val isEnabled = lyricPrefs.getBoolean("desktop_lyric_enabled", false)
                    val newState = !isEnabled
                    
                    // 如果要开启但没有悬浮窗权限，先请求权限
                    if (newState && !android.provider.Settings.canDrawOverlays(this)) {
                        // 需要权限，但通知栏无法直接请求权限，所以先不开启
                        android.util.Log.d("MusicPlayerService", "桌面歌词需要悬浮窗权限")
                        isProcessingLyricToggle = false
                    } else {
                        // 直接切换状态
                        lyricPrefs.edit().putBoolean("desktop_lyric_enabled", newState).apply()
                        playerManager.updateDesktopLyricState(newState)

                        // 控制桌面歌词服务
                        val lyricServiceIntent = Intent(this, com.neko.music.desktoplyric.DesktopLyricService::class.java)
                        if (newState) {
                            lyricServiceIntent.action = com.neko.music.desktoplyric.DesktopLyricService.ACTION_SHOW
                            startService(lyricServiceIntent)
                        } else {
                            lyricServiceIntent.action = com.neko.music.desktoplyric.DesktopLyricService.ACTION_HIDE
                            startService(lyricServiceIntent)
                        }
                        
                        android.util.Log.d("MusicPlayerService", "桌面歌词已${if (newState) "开启" else "关闭"}")
                    }
                    
                    // 更新通知以反映当前状态
                    updateMusicNotification()
                    
                    // 延迟重置标志位，防止快速连续点击
                    kotlinx.coroutines.GlobalScope.launch {
                        kotlinx.coroutines.delay(500)
                        isProcessingLyricToggle = false
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 先删除旧的通知频道
            notificationManager.deleteNotificationChannel(CHANNEL_ID)

            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.music_playback),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.music_playback_notification)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                enableLights(true)
                lightColor = android.graphics.Color.BLUE
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createMusicNotification(): Notification {
        val title = playerManager.currentMusicTitle.value ?: "Neko云音乐"
        val artist = playerManager.currentMusicArtist.value ?: ""
        val isPlaying = playerManager.isPlaying.value
        val remainingSeconds = playerManager.sleepTimerRemainingSeconds.value

        // 创建点击通知打开应用的 PendingIntent
        val notificationIntent = Intent(this, MainActivity::class.java)
        notificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (remainingSeconds > 0) {
            val hours = remainingSeconds / 3600
            val minutes = (remainingSeconds % 3600) / 60
            val seconds = remainingSeconds % 60

            val timeText = buildString {
                if (hours > 0) append("${hours}小时")
                if (minutes > 0) append("${minutes}分钟")
                append("${seconds}${getString(R.string.sleep_timer_seconds)}")
            }

            "$artist - $timeText"
        } else {
            artist
        }

        // 创建播放控制按钮的 PendingIntent
        val playPauseIntent = Intent(this, MusicPlayerService::class.java).apply {
            action = if (isPlaying) "ACTION_PAUSE" else "ACTION_PLAY"
        }
        val playPausePendingIntent = PendingIntent.getService(
            this,
            1,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previousIntent = Intent(this, MusicPlayerService::class.java).apply {
            action = "ACTION_PREVIOUS"
        }
        val previousPendingIntent = PendingIntent.getService(
            this,
            2,
            previousIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, MusicPlayerService::class.java).apply {
            action = "ACTION_NEXT"
        }
        val nextPendingIntent = PendingIntent.getService(
            this,
            3,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 创建桌面歌词切换按钮的 PendingIntent
        val lyricIntent = Intent(this, MusicPlayerService::class.java).apply {
            action = "ACTION_TOGGLE_LYRIC"
        }
        val lyricPendingIntent = PendingIntent.getService(
            this,
            4,
            lyricIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 获取 MediaSession Token
        val mediaSessionToken = playerManager.getMediaSessionToken()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(android.app.Notification.CATEGORY_TRANSPORT)
            // 添加 MediaStyle 以显示播放控制按钮
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSessionToken)
                    .setShowActionsInCompactView(0, 1, 2, 3) // 在紧凑视图显示4个按钮（上一首、播放/暂停、下一首、词）
                    .setShowCancelButton(false)
            )
            // 添加播放控制按钮
            .addAction(
                android.R.drawable.ic_media_previous,
                "上一首",
                previousPendingIntent
            )
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "暂停" else "播放",
                playPausePendingIntent
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "下一首",
                nextPendingIntent
            )
            .addAction(
                R.drawable.ic_widget_lyric,
                "词",
                lyricPendingIntent
            )
            .build()
    }

    fun updateMusicNotification() {
        notificationManager.notify(NOTIFICATION_ID, createMusicNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        // 注意：不再释放 MusicPlayerManager，保持播放器始终活跃状态
        // 释放已被禁用以防止 "Ignoring messages sent after release" 错误
    }
}