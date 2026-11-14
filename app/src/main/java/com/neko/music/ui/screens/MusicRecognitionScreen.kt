package com.neko.music.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.neko.music.service.MusicRecognitionPlaybackService
import com.neko.music.service.RecognitionAudioProcessor
import com.neko.music.ui.components.AppPageBackgroundImage
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.PlaylistPageDarkTintOverlay
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.theme.LightRose
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.isAppDarkTheme
import com.neko.music.util.UrlConfig
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val MAX_RECORDING_SECONDS = 20
private const val MIN_RECOGNITION_SECONDS = 3

private enum class RecognitionStage {
    Ready,
    Recording,
    Matched,
    NoMatch,
    Error,
}

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
    val recorder = remember { RecognitionAudioRecorder() }
    val scheme = MaterialTheme.colorScheme
    val isDark = isAppDarkTheme()
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)

    var stage by remember { mutableStateOf(RecognitionStage.Ready) }
    var match by remember { mutableStateOf<MusicApi.RecognitionMatch?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var recordingStartedAt by remember { mutableLongStateOf(0L) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    var recognitionJob by remember { mutableStateOf<Job?>(null) }
    var overlayPermissionGranted by remember {
        mutableStateOf(android.provider.Settings.canDrawOverlays(context))
    }
    var microphonePermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val latestStage by rememberUpdatedState(stage)
    val latestRecordingStartedAt by rememberUpdatedState(recordingStartedAt)
    val latestRecognitionJob by rememberUpdatedState(recognitionJob)

    val permissionDeniedText = stringResource(R.string.recognition_permission_denied)
    val recordingFailedText = stringResource(R.string.recognition_recording_failed)
    val requestFailedText = stringResource(R.string.recognition_request_failed)

    fun stopBackgroundRecognition() {
        runCatching {
            context.startService(
                Intent(context, MusicRecognitionPlaybackService::class.java)
                    .setAction(MusicRecognitionPlaybackService.ACTION_STOP_RECOGNITION),
            )
        }
    }

    fun returnServiceToIdle() {
        runCatching {
            context.startService(
                Intent(context, MusicRecognitionPlaybackService::class.java)
                    .setAction(MusicRecognitionPlaybackService.ACTION_RETURN_IDLE),
            )
        }
    }

    fun startIdleFloatingWindow() {
        if (!overlayPermissionGranted) return
        runCatching {
            val intent = Intent(context, MusicRecognitionPlaybackService::class.java)
                .setAction(MusicRecognitionPlaybackService.ACTION_SHOW_IDLE)
            if (microphonePermissionGranted) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun beginRecording() {
        recognitionJob?.cancel()
        match = null
        errorMessage = null
        playerManager.pause()
        try {
            val pcmFrames = recorder.start(scope)
            recordingStartedAt = SystemClock.elapsedRealtime()
            elapsedSeconds = 0
            stage = RecognitionStage.Recording
            // Establish the microphone foreground-service state while the
            // activity is visible. Actual background capture is requested later
            // from ON_PAUSE, avoiding Android 14/Oplus background mic denial.
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, MusicRecognitionPlaybackService::class.java)
                        .setAction(MusicRecognitionPlaybackService.ACTION_PREPARE)
                        .putExtra(
                            MusicRecognitionPlaybackService.EXTRA_MAX_SECONDS,
                            MAX_RECORDING_SECONDS,
                        ),
                )
            }
            recognitionJob = scope.launch {
                try {
                    val result = api.recognizeMusicStream(pcmFrames)
                    result.fold(
                        onSuccess = { response ->
                            val matched = response.match
                            if (matched != null) {
                                match = matched
                                errorMessage = null
                                stage = RecognitionStage.Matched
                            } else {
                                match = null
                                errorMessage = null
                                stage = RecognitionStage.NoMatch
                            }
                            recorder.cancel()
                            returnServiceToIdle()
                        },
                        onFailure = { error ->
                            errorMessage = error.message?.takeIf { it.isNotBlank() } ?: requestFailedText
                            stage = RecognitionStage.Error
                            recorder.cancel()
                            returnServiceToIdle()
                        },
                    )
                } catch (_: CancellationException) {
                    // User stopped listening or left the page.
                } catch (_: Exception) {
                    recorder.cancel()
                    returnServiceToIdle()
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
            microphonePermissionGranted = true
            beginRecording()
        } else {
            microphonePermissionGranted = false
            errorMessage = permissionDeniedText
            stage = RecognitionStage.Error
        }
    }

    fun requestRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        beginRecording()
    }

    fun stopListening() {
        recognitionJob?.cancel()
        recognitionJob = null
        recorder.cancel()
        stopBackgroundRecognition()
        elapsedSeconds = 0
        stage = RecognitionStage.Ready
    }

    LaunchedEffect(overlayPermissionGranted) {
        startIdleFloatingWindow()
    }

    LaunchedEffect(stage, recordingStartedAt) {
        if (stage == RecognitionStage.Recording) {
            while (stage == RecognitionStage.Recording) {
                elapsedSeconds = ((SystemClock.elapsedRealtime() - recordingStartedAt) / 1000L)
                    .toInt()
                    .coerceAtMost(MAX_RECORDING_SECONDS)
                if (elapsedSeconds >= MAX_RECORDING_SECONDS) {
                    // Close the microphone at the hard limit, but let the WebSocket
                    // finish sending the remaining PCM frames.
                    recorder.stopAtLimit()
                    break
                }
                delay(250)
            }
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != MusicRecognitionPlaybackService.ACTION_RESULT) return
                recognitionJob?.cancel()
                recognitionJob = null
                recorder.cancel()
                when (intent.getStringExtra(MusicRecognitionPlaybackService.EXTRA_STATUS)) {
                    MusicRecognitionPlaybackService.STATUS_STARTED -> {
                        match = null
                        errorMessage = null
                        recordingStartedAt = SystemClock.elapsedRealtime()
                        elapsedSeconds = 0
                        stage = RecognitionStage.Recording
                    }
                    MusicRecognitionPlaybackService.STATUS_MATCHED -> {
                        val music = Music(
                            id = intent.getIntExtra(MusicRecognitionPlaybackService.EXTRA_MUSIC_ID, -1),
                            title = intent.getStringExtra(MusicRecognitionPlaybackService.EXTRA_MUSIC_TITLE).orEmpty(),
                            artist = intent.getStringExtra(MusicRecognitionPlaybackService.EXTRA_MUSIC_ARTIST).orEmpty(),
                            album = intent.getStringExtra(MusicRecognitionPlaybackService.EXTRA_MUSIC_ALBUM).orEmpty(),
                            duration = intent.getIntExtra(MusicRecognitionPlaybackService.EXTRA_MUSIC_DURATION, 0),
                            coverFilePath = intent.getStringExtra(MusicRecognitionPlaybackService.EXTRA_MUSIC_COVER),
                        )
                        match = MusicApi.RecognitionMatch(
                            music = music,
                            confidence = intent.getDoubleExtra(MusicRecognitionPlaybackService.EXTRA_CONFIDENCE, 0.0),
                            offsetSeconds = intent.getDoubleExtra(MusicRecognitionPlaybackService.EXTRA_OFFSET_SECONDS, 0.0),
                            sampleDurationSeconds = intent.getDoubleExtra(MusicRecognitionPlaybackService.EXTRA_SAMPLE_DURATION_SECONDS, 0.0),
                        )
                        errorMessage = null
                        elapsedSeconds = 0
                        stage = RecognitionStage.Matched
                    }
                    MusicRecognitionPlaybackService.STATUS_NO_MATCH -> {
                        match = null
                        errorMessage = null
                        elapsedSeconds = 0
                        stage = RecognitionStage.NoMatch
                    }
                    MusicRecognitionPlaybackService.STATUS_STOPPED -> {
                        match = null
                        errorMessage = null
                        elapsedSeconds = 0
                        stage = RecognitionStage.Ready
                    }
                    MusicRecognitionPlaybackService.STATUS_ERROR -> {
                        match = null
                        errorMessage = intent.getStringExtra(MusicRecognitionPlaybackService.EXTRA_MESSAGE)
                        elapsedSeconds = 0
                        stage = RecognitionStage.Error
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(MusicRecognitionPlaybackService.ACTION_RESULT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    DisposableEffect(recorder, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // Start the microphone foreground service while the activity is still
            // considered visible. Waiting until ON_STOP is too late on some
            // Android versions and the system rejects the foreground-service start.
            if (event == Lifecycle.Event.ON_PAUSE && latestStage == RecognitionStage.Recording) {
                latestRecognitionJob?.cancel()
                recognitionJob = null
                recorder.cancel()
                val elapsed = ((SystemClock.elapsedRealtime() - latestRecordingStartedAt) / 1000L).toInt()
                val remaining = (MAX_RECORDING_SECONDS - elapsed).coerceAtLeast(0)
                if (remaining >= MIN_RECOGNITION_SECONDS) {
                    val serviceIntent = Intent(context, MusicRecognitionPlaybackService::class.java).apply {
                        action = MusicRecognitionPlaybackService.ACTION_START
                        putExtra(MusicRecognitionPlaybackService.EXTRA_MAX_SECONDS, remaining)
                    }
                    try {
                        // The service was prepared while the activity was
                        // visible; only send it the capture command now.
                        context.startService(serviceIntent)
                    } catch (_: Exception) {
                        elapsedSeconds = 0
                        stage = RecognitionStage.Error
                        errorMessage = recordingFailedText
                    }
                } else {
                    elapsedSeconds = 0
                    stage = RecognitionStage.Ready
                }
            } else if (
                event == Lifecycle.Event.ON_START
            ) {
                overlayPermissionGranted = android.provider.Settings.canDrawOverlays(context)
                microphonePermissionGranted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                if (overlayPermissionGranted) {
                    startIdleFloatingWindow()
                    runCatching {
                        context.startService(
                            Intent(context, MusicRecognitionPlaybackService::class.java)
                                .setAction(MusicRecognitionPlaybackService.ACTION_HIDE_FLOATING),
                        )
                    }
                }
            } else if (event == Lifecycle.Event.ON_STOP) {
                if (overlayPermissionGranted) {
                    runCatching {
                        context.startService(
                            Intent(context, MusicRecognitionPlaybackService::class.java)
                                .setAction(MusicRecognitionPlaybackService.ACTION_SHOW_FLOATING),
                        )
                    }
                }
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
    val accent by animateColorAsState(
        targetValue = when (stage) {
            RecognitionStage.Matched -> Color(0xFF4CC38A)
            else -> if (isDark) LightRose else RoseRed
        },
        animationSpec = tween(320),
        label = "recognitionAccent",
    )
    val secondaryAccent = if (isDark) Color(0xFF8B7CFF) else Color(0xFFFF9AB4)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop),
        ) {
            AppPageBackgroundImage(modifier = Modifier.fillMaxSize())
            PlaylistPageDarkTintOverlay(enabled = isDark)
            RecognitionAtmosphere(
                accent = accent,
                secondaryAccent = secondaryAccent,
                isDark = isDark,
            )
        }

        CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                RecognitionTopBar(
                    titleColor = titleColor,
                    mutedColor = mutedColor,
                    isDark = isDark,
                    onBackClick = onBackClick,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    RecognitionHero(
                        titleColor = titleColor,
                        mutedColor = mutedColor,
                        accent = accent,
                    )
                    Spacer(modifier = Modifier.height(15.dp))
                    if (overlayPermissionGranted) {
                        RecognitionBackgroundHint(
                            isDark = isDark,
                            accent = accent,
                            mutedColor = mutedColor,
                        )
                    } else {
                        RecognitionOverlayPermissionCard(
                            isDark = isDark,
                            accent = accent,
                            mutedColor = mutedColor,
                            onAuthorize = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            android.net.Uri.parse("package:${context.packageName}"),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(13.dp))

                    RecognitionStageCard(
                        stage = stage,
                        elapsedSeconds = elapsedSeconds,
                        isDark = isDark,
                        accent = accent,
                        secondaryAccent = secondaryAccent,
                        onClick = {
                            when (stage) {
                                RecognitionStage.Recording -> stopListening()
                                else -> requestRecording()
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    RecognitionStatusCopy(
                        stage = stage,
                        elapsedSeconds = elapsedSeconds,
                        errorMessage = errorMessage,
                        requestFailedText = requestFailedText,
                        titleColor = titleColor,
                        mutedColor = mutedColor,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    RecognitionActionButton(
                        stage = stage,
                        isDark = isDark,
                        accent = accent,
                        secondaryAccent = secondaryAccent,
                        onClick = {
                            when (stage) {
                                RecognitionStage.Recording -> stopListening()
                                else -> requestRecording()
                            }
                        },
                    )

                    AnimatedVisibility(
                        visible = stage == RecognitionStage.Matched && match != null,
                        enter = fadeIn(tween(220)) + slideInVertically(tween(320)) { it / 3 },
                        exit = fadeOut(tween(160)) + slideOutVertically(tween(220)) { it / 4 },
                    ) {
                        match?.let { value ->
                            Column {
                                Spacer(modifier = Modifier.height(22.dp))
                                RecognitionResultHeader(
                                    mutedColor = mutedColor,
                                    accent = accent,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                RecognitionMatchCard(
                                    match = value,
                                    isDark = isDark,
                                    accent = accent,
                                    onClick = { onMusicClick(value.music) },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    RecognitionPrivacyHint(mutedColor = mutedColor)
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun RecognitionTopBar(
    titleColor: Color,
    mutedColor: Color,
    isDark: Boolean,
    onBackClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LiquidGlassDefaults.authIconButton
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 20.dp),
    ) {
        GlassSurface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(42.dp),
            shape = CircleShape,
            backgroundAlpha = glass.tint.background(isDark),
            borderAlpha = glass.tint.border(isDark),
            highlightAlpha = glass.tint.highlight(isDark),
            borderColor = if (isDark) Color.White.copy(alpha = 0.16f) else scheme.outline,
            liquidBlur = glass.liquid.blur,
            liquidLensHeight = glass.liquid.lensHeight,
            liquidLensAmount = glass.liquid.lensAmount,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = mutedColor,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.music_recognition),
            color = titleColor,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
        GlassSurface(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .height(30.dp)
                .width(54.dp),
            shape = RoundedCornerShape(15.dp),
            backgroundAlpha = glass.tint.background(isDark),
            borderAlpha = glass.tint.border(isDark),
            highlightAlpha = glass.tint.highlight(isDark),
            borderColor = if (isDark) Color.White.copy(alpha = 0.12f) else scheme.outline.copy(alpha = 0.6f),
            liquidBlur = 6.dp,
            liquidLensHeight = 12.dp,
            liquidLensAmount = 22.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.recognition_duration_badge),
                    color = mutedColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun RecognitionAtmosphere(
    accent: Color,
    secondaryAccent: Color,
    isDark: Boolean,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val glowAlpha = if (isDark) 0.30f else 0.20f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accent.copy(alpha = glowAlpha),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.86f, size.height * 0.20f),
                radius = size.width * 0.72f,
            ),
            radius = size.width * 0.72f,
            center = Offset(size.width * 0.86f, size.height * 0.20f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondaryAccent.copy(alpha = glowAlpha * 0.68f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.08f, size.height * 0.54f),
                radius = size.width * 0.82f,
            ),
            radius = size.width * 0.82f,
            center = Offset(size.width * 0.08f, size.height * 0.54f),
        )
    }
}

@Composable
private fun RecognitionHero(
    titleColor: Color,
    mutedColor: Color,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(accent, CircleShape),
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = stringResource(R.string.recognition_page_eyebrow),
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.35.sp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = 0.30f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
    Spacer(modifier = Modifier.height(13.dp))
    Text(
        text = stringResource(R.string.recognition_hero_title),
        color = titleColor,
        fontSize = 36.sp,
        lineHeight = 41.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.9).sp,
    )
    Spacer(modifier = Modifier.height(9.dp))
    Text(
        text = stringResource(R.string.recognition_hero_subtitle),
        color = mutedColor,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun RecognitionOverlayPermissionCard(
    isDark: Boolean,
    accent: Color,
    mutedColor: Color,
    onAuthorize: () -> Unit,
) {
    val background = if (isDark) {
        accent.copy(alpha = 0.12f)
    } else {
        accent.copy(alpha = 0.09f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(accent, CircleShape),
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = stringResource(R.string.recognition_overlay_permission_title),
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = stringResource(R.string.recognition_overlay_permission_message),
            color = mutedColor.copy(alpha = 0.86f),
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(accent)
                .clickable(onClick = onAuthorize)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.recognition_overlay_permission_authorize),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RecognitionBackgroundHint(
    isDark: Boolean,
    accent: Color,
    mutedColor: Color,
) {
    val containerColor = if (isDark) {
        Color.White.copy(alpha = 0.07f)
    } else {
        Color.White.copy(alpha = 0.52f)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(17.dp))
                .background(containerColor)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(accent, CircleShape),
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = stringResource(R.string.recognition_background_ready_title),
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.recognition_background_ready_badge),
                color = mutedColor.copy(alpha = 0.78f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = stringResource(R.string.recognition_background_ready_hint),
            color = mutedColor.copy(alpha = 0.80f),
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(horizontal = 3.dp),
        )
    }
}

@Composable
private fun RecognitionStageCard(
    stage: RecognitionStage,
    elapsedSeconds: Int,
    isDark: Boolean,
    accent: Color,
    secondaryAccent: Color,
    onClick: () -> Unit,
) {
    RecognitionOrb(
        stage = stage,
        elapsedSeconds = elapsedSeconds,
        isDark = isDark,
        accent = accent,
        secondaryAccent = secondaryAccent,
        onClick = onClick,
    )
}

@Composable
private fun RecognitionStatusCopy(
    stage: RecognitionStage,
    elapsedSeconds: Int,
    errorMessage: String?,
    requestFailedText: String,
    titleColor: Color,
    mutedColor: Color,
) {
    val scheme = MaterialTheme.colorScheme
    AnimatedContent(
        targetState = stage,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically(tween(280)) { it / 5 })
                .togetherWith(
                    fadeOut(tween(150)) + slideOutVertically(tween(200)) { -it / 5 },
                )
                .using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.Center,
        label = "recognitionStatus",
    ) { animatedStage ->
        val statusText = when (animatedStage) {
            RecognitionStage.Ready -> stringResource(R.string.recognition_ready)
            RecognitionStage.Recording -> stringResource(
                R.string.recognition_listening,
                elapsedSeconds,
            )
            RecognitionStage.Matched -> stringResource(R.string.recognition_success)
            RecognitionStage.NoMatch -> stringResource(R.string.recognition_no_match)
            RecognitionStage.Error -> errorMessage?.takeIf { it.isNotBlank() }
                ?: requestFailedText
        }
        val hintText = when (animatedStage) {
            RecognitionStage.Ready -> stringResource(R.string.recognition_ready_hint)
            RecognitionStage.Recording -> stringResource(R.string.recognition_recording_hint)
            RecognitionStage.Matched -> stringResource(R.string.recognition_matched_hint)
            RecognitionStage.NoMatch -> stringResource(R.string.recognition_no_match_hint)
            RecognitionStage.Error -> null
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = statusText,
                color = if (animatedStage == RecognitionStage.Error) scheme.error else titleColor,
                fontSize = 20.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (hintText != null) {
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = hintText,
                    color = mutedColor,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RecognitionOrb(
    stage: RecognitionStage,
    elapsedSeconds: Int,
    isDark: Boolean,
    accent: Color,
    secondaryAccent: Color,
    onClick: () -> Unit,
) {
    val recording = stage == RecognitionStage.Recording
    val infiniteTransition = rememberInfiniteTransition(label = "recognitionOrbit")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1050),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recognitionPulseAlpha",
    )
    val orbit by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (recording) 4600 else 9000,
                easing = LinearEasing,
            ),
        ),
        label = "recognitionOrbitRotation",
    )
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (recording) 900 else 2400,
                easing = LinearEasing,
            ),
        ),
        label = "recognitionWavePhase",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "recognitionOrbPress",
    )
    val tint = LiquidGlassDefaults.homeHeroShortcutCard

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val stageSize = minOf(maxWidth, 300.dp)
        Box(
            modifier = Modifier
                .size(stageSize)
                .scale(scale)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.minDimension * 0.455f
                val progressRadius = size.minDimension * 0.425f
                val thinStroke = 1.dp.toPx()

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = if (recording) 0.24f * pulse else 0.16f),
                            secondaryAccent.copy(alpha = 0.07f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = outerRadius * 1.16f,
                    ),
                    radius = outerRadius * 1.16f,
                    center = center,
                )
                drawCircle(
                    color = Color.White.copy(alpha = if (isDark) 0.08f else 0.34f),
                    radius = outerRadius,
                    center = center,
                    style = Stroke(width = thinStroke),
                )
                drawCircle(
                    color = accent.copy(alpha = if (recording) 0.22f * pulse else 0.12f),
                    radius = outerRadius * 0.78f,
                    center = center,
                    style = Stroke(width = thinStroke),
                )
                drawCircle(
                    color = accent.copy(alpha = if (recording) 0.16f * pulse else 0.08f),
                    radius = outerRadius * 0.58f,
                    center = center,
                    style = Stroke(width = thinStroke),
                )

                rotate(orbit) {
                    drawArc(
                        color = accent.copy(alpha = 0.82f),
                        startAngle = 12f,
                        sweepAngle = 54f,
                        useCenter = false,
                        topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                        size = Size(outerRadius * 2f, outerRadius * 2f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = secondaryAccent.copy(alpha = 0.52f),
                        startAngle = 196f,
                        sweepAngle = 74f,
                        useCenter = false,
                        topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                        size = Size(outerRadius * 2f, outerRadius * 2f),
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                }

                repeat(3) { index ->
                    val angle = Math.toRadians((orbit + index * 120f).toDouble())
                    drawCircle(
                        color = if (index == 0) {
                            accent
                        } else {
                            secondaryAccent.copy(alpha = 0.72f)
                        },
                        radius = if (index == 0) 3.dp.toPx() else 2.dp.toPx(),
                        center = Offset(
                            center.x + cos(angle).toFloat() * outerRadius,
                            center.y + sin(angle).toFloat() * outerRadius,
                        ),
                    )
                }

                drawArc(
                    color = Color.White.copy(alpha = if (isDark) 0.10f else 0.32f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(
                        center.x - progressRadius,
                        center.y - progressRadius,
                    ),
                    size = Size(progressRadius * 2f, progressRadius * 2f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
                if (recording) {
                    val sweep = 360f * (elapsedSeconds.toFloat() / MAX_RECORDING_SECONDS)
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(
                            center.x - progressRadius,
                            center.y - progressRadius,
                        ),
                        size = Size(progressRadius * 2f, progressRadius * 2f),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                    val progressAngle = Math.toRadians((-90f + sweep).toDouble())
                    drawCircle(
                        color = Color.White,
                        radius = 3.5.dp.toPx(),
                        center = Offset(
                            center.x + cos(progressAngle).toFloat() * progressRadius,
                            center.y + sin(progressAngle).toFloat() * progressRadius,
                        ),
                    )
                }
            }

            GlassSurface(
                modifier = Modifier.size(164.dp),
                shape = CircleShape,
                backgroundAlpha = (tint.tint.background(isDark) + 0.10f).coerceAtMost(0.50f),
                borderAlpha = (tint.tint.border(isDark) + 0.18f).coerceAtMost(0.42f),
                highlightAlpha = tint.tint.highlight(isDark),
                borderColor = accent,
                surfaceTint = accent.copy(alpha = if (recording) 0.12f else 0.06f),
                liquidBlur = LiquidGlassDefaults.liquidMedium.blur,
                liquidLensHeight = LiquidGlassDefaults.liquidMedium.lensHeight,
                liquidLensAmount = LiquidGlassDefaults.liquidMedium.lensAmount,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (stage) {
                        RecognitionStage.Matched -> Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.recognition_success),
                            tint = accent,
                            modifier = Modifier.size(52.dp),
                        )
                        RecognitionStage.Error, RecognitionStage.NoMatch -> Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.retry),
                            tint = accent,
                            modifier = Modifier.size(46.dp),
                        )
                        RecognitionStage.Ready, RecognitionStage.Recording -> {
                            RecognitionWaveform(
                                phase = wavePhase,
                                active = recording,
                                color = accent,
                                modifier = Modifier
                                    .width(82.dp)
                                    .height(44.dp),
                            )
                            if (recording) {
                                Spacer(modifier = Modifier.height(7.dp))
                                Text(
                                    text = elapsedSeconds.toString().padStart(2, '0'),
                                    color = accent,
                                    fontSize = 28.sp,
                                    lineHeight = 29.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.8).sp,
                                )
                                Text(
                                    text = "SEC",
                                    color = accent.copy(alpha = 0.62f),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.1.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecognitionWaveform(
    phase: Float,
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier
        .width(76.dp)
        .height(52.dp),
) {
    Canvas(modifier = modifier) {
        val barCount = 9
        val slotWidth = size.width / barCount
        val barWidth = slotWidth * 0.36f
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
        repeat(barCount) { index ->
            val oscillated = abs(sin((phase * 2f * PI + index * 0.72f).toFloat()))
            val level = if (active) {
                0.24f + 0.76f * oscillated
            } else {
                0.18f + 0.20f * oscillated
            }
            val barHeight = size.height * (0.16f + level * 0.72f)
            val left = slotWidth * index + (slotWidth - barWidth) / 2f
            val top = (size.height - barHeight) / 2f
            drawRoundRect(
                color = color.copy(alpha = if (active) 0.96f else 0.78f),
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
private fun RecognitionActionButton(
    stage: RecognitionStage,
    isDark: Boolean,
    accent: Color,
    secondaryAccent: Color,
    onClick: () -> Unit,
) {
    val label = when (stage) {
        RecognitionStage.Ready -> stringResource(R.string.recognition_action_listen)
        RecognitionStage.Recording -> stringResource(R.string.recognition_action_stop)
        RecognitionStage.Matched -> stringResource(R.string.recognition_action_again)
        RecognitionStage.NoMatch, RecognitionStage.Error -> stringResource(R.string.recognition_action_retry)
    }
    val contentColor = if (isDark) Color(0xFF1D1020) else Color.White
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "recognitionActionPress",
    )

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(22.dp),
        backgroundAlpha = 0.42f,
        borderAlpha = 0.34f,
        highlightAlpha = 0.18f,
        borderColor = Color.White.copy(alpha = 0.34f),
        surfaceTint = accent.copy(alpha = if (isDark) 0.66f else 0.88f),
        liquidBlur = 12.dp,
        liquidLensHeight = 20.dp,
        liquidLensAmount = 34.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = if (isDark) 0.30f else 0.18f),
                            secondaryAccent.copy(alpha = if (isDark) 0.24f else 0.12f),
                        ),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                when (stage) {
                    RecognitionStage.Recording -> Icon(
                        painter = painterResource(R.drawable.ic_stop_recording),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                    RecognitionStage.Matched, RecognitionStage.NoMatch, RecognitionStage.Error -> Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(19.dp),
                    )
                    RecognitionStage.Ready -> Icon(
                        painter = painterResource(R.drawable.ic_music_recognition),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RecognitionResultHeader(
    mutedColor: Color,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(accent, CircleShape),
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = stringResource(R.string.recognition_result_title),
            color = mutedColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            accent.copy(alpha = 0.36f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun RecognitionMatchCard(
    match: MusicApi.RecognitionMatch,
    isDark: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = LiquidGlassDefaults.homeHeroShortcutCard
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "recognitionMatchPress",
    )

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(28.dp),
        backgroundAlpha = (tint.tint.background(isDark) + 0.08f).coerceAtMost(0.48f),
        borderAlpha = (tint.tint.border(isDark) + 0.08f).coerceAtMost(0.34f),
        highlightAlpha = tint.tint.highlight(isDark),
        borderColor = if (isDark) accent else scheme.outline,
        liquidBlur = tint.liquid.blur,
        liquidLensHeight = tint.liquid.lensHeight,
        liquidLensAmount = tint.liquid.lensAmount,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = UrlConfig.buildMusicCoverUrl(match.music.id, match.music.coverFilePath),
                contentDescription = stringResource(R.string.content_description_cover),
                modifier = Modifier
                    .size(86.dp)
                    .clip(RoundedCornerShape(22.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(15.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.recognition_success),
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                )
                Text(
                    text = match.music.title,
                    color = if (isDark) Color(0xFFF0F0F5) else scheme.onSurface,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = match.music.artist,
                    color = if (isDark) Color(0xFFB8B8D1).copy(alpha = 0.88f) else scheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (match.music.album.isNotBlank()) {
                    Text(
                        text = match.music.album,
                        color = if (isDark) {
                            Color(0xFFB8B8D1).copy(alpha = 0.58f)
                        } else {
                            scheme.onSurfaceVariant.copy(alpha = 0.64f)
                        },
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.recognition_open_result),
                    tint = if (isDark) Color(0xFF102018) else Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (isDark) 0.10f else 0.42f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(match.confidence.toFloat().coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    accent.copy(alpha = 0.74f),
                                    accent,
                                ),
                            ),
                        ),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(
                    R.string.recognition_confidence,
                    (match.confidence * 100).toInt().coerceIn(0, 100),
                ),
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RecognitionPrivacyHint(mutedColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = mutedColor.copy(alpha = 0.72f),
            modifier = Modifier.size(13.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.recognition_privacy_hint),
            color = mutedColor.copy(alpha = 0.78f),
            fontSize = 11.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private class RecognitionAudioRecorder {
    @Volatile private var recording = false
    private var audioRecord: AudioRecord? = null
    private var audioProcessor: RecognitionAudioProcessor? = null
    private var captureJob: Job? = null
    private var pcmChannel: Channel<ByteArray>? = null

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope): ReceiveChannel<ByteArray> {
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

        val channel = Channel<ByteArray>(capacity = 64)
        audioRecord = created
        pcmChannel = channel
        recording = true
        try {
            created.startRecording()
        } catch (error: Exception) {
            recording = false
            created.release()
            audioRecord = null
            pcmChannel = null
            channel.cancel()
            throw error
        }
        audioProcessor?.close()
        audioProcessor = RecognitionAudioProcessor()

        captureJob = scope.launch(Dispatchers.IO) {
            val maximumBytes = STREAM_SAMPLE_RATE * PCM_BYTES_PER_SAMPLE * MAX_RECORDING_SECONDS
            val readBuffer = ByteArray(bufferSize)
            var capturedBytes = 0
            try {
                while (isActive && recording && capturedBytes < maximumBytes) {
                    val requested = minOf(readBuffer.size, maximumBytes - capturedBytes)
                    val read = created.read(readBuffer, 0, requested, AudioRecord.READ_BLOCKING)
                    if (read <= 0) {
                        if (!recording || !isActive) break
                        throw IOException("Audio capture failed: $read")
                    }
                    capturedBytes += read
                    audioProcessor?.process(readBuffer, read)
                    channel.send(readBuffer.copyOf(read))
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
                    pcmChannel = null
                }
                try {
                    if (created.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        created.stop()
                    }
                } catch (_: IllegalStateException) {
                    // Recorder is already stopped.
                }
                created.release()
                audioProcessor?.close()
                audioProcessor = null
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
        pcmChannel?.cancel()
        audioProcessor?.close()
        audioProcessor = null
        captureJob = null
        pcmChannel = null
    }

    /** Stops microphone capture at the duration limit while leaving queued PCM frames deliverable. */
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

    private companion object {
        const val STREAM_SAMPLE_RATE = 16_000
        const val PCM_BYTES_PER_SAMPLE = 2
    }
}
