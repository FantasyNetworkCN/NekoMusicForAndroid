package com.neko.music.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.R
import com.neko.music.data.api.MusicApi
import com.neko.music.data.model.Music
import com.neko.music.service.MusicPlayerManager
import com.neko.music.ui.components.AppPageBackgroundImage
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.PlaylistPageDarkTintOverlay
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.isAppDarkTheme
import com.neko.music.util.UrlConfig
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

private const val MAX_RECORDING_SECONDS = 20
private const val SNAPSHOT_INTERVAL_SECONDS = 4

private enum class RecognitionStage {
    Ready,
    Recording,
    Matched,
    NoMatch,
    Error,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicRecognitionScreen(
    onBackClick: () -> Unit,
    onMusicClick: (Music) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val api = remember(context) { MusicApi(context.applicationContext) }
    val playerManager = remember(context) { MusicPlayerManager.getInstance(context) }
    val recorder = remember(context) { RecognitionAudioRecorder(context.applicationContext) }
    val scheme = MaterialTheme.colorScheme
    val isDark = isAppDarkTheme()
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)

    var stage by remember { mutableStateOf(RecognitionStage.Ready) }
    var match by remember { mutableStateOf<MusicApi.RecognitionMatch?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var recordingStartedAt by remember { mutableLongStateOf(0L) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var recognitionJob by remember { mutableStateOf<Job?>(null) }

    val permissionDeniedText = stringResource(R.string.recognition_permission_denied)
    val recordingFailedText = stringResource(R.string.recognition_recording_failed)
    val requestFailedText = stringResource(R.string.recognition_request_failed)

    fun beginRecording() {
        recognitionJob?.cancel()
        match = null
        errorMessage = null
        playerManager.pause()
        try {
            val snapshots = recorder.start(scope)
            recordingStartedAt = SystemClock.elapsedRealtime()
            elapsedSeconds = 0
            stage = RecognitionStage.Recording
            recognitionJob = scope.launch {
                try {
                    for (snapshot in snapshots) {
                        val result = try {
                            api.recognizeMusic(snapshot)
                        } finally {
                            snapshot.delete()
                        }
                        var matched = false
                        result.fold(
                            onSuccess = { response ->
                                if (response.match != null) {
                                    match = response.match
                                    errorMessage = null
                                    stage = RecognitionStage.Matched
                                    matched = true
                                }
                            },
                            onFailure = { error ->
                                recorder.cancel()
                                errorMessage = error.message?.takeIf { it.isNotBlank() } ?: requestFailedText
                                stage = RecognitionStage.Error
                            },
                        )
                        if (matched || result.isFailure) {
                            recorder.cancel()
                            return@launch
                        }
                    }
                    if (stage == RecognitionStage.Recording) {
                        stage = RecognitionStage.NoMatch
                    }
                } catch (_: CancellationException) {
                    // User stopped listening or left the page.
                } catch (_: Exception) {
                    recorder.cancel()
                    errorMessage = recordingFailedText
                    stage = RecognitionStage.Error
                }
            }
        } catch (_: Exception) {
            recorder.cancel()
            errorMessage = recordingFailedText
            stage = RecognitionStage.Error
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            beginRecording()
        } else {
            errorMessage = permissionDeniedText
            stage = RecognitionStage.Error
        }
    }

    fun requestRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            beginRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stopListening() {
        recognitionJob?.cancel()
        recognitionJob = null
        recorder.cancel()
        elapsedSeconds = 0
        stage = RecognitionStage.Ready
    }

    LaunchedEffect(stage, recordingStartedAt) {
        if (stage == RecognitionStage.Recording) {
            while (stage == RecognitionStage.Recording) {
                elapsedSeconds = ((SystemClock.elapsedRealtime() - recordingStartedAt) / 1000L)
                    .toInt()
                    .coerceAtMost(MAX_RECORDING_SECONDS)
                if (elapsedSeconds >= MAX_RECORDING_SECONDS) {
                    // Close the microphone at the hard limit, but let the recognition
                    // coroutine finish uploading the last snapshot already queued.
                    recorder.stopAtLimit()
                    break
                }
                delay(250)
            }
        }
    }

    DisposableEffect(recorder, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && stage == RecognitionStage.Recording) {
                recognitionJob?.cancel()
                recognitionJob = null
                recorder.cancel()
                elapsedSeconds = 0
                stage = RecognitionStage.Ready
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            recognitionJob?.cancel()
            recorder.cancel()
        }
    }

    val titleColor = if (isDark) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface
    val mutedColor = if (isDark) Color(0xFFB8B8D1).copy(alpha = 0.82f) else scheme.onSurfaceVariant

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop),
        ) {
            AppPageBackgroundImage(modifier = Modifier.fillMaxSize())
            PlaylistPageDarkTintOverlay(enabled = isDark)
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.music_recognition),
                            color = titleColor,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = mutedColor,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                )
            },
        ) { paddingValues ->
            CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(42.dp))

                    RecognitionControl(
                        stage = stage,
                        elapsedSeconds = elapsedSeconds,
                        isDark = isDark,
                        enabled = true,
                        onClick = {
                            when (stage) {
                                RecognitionStage.Recording -> stopListening()
                                else -> requestRecording()
                            }
                        },
                    )

                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = when (stage) {
                            RecognitionStage.Ready -> stringResource(R.string.recognition_ready)
                            RecognitionStage.Recording -> stringResource(R.string.recognition_listening, elapsedSeconds)
                            RecognitionStage.Matched -> stringResource(R.string.recognition_success)
                            RecognitionStage.NoMatch -> stringResource(R.string.recognition_no_match)
                            RecognitionStage.Error -> errorMessage ?: requestFailedText
                        },
                        color = if (stage == RecognitionStage.Error) scheme.error else titleColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(28.dp))
                    AnimatedVisibility(
                        visible = stage == RecognitionStage.Matched && match != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        match?.let { value ->
                            RecognitionMatchRow(
                                match = value,
                                isDark = isDark,
                                onClick = { onMusicClick(value.music) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecognitionControl(
    stage: RecognitionStage,
    elapsedSeconds: Int,
    isDark: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recognitionPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recognitionPulseAlpha",
    )
    val tint = LiquidGlassDefaults.screenListCard
    val accent = when (stage) {
        RecognitionStage.Matched -> Color(0xFF55C98B)
        RecognitionStage.Recording -> RoseRed
        else -> RoseRed
    }

    Box(
        modifier = Modifier.size(196.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 1.5.dp.toPx()
            drawCircle(
                color = accent.copy(alpha = if (stage == RecognitionStage.Recording) 0.18f * pulse else 0.11f),
                radius = size.minDimension * 0.48f,
                style = Stroke(width = stroke),
            )
            drawCircle(
                color = accent.copy(alpha = if (stage == RecognitionStage.Recording) 0.34f * pulse else 0.18f),
                radius = size.minDimension * 0.41f,
                style = Stroke(width = stroke),
            )
            if (stage == RecognitionStage.Recording) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * (elapsedSeconds.toFloat() / MAX_RECORDING_SECONDS),
                    useCenter = false,
                    topLeft = Offset(size.width * 0.09f, size.height * 0.09f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.82f, size.height * 0.82f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        GlassSurface(
            modifier = Modifier
                .size(132.dp)
                .clickable(enabled = enabled, onClick = onClick),
            shape = CircleShape,
            backgroundAlpha = (tint.background(isDark) + 0.10f).coerceAtMost(0.50f),
            borderAlpha = (tint.border(isDark) + 0.10f).coerceAtMost(0.35f),
            highlightAlpha = tint.highlight(isDark),
            borderColor = accent,
            liquidBlur = 10.dp,
            liquidLensHeight = 18.dp,
            liquidLensAmount = 30.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                when (stage) {
                    RecognitionStage.Recording -> Icon(
                        painter = painterResource(R.drawable.ic_stop_recording),
                        contentDescription = stringResource(R.string.recognition_stop),
                        tint = accent,
                        modifier = Modifier.size(38.dp),
                    )
                    RecognitionStage.Matched -> Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.recognition_success),
                        tint = accent,
                        modifier = Modifier.size(42.dp),
                    )
                    RecognitionStage.Error, RecognitionStage.NoMatch -> Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.retry),
                        tint = accent,
                        modifier = Modifier.size(38.dp),
                    )
                    RecognitionStage.Ready -> Icon(
                        painter = painterResource(R.drawable.ic_music_recognition),
                        contentDescription = stringResource(R.string.recognition_start),
                        tint = accent,
                        modifier = Modifier.size(42.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecognitionMatchRow(
    match: MusicApi.RecognitionMatch,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = LiquidGlassDefaults.screenListCard
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        backgroundAlpha = tint.background(isDark),
        borderAlpha = tint.border(isDark),
        highlightAlpha = tint.highlight(isDark),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = UrlConfig.buildMusicCoverUrl(match.music.id, match.music.coverFilePath),
                contentDescription = stringResource(R.string.content_description_cover),
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = match.music.title,
                    color = if (isDark) Color(0xFFF0F0F5) else scheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = match.music.artist,
                    color = if (isDark) Color(0xFFB8B8D1).copy(alpha = 0.86f) else scheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.recognition_confidence,
                        (match.confidence * 100).toInt().coerceIn(0, 100),
                    ),
                    color = RoseRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.recognition_open_result),
                tint = RoseRed,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private class RecognitionAudioRecorder(context: Context) {
    private val cacheDir = context.applicationContext.cacheDir
    @Volatile private var recording = false
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var snapshotChannel: Channel<File>? = null

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope): ReceiveChannel<File> {
        cancel()
        val minimumBuffer = AudioRecord.getMinBufferSize(
            STREAM_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minimumBuffer <= 0) {
            throw IOException("Audio recording is not supported")
        }

        val bufferSize = maxOf(minimumBuffer * 2, 4096)
        val created = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(STREAM_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()
        if (created.state != AudioRecord.STATE_INITIALIZED) {
            created.release()
            throw IOException("Audio recorder failed to initialize")
        }

        val channel = Channel<File>(
            capacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
            onUndeliveredElement = { it.delete() },
        )
        audioRecord = created
        snapshotChannel = channel
        recording = true
        try {
            created.startRecording()
        } catch (error: Exception) {
            recording = false
            created.release()
            audioRecord = null
            snapshotChannel = null
            channel.cancel()
            throw error
        }

        captureJob = scope.launch(Dispatchers.IO) {
            val maximumBytes = STREAM_SAMPLE_RATE * PCM_BYTES_PER_SAMPLE * MAX_RECORDING_SECONDS
            val intervalBytes = STREAM_SAMPLE_RATE * PCM_BYTES_PER_SAMPLE * SNAPSHOT_INTERVAL_SECONDS
            val pcm = ByteArrayOutputStream(maximumBytes)
            val readBuffer = ByteArray(bufferSize)
            var nextSnapshotBytes = intervalBytes
            try {
                while (isActive && recording && pcm.size() < maximumBytes) {
                    val requested = minOf(readBuffer.size, maximumBytes - pcm.size())
                    val read = created.read(readBuffer, 0, requested, AudioRecord.READ_BLOCKING)
                    if (read <= 0) {
                        if (!recording || !isActive) break
                        throw IOException("Audio capture failed: $read")
                    }
                    pcm.write(readBuffer, 0, read)

                    val reachedMaximum = pcm.size() >= maximumBytes
                    if (pcm.size() >= nextSnapshotBytes || reachedMaximum) {
                        val snapshot = writeWaveSnapshot(pcm.toByteArray())
                        if (!channel.trySend(snapshot).isSuccess) {
                            snapshot.delete()
                        }
                        while (nextSnapshotBytes <= pcm.size()) {
                            nextSnapshotBytes += intervalBytes
                        }
                    }
                }
            } catch (error: Exception) {
                if (recording && isActive) {
                    channel.close(error)
                }
            } finally {
                if (audioRecord === created) {
                    recording = false
                    audioRecord = null
                    captureJob = null
                    snapshotChannel = null
                }
                try {
                    if (created.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        created.stop()
                    }
                } catch (_: IllegalStateException) {
                    // Recorder is already stopped.
                }
                created.release()
                channel.close()
            }
        }
        return channel
    }

    fun cancel() {
        recording = false
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) {
            // Recorder was not running or has already stopped.
        }
        captureJob?.cancel()
        snapshotChannel?.cancel()
        captureJob = null
        snapshotChannel = null
    }

    /** Stops microphone capture at the duration limit without cancelling queued uploads. */
    fun stopAtLimit() {
        if (!recording) return
        recording = false
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) {
            // Recorder was not running or has already stopped.
        }
    }

    fun isRecording(): Boolean = recording

    private fun writeWaveSnapshot(pcm: ByteArray): File {
        val target = File.createTempFile("music-recognition-", ".wav", cacheDir)
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
                header.putInt(STREAM_SAMPLE_RATE)
                header.putInt(STREAM_SAMPLE_RATE * PCM_BYTES_PER_SAMPLE)
                header.putShort(PCM_BYTES_PER_SAMPLE.toShort())
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

    private companion object {
        const val STREAM_SAMPLE_RATE = 16_000
        const val PCM_BYTES_PER_SAMPLE = 2
        const val WAVE_HEADER_BYTES = 44
    }
}
