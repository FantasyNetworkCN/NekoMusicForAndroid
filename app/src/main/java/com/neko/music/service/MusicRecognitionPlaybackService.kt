package com.neko.music.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.neko.music.BuildConfig
import com.neko.music.R
import com.neko.music.data.api.MusicApi
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Keeps microphone-based recognition alive while the recognition screen is backgrounded. */
class MusicRecognitionPlaybackService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var captureJob: Job? = null
    private var recognitionJob: Job? = null
    private var pcmChannel: Channel<ByteArray>? = null
    private var audioRecord: AudioRecord? = null
    private var recording = false
    private var foregroundStarted = false
    private var floatingWindow: RecognitionFloatingWindow? = null
    private var floatingWindowRequested = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var stopAfterResultJob: Job? = null
    private var terminalStatusSent = false
    private var audioExporter: RecognitionAudioExporter? = null
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
                stopAfterResultJob?.cancel()
                stopAfterResultJob = null
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
                    stopAfterResultJob?.cancel()
                    stopAfterResultJob = null
                    val maxSeconds = intent.getIntExtra(EXTRA_MAX_SECONDS, MAX_SECONDS).coerceIn(1, MAX_SECONDS)
                    terminalStatusSent = false
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
                } else {
                    showFloatingWindowIfAllowed()
                }
            }
            ACTION_SHOW_OVERLAY -> {
                if (captureJob != null || recognitionJob != null) {
                    showFloatingWindowIfAllowed()
                } else {
                    stopSelf()
                }
            }
            ACTION_HIDE_OVERLAY -> {
                hideFloatingWindow()
                if (captureJob == null && recognitionJob == null) stopSelf()
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
        Toast.makeText(
            this,
            getString(R.string.recognition_background_toast),
            Toast.LENGTH_SHORT,
        ).show()
        acquireKeepAliveLocks()
        showFloatingWindowIfAllowed()
        if (BuildConfig.DEBUG) {
            audioExporter = RecognitionAudioExporter(applicationContext)
        }

        val maxBytes = SAMPLE_RATE * BYTES_PER_SAMPLE * maxSeconds
        val channel = Channel<ByteArray>(capacity = 64)
        pcmChannel = channel
        recognitionJob = serviceScope.launch(Dispatchers.IO) {
            try {
                api.recognizeMusicStream(channel).fold(
                    onSuccess = { result ->
                        val matched = result.match
                        if (matched != null) {
                            sendMatch(matched)
                        } else {
                            finishWithStatus(STATUS_NO_MATCH, "未在当前曲库识别到歌曲")
                        }
                    },
                    onFailure = { error ->
                        finishWithStatus(STATUS_ERROR, error.message ?: "识曲请求失败")
                    }
                )
            } catch (_: CancellationException) {
                // Explicit stop or service destruction.
            }
        }
        captureJob = serviceScope.launch(Dispatchers.IO) {
            val readBuffer = ByteArray(maxOf(minimumBuffer * 2, 4096))
            val startedAt = System.currentTimeMillis()
            var capturedBytes = 0
            try {
                while (isActive && recording
                    && capturedBytes < maxBytes
                    && (System.currentTimeMillis() - startedAt) < maxSeconds * 1_000L) {
                    val requested = minOf(readBuffer.size, maxBytes - capturedBytes)
                    val read = created.read(readBuffer, 0, requested, AudioRecord.READ_BLOCKING)
                    if (read <= 0) {
                        if (!recording || !isActive) break
                        throw IOException("后台录音读取失败: $read")
                    }
                    capturedBytes += read
                    audioExporter?.append(readBuffer, 0, read)
                    channel.send(readBuffer.copyOf(read))
                    val elapsed = ((System.currentTimeMillis() - startedAt) / 1000L).toInt()
                    serviceScope.launch {
                        floatingWindow?.updateRecording(
                            elapsedSeconds = elapsed.coerceAtMost(maxSeconds),
                            maxSeconds = maxSeconds,
                        )
                    }
                }
            } catch (_: CancellationException) {
                // Explicit stop or service destruction.
            } catch (error: Exception) {
                channel.close(error)
            } finally {
                channel.close()
                recording = false
                if (audioRecord === created) audioRecord = null
                try {
                    if (created.recordingState == AudioRecord.RECORDSTATE_RECORDING) created.stop()
                } catch (_: IllegalStateException) {
                    // Already stopped.
                }
                created.release()
                if (pcmChannel === channel) pcmChannel = null
                if (captureJob?.isCompleted == true) captureJob = null
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
        terminalStatusSent = true
        sendBroadcast(intent)
        finishCaptureKeepAlive()
        if (floatingWindowRequested && floatingWindow?.isVisible == true) {
            val title = match.music.title
            val artist = match.music.artist
            val confidence = match.confidence
            Handler(Looper.getMainLooper()).post {
                floatingWindow?.showMatch(title, artist, confidence)
            }
            scheduleServiceStop(RESULT_HOLD_MS)
        } else {
            stopCaptureAndService(null)
        }
    }

    private fun finishWithStatus(status: String, message: String) {
        terminalStatusSent = true
        sendBroadcast(baseResultIntent(status).putExtra(EXTRA_MESSAGE, message))
        finishCaptureKeepAlive()
        if (floatingWindowRequested && floatingWindow?.isVisible == true) {
            val title = if (status == STATUS_NO_MATCH) {
                getString(R.string.recognition_float_no_match)
            } else {
                getString(R.string.recognition_float_error)
            }
            val subtitle = if (status == STATUS_NO_MATCH) {
                getString(R.string.recognition_float_no_match_tip)
            } else {
                message
            }
            val error = status != STATUS_NO_MATCH
            Handler(Looper.getMainLooper()).post {
                floatingWindow?.showMessage(title, subtitle, error)
            }
            scheduleServiceStop(MESSAGE_HOLD_MS)
        } else {
            stopCaptureAndService(null)
        }
    }

    private fun finishCaptureKeepAlive() {
        recording = false
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) {
            // Already stopped.
        }
        captureJob?.cancel()
        captureJob = null
        pcmChannel?.cancel()
        pcmChannel = null
    }

    private fun scheduleServiceStop(delayMillis: Long) {
        stopAfterResultJob?.cancel()
        stopAfterResultJob = serviceScope.launch {
            delay(delayMillis)
            stopCaptureAndService(null)
        }
    }

    private fun stopCaptureAndService(status: String?) {
        stopAfterResultJob?.cancel()
        stopAfterResultJob = null
        recording = false
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) {
            // Already stopped.
        }
        captureJob?.cancel()
        captureJob = null
        pcmChannel?.cancel()
        pcmChannel = null
        recognitionJob?.cancel()
        recognitionJob = null
        releaseKeepAliveLocks()
        publishDebugAudio()
        if (status != null) sendBroadcast(baseResultIntent(status))
        hideFloatingWindow()
        stopSelf()
    }

    private fun publishDebugAudio() {
        val exporter = audioExporter ?: return
        audioExporter = null
        exporter.finishAndPublish().fold(
            onSuccess = { path ->
                Log.i(TAG, "Debug recognition audio saved to $path")
                showToastOnMain(getString(R.string.recognition_test_audio_saved, path))
            },
            onFailure = { error ->
                Log.e(TAG, "Unable to save debug recognition audio", error)
                showToastOnMain(
                    getString(
                        R.string.recognition_test_audio_save_failed,
                        error.message ?: "unknown error",
                    ),
                )
            },
        )
    }

    private fun showToastOnMain(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
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
            .setContentTitle(getString(R.string.recognition_background_title))
            .setContentText(getString(R.string.recognition_background_text, maxSeconds))
            .setOngoing(true)
            .addAction(
                R.drawable.ic_stop_recording,
                getString(R.string.recognition_background_stop),
                stopIntent,
            )
            .build()
    }

    private fun showFloatingWindowIfAllowed() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission is not granted; background recognition will use notification only")
            floatingWindowRequested = false
            return
        }
        floatingWindowRequested = true
        val window = floatingWindow ?: RecognitionFloatingWindow(
            context = this,
            onStop = ::closeFromFloatingWindow,
            onOpenApp = ::openRecognitionApp,
        ).also { floatingWindow = it }
        window.show()
    }

    private fun hideFloatingWindow() {
        floatingWindowRequested = false
        Handler(Looper.getMainLooper()).post {
            floatingWindow?.hide()
        }
    }

    private fun closeFromFloatingWindow() {
        stopCaptureAndService(if (terminalStatusSent) null else STATUS_STOPPED)
    }

    private fun openRecognitionApp() {
        floatingWindowRequested = false
        floatingWindow?.hide()
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching { launchIntent?.let(::startActivity) }
        scheduleServiceStop(800L)
    }

    private fun acquireKeepAliveLocks() {
        if (wakeLock == null) {
            val powerManager = getSystemService(PowerManager::class.java)
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$packageName:recognition",
            )?.apply {
                setReferenceCounted(false)
                acquire((MAX_SECONDS + 10L) * 1_000L)
            }
        }
        if (wifiLock == null) {
            val wifiManager = applicationContext.getSystemService(WifiManager::class.java)
            @Suppress("DEPRECATION")
            val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            } else {
                WifiManager.WIFI_MODE_FULL
            }
            wifiLock = wifiManager?.createWifiLock(lockMode, "$packageName:recognition")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseKeepAliveLocks() {
        wakeLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        wifiLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        wakeLock = null
        wifiLock = null
    }

    override fun onDestroy() {
        stopCaptureAndService(null)
        floatingWindow?.remove()
        floatingWindow = null
        releaseKeepAliveLocks()
        api.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.neko.music.action.START_RECOGNITION_PLAYBACK"
        const val ACTION_PREPARE = "com.neko.music.action.PREPARE_RECOGNITION_PLAYBACK"
        const val ACTION_SHOW_OVERLAY = "com.neko.music.action.SHOW_RECOGNITION_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.neko.music.action.HIDE_RECOGNITION_OVERLAY"
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
        private const val MAX_SECONDS = 20
        private const val RESULT_HOLD_MS = 8_000L
        private const val MESSAGE_HOLD_MS = 4_500L
        private const val TAG = "MusicRecognitionService"
    }
}
