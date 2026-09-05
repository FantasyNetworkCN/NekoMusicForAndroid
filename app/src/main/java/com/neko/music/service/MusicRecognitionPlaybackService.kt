package com.neko.music.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.neko.music.R
import com.neko.music.data.api.MusicApi
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Keeps microphone-based recognition alive while the recognition screen is backgrounded. */
class MusicRecognitionPlaybackService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var captureJob: Job? = null
    private var uploadJob: Job? = null
    private var snapshotChannel: Channel<File>? = null
    private var audioRecord: AudioRecord? = null
    private var recording = false
    private var foregroundStarted = false
    private var overlay: LinearLayout? = null
    private var overlayText: TextView? = null
    private var overlayAdded = false
    private lateinit var api: MusicApi

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onCreate() {
        super.onCreate()
        api = MusicApi(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREPARE -> {
                val maxSeconds = intent.getIntExtra(EXTRA_MAX_SECONDS, MAX_SECONDS).coerceIn(1, MAX_SECONDS)
                try {
                    ensureForeground(maxSeconds)
                } catch (error: Exception) {
                    Log.e(TAG, "Unable to prepare foreground recognition service", error)
                    sendBroadcast(
                        baseResultIntent(STATUS_ERROR)
                            .putExtra(EXTRA_MESSAGE, error.message ?: "后台识音服务启动失败"),
                    )
                    stopSelf()
                }
            }
            ACTION_START -> {
                if (captureJob == null) {
                    val maxSeconds = intent.getIntExtra(EXTRA_MAX_SECONDS, MAX_SECONDS).coerceIn(1, MAX_SECONDS)
                    Log.d(TAG, "Starting background recognition, maxSeconds=$maxSeconds, overlay=${Settings.canDrawOverlays(this)}")
                    try {
                        ensureForeground(maxSeconds)
                        startMicrophoneCapture(maxSeconds)
                    } catch (error: Exception) {
                        // A missing microphone permission or an OEM foreground-service
                        // restriction must not crash the host application process.
                        Log.e(TAG, "Unable to start background recognition", error)
                        sendBroadcast(
                            baseResultIntent(STATUS_ERROR)
                                .putExtra(EXTRA_MESSAGE, error.message ?: "后台识音服务启动失败"),
                        )
                        stopSelf()
                    }
                }
            }
            ACTION_STOP -> stopCaptureAndService(STATUS_STOPPED)
        }
        return START_NOT_STICKY
    }

    private fun ensureForeground(maxSeconds: Int) {
        if (foregroundStarted) return
        startForeground(NOTIFICATION_ID, createNotification(maxSeconds))
        foregroundStarted = true
    }

    private fun startMicrophoneCapture(maxSeconds: Int) {
        if (Settings.canDrawOverlays(this)) {
            showOverlay()
        } else {
            Log.w(TAG, "Overlay permission is not granted; background recognition will use notification only")
        }

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val minimumBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minimumBuffer <= 0) {
            finishWithStatus(STATUS_ERROR, "设备不支持后台录音")
            return
        }
        val created = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minimumBuffer * 2, 4096))
            .build()
        if (created.state != AudioRecord.STATE_INITIALIZED) {
            created.release()
            finishWithStatus(STATUS_ERROR, "后台录音初始化失败")
            return
        }
        audioRecord = created
        recording = true
        try {
            created.startRecording()
        } catch (error: Exception) {
            created.release()
            audioRecord = null
            finishWithStatus(STATUS_ERROR, error.message ?: "后台录音启动失败")
            return
        }

        val maxBytes = SAMPLE_RATE * BYTES_PER_SAMPLE * maxSeconds
        val intervalBytes = SAMPLE_RATE * BYTES_PER_SAMPLE * SNAPSHOT_SECONDS
        val channel = Channel<File>(
            capacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
            onUndeliveredElement = { it.delete() },
        )
        snapshotChannel = channel
        uploadJob = serviceScope.launch(Dispatchers.IO) {
            try {
                for (snapshot in channel) {
                    val result = try {
                        api.recognizeMusic(snapshot)
                    } finally {
                        snapshot.delete()
                    }
                    val matched = result.getOrNull()?.match
                    if (matched != null) {
                        sendMatch(matched)
                        return@launch
                    }
                    if (result.isFailure) {
                        finishWithStatus(STATUS_ERROR, result.exceptionOrNull()?.message ?: "识曲请求失败")
                        return@launch
                    }
                }
            } catch (_: CancellationException) {
                // Explicit stop or service destruction.
            }
        }
        captureJob = serviceScope.launch(Dispatchers.IO) {
            val pcm = ByteArrayOutputStream(maxBytes)
            val readBuffer = ByteArray(maxOf(minimumBuffer * 2, 4096))
            var nextSnapshotBytes = intervalBytes
            val startedAt = System.currentTimeMillis()
            var reachedEnd = false
            try {
                while (isActive && recording
                    && pcm.size() < maxBytes
                    && (System.currentTimeMillis() - startedAt) < maxSeconds * 1_000L) {
                    val requested = minOf(readBuffer.size, maxBytes - pcm.size())
                    val read = created.read(readBuffer, 0, requested, AudioRecord.READ_BLOCKING)
                    if (read <= 0) {
                        if (!recording || !isActive) break
                        throw IOException("后台录音读取失败: $read")
                    }
                    pcm.write(readBuffer, 0, read)
                    val elapsed = ((System.currentTimeMillis() - startedAt) / 1000L).toInt()
                    serviceScope.launch { updateOverlay("正在后台识别 · ${elapsed.coerceAtMost(maxSeconds)} 秒") }
                    val reachedMaximum = pcm.size() >= maxBytes
                    if (pcm.size() >= nextSnapshotBytes || reachedMaximum) {
                        val snapshot = writeWaveSnapshot(pcm.toByteArray())
                        if (!channel.trySend(snapshot).isSuccess) {
                            snapshot.delete()
                        }
                        while (nextSnapshotBytes <= pcm.size()) nextSnapshotBytes += intervalBytes
                    }
                }
                reachedEnd = recording
            } catch (_: CancellationException) {
                // Explicit stop or service destruction.
            } catch (error: Exception) {
                if (recording) finishWithStatus(STATUS_ERROR, error.message ?: "后台录音失败")
            } finally {
                channel.close()
                if (reachedEnd && isActive) {
                    uploadJob?.join()
                    if (recording) finishWithStatus(STATUS_NO_MATCH, "未在当前曲库识别到歌曲")
                }
                recording = false
                if (audioRecord === created) audioRecord = null
                try {
                    if (created.recordingState == AudioRecord.RECORDSTATE_RECORDING) created.stop()
                } catch (_: IllegalStateException) {
                    // Already stopped.
                }
                created.release()
                if (snapshotChannel === channel) snapshotChannel = null
                if (uploadJob?.isCompleted == true) uploadJob = null
            }
        }
    }

    private fun sendMatch(match: MusicApi.RecognitionMatch) {
        val intent = baseResultIntent(STATUS_MATCHED).apply {
            putExtra(EXTRA_MUSIC_ID, match.music.id)
            putExtra(EXTRA_MUSIC_TITLE, match.music.title)
            putExtra(EXTRA_MUSIC_ARTIST, match.music.artist)
            putExtra(EXTRA_MUSIC_ALBUM, match.music.album)
            putExtra(EXTRA_MUSIC_DURATION, match.music.duration)
            putExtra(EXTRA_MUSIC_COVER, match.music.coverFilePath)
            putExtra(EXTRA_CONFIDENCE, match.confidence)
            putExtra(EXTRA_OFFSET_SECONDS, match.offsetSeconds)
            putExtra(EXTRA_SAMPLE_DURATION_SECONDS, match.sampleDurationSeconds)
        }
        sendBroadcast(intent)
        stopCaptureAndService(null)
    }

    private fun finishWithStatus(status: String, message: String) {
        sendBroadcast(baseResultIntent(status).putExtra(EXTRA_MESSAGE, message))
        stopCaptureAndService(null)
    }

    private fun stopCaptureAndService(status: String?) {
        recording = false
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) {
            // Already stopped.
        }
        captureJob?.cancel()
        captureJob = null
        snapshotChannel?.cancel()
        snapshotChannel = null
        uploadJob?.cancel()
        uploadJob = null
        if (status != null) sendBroadcast(baseResultIntent(status))
        stopSelf()
    }

    private fun baseResultIntent(status: String): Intent =
        Intent(ACTION_RESULT).setPackage(packageName).putExtra(EXTRA_STATUS, status)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "听歌识曲", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun createNotification(maxSeconds: Int): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MusicRecognitionPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_recognition)
            .setContentTitle("正在后台听歌识曲")
            .setContentText("后台录音将在 ${maxSeconds} 秒内完成")
            .setOngoing(true)
            .addAction(R.drawable.ic_stop_recording, "停止", stopIntent)
            .build()
    }

    private fun showOverlay() {
        if (overlayAdded) return
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 12, 12, 12)
            setBackgroundColor(0xEE202027.toInt())
        }
        val text = TextView(this).apply {
            text = "正在后台识别 · 0 秒"
            setTextColor(0xFFFFFFFF.toInt())
        }
        val stop = Button(this).apply {
            this.text = "停止"
            setOnClickListener { stopCaptureAndService(STATUS_STOPPED) }
        }
        root.addView(text, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(stop, LinearLayout.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT))
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 96 }
        try {
            manager.addView(root, params)
            overlay = root
            overlayText = text
            overlayAdded = true
        } catch (error: Exception) {
            // Overlay permission may be revoked between the check and addView;
            // some OEM WindowManagers throw BadTokenException instead.
            Log.w(TAG, "Unable to show recognition overlay", error)
        }
    }

    private fun updateOverlay(value: String) {
        overlayText?.text = value
    }

    private fun removeOverlay() {
        val root = overlay ?: return
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(root)
        } catch (_: Exception) {
            // View was already removed.
        }
        overlay = null
        overlayText = null
        overlayAdded = false
    }

    private fun writeWaveSnapshot(pcm: ByteArray): File {
        val target = File.createTempFile("music-recognition-playback-", ".wav", cacheDir)
        try {
            FileOutputStream(target).use { output ->
                val header = ByteBuffer.allocate(WAVE_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
                header.put("RIFF".toByteArray(Charsets.US_ASCII))
                header.putInt(36 + pcm.size)
                header.put("WAVE".toByteArray(Charsets.US_ASCII))
                header.put("fmt ".toByteArray(Charsets.US_ASCII))
                header.putInt(16)
                header.putShort(1.toShort())
                header.putShort(1.toShort())
                header.putInt(SAMPLE_RATE)
                header.putInt(SAMPLE_RATE * BYTES_PER_SAMPLE)
                header.putShort(BYTES_PER_SAMPLE.toShort())
                header.putShort(16.toShort())
                header.put("data".toByteArray(Charsets.US_ASCII))
                header.putInt(pcm.size)
                output.write(header.array())
                output.write(pcm)
            }
            return target
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }

    override fun onDestroy() {
        stopCaptureAndService(null)
        removeOverlay()
        api.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.neko.music.action.START_RECOGNITION_PLAYBACK"
        const val ACTION_PREPARE = "com.neko.music.action.PREPARE_RECOGNITION_PLAYBACK"
        const val ACTION_STOP = "com.neko.music.action.STOP_RECOGNITION_PLAYBACK"
        const val ACTION_RESULT = "com.neko.music.action.RECOGNITION_PLAYBACK_RESULT"
        const val EXTRA_MAX_SECONDS = "max_seconds"
        const val EXTRA_STATUS = "status"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_MUSIC_ID = "music_id"
        const val EXTRA_MUSIC_TITLE = "music_title"
        const val EXTRA_MUSIC_ARTIST = "music_artist"
        const val EXTRA_MUSIC_ALBUM = "music_album"
        const val EXTRA_MUSIC_DURATION = "music_duration"
        const val EXTRA_MUSIC_COVER = "music_cover"
        const val EXTRA_CONFIDENCE = "confidence"
        const val EXTRA_OFFSET_SECONDS = "offset_seconds"
        const val EXTRA_SAMPLE_DURATION_SECONDS = "sample_duration_seconds"
        const val STATUS_MATCHED = "matched"
        const val STATUS_NO_MATCH = "no_match"
        const val STATUS_ERROR = "error"
        const val STATUS_STOPPED = "stopped"
        private const val CHANNEL_ID = "music_recognition_playback"
        private const val NOTIFICATION_ID = 37
        private const val SAMPLE_RATE = 16_000
        private const val BYTES_PER_SAMPLE = 2
        private const val SNAPSHOT_SECONDS = 4
        private const val MAX_SECONDS = 20
        private const val WAVE_HEADER_BYTES = 44
        private const val TAG = "MusicRecognitionService"
    }
}
