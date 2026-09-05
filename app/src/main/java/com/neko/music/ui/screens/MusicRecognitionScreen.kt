package com.neko.music.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
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
import androidx.compose.material3.CircularProgressIndicator
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
import com.neko.music.ui.theme.SkyBlue
import com.neko.music.ui.theme.isAppDarkTheme
import com.neko.music.util.UrlConfig
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MIN_RECORDING_SECONDS = 3
private const val MAX_RECORDING_SECONDS = 20

private enum class RecognitionStage {
    Ready,
    Recording,
    Identifying,
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

    val permissionDeniedText = stringResource(R.string.recognition_permission_denied)
    val recordingFailedText = stringResource(R.string.recognition_recording_failed)
    val tooShortText = stringResource(R.string.recognition_too_short, MIN_RECORDING_SECONDS)
    val requestFailedText = stringResource(R.string.recognition_request_failed)

    fun beginRecording() {
        match = null
        errorMessage = null
        playerManager.pause()
        try {
            val output = File.createTempFile("music-recognition-", ".m4a", context.cacheDir)
            recorder.start(output)
            recordingStartedAt = SystemClock.elapsedRealtime()
            elapsedSeconds = 0
            stage = RecognitionStage.Recording
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

    fun finishRecording() {
        if (stage != RecognitionStage.Recording) return
        val recordedSeconds = ((SystemClock.elapsedRealtime() - recordingStartedAt) / 1000L).toInt()
        val recording = recorder.stop()
        if (recording == null) {
            errorMessage = recordingFailedText
            stage = RecognitionStage.Error
            return
        }
        if (recordedSeconds < MIN_RECORDING_SECONDS) {
            recording.delete()
            errorMessage = tooShortText
            stage = RecognitionStage.Error
            return
        }

        stage = RecognitionStage.Identifying
        scope.launch {
            try {
                api.recognizeMusic(recording).fold(
                    onSuccess = { result ->
                        match = result.match
                        errorMessage = null
                        stage = if (result.match == null) RecognitionStage.NoMatch else RecognitionStage.Matched
                    },
                    onFailure = { error ->
                        errorMessage = error.message?.takeIf { it.isNotBlank() } ?: requestFailedText
                        stage = RecognitionStage.Error
                    },
                )
            } finally {
                recording.delete()
            }
        }
    }

    LaunchedEffect(stage, recordingStartedAt) {
        if (stage == RecognitionStage.Recording) {
            while (stage == RecognitionStage.Recording) {
                elapsedSeconds = ((SystemClock.elapsedRealtime() - recordingStartedAt) / 1000L)
                    .toInt()
                    .coerceAtMost(MAX_RECORDING_SECONDS)
                if (elapsedSeconds >= MAX_RECORDING_SECONDS) {
                    finishRecording()
                    break
                }
                delay(250)
            }
        }
    }

    DisposableEffect(recorder, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && stage == RecognitionStage.Recording) {
                recorder.cancel()
                elapsedSeconds = 0
                stage = RecognitionStage.Ready
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                        enabled = stage != RecognitionStage.Identifying,
                        onClick = {
                            when (stage) {
                                RecognitionStage.Recording -> finishRecording()
                                RecognitionStage.Identifying -> Unit
                                else -> requestRecording()
                            }
                        },
                    )

                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = when (stage) {
                            RecognitionStage.Ready -> stringResource(R.string.recognition_ready)
                            RecognitionStage.Recording -> stringResource(R.string.recognition_listening, elapsedSeconds)
                            RecognitionStage.Identifying -> stringResource(R.string.recognition_identifying)
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
        RecognitionStage.Identifying -> SkyBlue
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
                    RecognitionStage.Identifying -> CircularProgressIndicator(
                        modifier = Modifier.size(38.dp),
                        color = accent,
                        strokeWidth = 3.dp,
                    )
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
    private val appContext = context.applicationContext
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var recording = false

    fun start(file: File) {
        cancel()
        @Suppress("DEPRECATION")
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            MediaRecorder()
        }
        try {
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setAudioChannels(1)
            created.setAudioSamplingRate(44_100)
            created.setAudioEncodingBitRate(128_000)
            created.setMaxDuration((MAX_RECORDING_SECONDS + 1) * 1000)
            created.setOutputFile(file.absolutePath)
            created.prepare()
            created.start()
            mediaRecorder = created
            outputFile = file
            recording = true
        } catch (error: Exception) {
            created.release()
            file.delete()
            throw error
        }
    }

    fun stop(): File? {
        val created = mediaRecorder ?: return null
        val file = outputFile
        recording = false
        return try {
            created.stop()
            file?.takeIf { it.isFile && it.length() > 0L }
        } catch (_: RuntimeException) {
            file?.delete()
            null
        } finally {
            created.release()
            mediaRecorder = null
            outputFile = null
        }
    }

    fun cancel() {
        val created = mediaRecorder
        if (created != null) {
            if (recording) {
                try {
                    created.stop()
                } catch (_: RuntimeException) {
                    // A partial recording is discarded below.
                }
            }
            created.release()
        }
        outputFile?.delete()
        mediaRecorder = null
        outputFile = null
        recording = false
    }
}
