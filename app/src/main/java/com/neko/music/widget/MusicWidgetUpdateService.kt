package com.neko.music.widget

import android.app.IntentService
import android.content.Context
import android.content.Intent
import com.neko.music.service.MusicPlayerManager

class MusicWidgetUpdateService : IntentService("MusicWidgetUpdateService") {

    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action ?: return

        when (action) {
            MusicWidgetProvider.ACTION_UPDATE_WIDGET -> {
                // 发送广播更新桌面组件
                val updateIntent = Intent(this, MusicWidgetProvider::class.java).apply {
                    this.action = MusicWidgetProvider.ACTION_UPDATE_WIDGET
                }
                sendBroadcast(updateIntent)
            }
        }
    }

    companion object {
        fun startUpdateService(context: Context) {
            val intent = Intent(context, MusicWidgetUpdateService::class.java).apply {
                action = MusicWidgetProvider.ACTION_UPDATE_WIDGET
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}