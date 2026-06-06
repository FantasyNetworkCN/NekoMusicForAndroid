package com.neko.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.neko.music.R
import com.neko.music.service.MusicPlayerManager

class MusicWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.neko.music.action.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.neko.music.action.PREVIOUS"
        const val ACTION_NEXT = "com.neko.music.action.NEXT"
        const val ACTION_UPDATE_WIDGET = "com.neko.music.action.UPDATE_WIDGET"
        const val ACTION_OPEN_PLAYER = "com.neko.music.action.OPEN_PLAYER"
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MusicWidgetPreviewRegistrar.register(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        // 确保 Service 正在运行，以便处理播放逻辑
        if (intent.action in listOf(ACTION_PLAY_PAUSE, ACTION_PREVIOUS, ACTION_NEXT)) {
            com.neko.music.MusicPlayerService.startService(context)
        }

        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                val playerManager = MusicPlayerManager.getInstance(context)
                if (playerManager.isPlaying.value) {
                    playerManager.pause()
                } else {
                    playerManager.togglePlayPause()
                }
                updateAllWidgets(context)
            }
            ACTION_PREVIOUS -> {
                MusicPlayerManager.getInstance(context).previous()
                updateAllWidgets(context)
            }
            ACTION_NEXT -> {
                MusicPlayerManager.getInstance(context).next()
                updateAllWidgets(context)
            }
            ACTION_OPEN_PLAYER -> {
                // 打开应用并跳转到播放页面
                val playerManager = MusicPlayerManager.getInstance(context)
                val musicId = playerManager.currentMusicId.value
                val title = playerManager.currentMusicTitle.value
                val artist = playerManager.currentMusicArtist.value

                if (musicId != null && title != null && artist != null) {
                    val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                    val encodedArtist = java.net.URLEncoder.encode(artist, "UTF-8")
                    val openIntent = Intent(context, com.neko.music.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        data = android.net.Uri.parse("neko://player/$musicId/$encodedTitle/$encodedArtist")
                    }
                    context.startActivity(openIntent)
                } else {
                    // 没有播放的音乐，只打开应用
                    val openIntent = Intent(context, com.neko.music.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(openIntent)
                }
            }
            ACTION_UPDATE_WIDGET -> {
                updateAllWidgets(context)
            }
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val playerManager = MusicPlayerManager.getInstance(context)
        val views = RemoteViews(context.packageName, R.layout.music_widget)

        // 更新歌曲信息
        val title = playerManager.currentMusicTitle.value ?: "Neko云音乐"
        val artist = playerManager.currentMusicArtist.value ?: "暂无播放"
        views.setTextViewText(R.id.widget_song_title, title)
        views.setTextViewText(R.id.widget_artist_name, artist)

        // 更新播放/暂停按钮图标
        val playPauseIcon = if (playerManager.isPlaying.value) {
            R.drawable.pause
        } else {
            R.drawable.play
        }
        views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)

        // 进度条
        val currentPosition = playerManager.currentPosition.value
        val duration = playerManager.duration.value
        if (duration > 0) {
            val progress = ((currentPosition.toFloat() / duration.toFloat()) * 100).toInt()
            views.setProgressBar(R.id.widget_progress_bar, 100, progress, false)
            views.setTextViewText(R.id.widget_current_time, formatTime(currentPosition))
            views.setTextViewText(R.id.widget_total_time, formatTime(duration))
        } else {
            val indeterminate = playerManager.isPlaying.value
            views.setProgressBar(R.id.widget_progress_bar, 100, 0, indeterminate)
            views.setTextViewText(R.id.widget_current_time, "0:00")
            views.setTextViewText(R.id.widget_total_time, "0:00")
        }

        // 异步加载封面
        val coverUrl = playerManager.currentMusicCover.value
        val musicId = playerManager.currentMusicId.value
        if (!coverUrl.isNullOrEmpty() && musicId != null) {
            val cacheManager = com.neko.music.data.cache.MusicCacheManager.getInstance(context)
            val cachedFile = cacheManager.getExistingCoverCacheFile(musicId)
            
            // 使用协程在 IO 线程加载图片（这里简单起见使用 Coil 的 ImageLoader）
            // 注意：RemoteViews 只能在主线程更新，所以这里我们只是触发加载
            // 实际上在 Widget 中加载图片通常需要一个专门的 Service 或者使用 Glide/Coil 的 Target
            // 这里我们利用 MusicWidgetUpdateService 或者直接在 Provider 中处理
            
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val request = coil3.request.ImageRequest.Builder(context)
                        .data(cachedFile ?: coverUrl)
                        .size(128, 128)
                        .build()
                    val imageLoader = coil3.ImageLoader.Builder(context).build()
                    val result = imageLoader.execute(request)
                    if (result is coil3.request.SuccessResult) {
                        val bitmap = androidx.core.graphics.drawable.toBitmap(result.image.asDrawable(context.resources))
                        views.setImageViewBitmap(R.id.widget_cover, bitmap)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MusicWidgetProvider", "加载封面失败", e)
                }
            }
        } else {
            views.setImageViewResource(R.id.widget_cover, R.drawable.music)
        }

        // 设置点击事件
        views.setOnClickPendingIntent(R.id.widget_play_pause, getPendingIntent(context, ACTION_PLAY_PAUSE))
        views.setOnClickPendingIntent(R.id.widget_previous, getPendingIntent(context, ACTION_PREVIOUS))
        views.setOnClickPendingIntent(R.id.widget_next, getPendingIntent(context, ACTION_NEXT))
        views.setOnClickPendingIntent(R.id.widget_root, getPendingIntent(context, ACTION_OPEN_PLAYER))

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun formatTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / (1000 * 60)) % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(
            android.content.ComponentName(context, MusicWidgetProvider::class.java)
        )
        onUpdate(context, appWidgetManager, appWidgetIds)
    }

    private fun getPendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, MusicWidgetProvider::class.java).apply {
            this.action = action
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }
}