package com.neko.music.quicksettings

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.neko.music.MainActivity
import com.neko.music.R

abstract class NekoMusicTileService : TileService() {

    protected abstract val destination: String
    protected abstract val tileLabelRes: Int
    protected abstract val tileIconRes: Int

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(tileLabelRes)
            icon = Icon.createWithResource(this@NekoMusicTileService, tileIconRes)
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("nekomusic://$destination")
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                destination.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

class SearchTileService : NekoMusicTileService() {
    override val destination: String = "search"
    override val tileLabelRes: Int = R.string.search
    override val tileIconRes: Int = R.drawable.ic_quick_search
}

class QrScanTileService : NekoMusicTileService() {
    override val destination: String = "qr"
    override val tileLabelRes: Int = R.string.qr_scan
    override val tileIconRes: Int = R.drawable.ic_qr_scan
}

class RecognitionTileService : NekoMusicTileService() {
    override val destination: String = "recognition"
    override val tileLabelRes: Int = R.string.music_recognition
    override val tileIconRes: Int = R.drawable.ic_music_recognition
}
