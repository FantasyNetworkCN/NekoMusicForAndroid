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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.neko.music.service.MusicRecognitionPlaybackService
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private const val MAX_RECORDING_SECONDS = 20
private const val MIN_RECOGNITION_SECONDS = 3
private const val SNAPSHOT_INTERVAL_SECONDS = 4

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
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    val latestStage by rememberUpdatedState(stage)
    val latestRecordingStartedAt by rememberUpdatedState(recordingStartedAt)
    val latestRecognitionJob by rememberUpdatedState(recognitionJob)

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
            // Establish the microphone foreground-service state while the
            // activity is visible. Actual background capture is requested later
            // from ON_PAUSE, avoiding Android 14/Oplus background mic denial.
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, MusicRecognitionPlaybackService::class.java)
                        .setAction(MusicRecognitionPlaybackService.ACTION_PREPARE)
                        .putExtra(MusicRecognitionPlaybackService.EXTRA_MAX_SECONDS, MAX_RECORDING_SECONDS),
                )
            }
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
                                    context.stopService(Intent(context, MusicRecognitionPlaybackService::class.java))
                                }
                            },
                            onFailure = { error ->
                                recorder.cancel()
                                context.stopService(Intent(context, MusicRecognitionPlaybackService::class.java))
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
                        context.stopService(Intent(context, MusicRecognitionPlaybackService::class.java))
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
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!android.provider.Settings.canDrawOverlays(context)) {
            // Recognition can continue without this optional permission, but the
            // floating background control will only be shown after it is granted.
            showOverlayPermissionDialog = true
        }
        beginRecording()
    }

    fun stopListening() {
        recognitionJob?.cancel()
        recognitionJob = null
        recorder.cancel()
        context.stopService(Intent(context, MusicRecognitionPlaybackService::class.java))
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

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != MusicRecognitionPlaybackService.ACTION_RESULT) return
                recognitionJob?.cancel()
                recognitionJob = null
                recorder.cancel()
                when (intent.getStringExtra(MusicRecognitionPlaybackService.EXTRA_STATUS)) {
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
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            recognitionJob?.cancel()
            recorder.cancel()
            context.stopService(Intent(context, MusicRecognitionPlaybackService::class.java))
        }
    }

    if (showOverlayPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayPermissionDialog = false },
            title = { Text(stringResource(R.string.recognition_overlay_permission_title)) },
            text = { Text(stringResource(R.string.recognition_overlay_permission_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOverlayPermissionDialog = false
                        runCatching {
                            context.startActivity(
                                Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.recognition_overlay_permission_authorize))
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayPermissionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
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

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop),
        ) {
            AppPageBackgroundImage(modifier = Modifier.fillMaxSize())
            PlaylistPageDarkTintOverlay(enabled = isDark)
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
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.recognition_hero_title),
                        color = titleColor,
                        fontSize = 32.sp,
                        lineHeight = 37.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp,
                    )
                    Spacer(modifier = Modifier.height(9.dp))
                    Text(
                        text = stringResource(R.string.recognition_hero_subtitle),
                        color = mutedColor,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    RecognitionStageCard(
                        stage = stage,
                        elapsedSeconds = elapsedSeconds,
                        errorMessage = errorMessage,
                        requestFailedText = requestFailedText,
                        isDark = isDark,
                        accent = accent,
                        titleColor = titleColor,
                        mutedColor = mutedColor,
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
                                Text(
                                    text = stringResource(R.string.recognition_result_title),
                                    color = mutedColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.3.sp,
                                )
                                Spacer(modifier = Modifier.height(9.dp))
                                RecognitionMatchCard(
                                    match = value,
                                    isDark = isDark,
                                    accent = accent,
                                    onClick = { onMusicClick(value.music) },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))
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
                .size(44.dp),
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
    }
}

@Composable
private fun RecognitionStageCard(
    stage: RecognitionStage,
    elapsedSeconds: Int,
    errorMessage: String?,
    requestFailedText: String,
    isDark: Boolean,
    accent: Color,
    titleColor: Color,
    mutedColor: Color,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LiquidGlassDefaults.homeHeroShortcutCard
    val actionLabel = when (stage) {
        RecognitionStage.Ready -> stringResource(R.string.recognition_action_listen)
        RecognitionStage.Recording -> stringResource(R.string.recognition_action_stop)
        RecognitionStage.Matched -> stringResource(R.string.recognition_action_again)
        RecognitionStage.NoMatch, RecognitionStage.Error -> stringResource(R.string.recognition_action_retry)
    }

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        backgroundAlpha = (glass.tint.background(isDark) + 0.04f).coerceAtMost(0.48f),
        borderAlpha = (glass.tint.border(isDark) + 0.04f).coerceAtMost(0.32f),
        highlightAlpha = glass.tint.highlight(isDark),
        borderColor = if (isDark) accent else scheme.outline,
        liquidBlur = glass.liquid.blur,
        liquidLensHeight = glass.liquid.lensHeight,
        liquidLensAmount = glass.liquid.lensAmount,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = if (isDark) 0.14f else 0.08f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RecognitionOrb(
                    stage = stage,
                    elapsedSeconds = elapsedSeconds,
                    isDark = isDark,
                    accent = accent,
                    onClick = onClick,
                )
                Spacer(modifier = Modifier.height(8.dp))
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = statusText,
                            color = if (animatedStage == RecognitionStage.Error) scheme.error else titleColor,
                            fontSize = 19.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
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
                Spacer(modifier = Modifier.height(18.dp))
                RecognitionActionButton(
                    stage = stage,
                    label = actionLabel,
                    isDark = isDark,
                    accent = accent,
                    onClick = onClick,
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
    onClick: () -> Unit,
) {
    val recording = stage == RecognitionStage.Recording
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
    val tint = LiquidGlassDefaults.screenListCard

    Box(
        modifier = Modifier
            .size(218.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(188.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = if (recording) 0.20f * pulse else 0.11f),
                            Color.Transparent,
                        ),
                    ),
                    CircleShape,
                ),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val thinStroke = 1.dp.toPx()
            drawCircle(
                color = accent.copy(alpha = if (recording) 0.16f * pulse else 0.10f),
                radius = size.minDimension * 0.48f,
                style = Stroke(width = thinStroke),
            )
            drawCircle(
                color = accent.copy(alpha = if (recording) 0.32f * pulse else 0.18f),
                radius = size.minDimension * 0.40f,
                style = Stroke(width = thinStroke),
            )
            if (recording) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * (elapsedSeconds.toFloat() / MAX_RECORDING_SECONDS),
                    useCenter = false,
                    topLeft = Offset(size.width * 0.06f, size.height * 0.06f),
                    size = Size(size.width * 0.88f, size.height * 0.88f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        GlassSurface(
            modifier = Modifier.size(148.dp),
            shape = CircleShape,
            backgroundAlpha = (tint.background(isDark) + 0.06f).coerceAtMost(0.46f),
            borderAlpha = (tint.border(isDark) + 0.14f).coerceAtMost(0.36f),
            highlightAlpha = tint.highlight(isDark),
            borderColor = accent,
            liquidBlur = LiquidGlassDefaults.liquidMedium.blur,
            liquidLensHeight = LiquidGlassDefaults.liquidMedium.lensHeight,
            liquidLensAmount = LiquidGlassDefaults.liquidMedium.lensAmount,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                when (stage) {
                    RecognitionStage.Matched -> Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.recognition_success),
                        tint = accent,
                        modifier = Modifier.size(44.dp),
                    )
                    RecognitionStage.Error, RecognitionStage.NoMatch -> Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.retry),
                        tint = accent,
                        modifier = Modifier.size(40.dp),
                    )
                    RecognitionStage.Ready, RecognitionStage.Recording -> RecognitionWaveform(
                        phase = wavePhase,
                        active = recording,
                        color = accent,
                    )
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
) {
    Canvas(
        modifier = Modifier
            .width(76.dp)
            .height(52.dp),
    ) {
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
    label: String,
    isDark: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val tint = LiquidGlassDefaults.screenListCard
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "recognitionActionPress",
    )

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(26.dp),
        backgroundAlpha = (tint.background(isDark) + 0.10f).coerceAtMost(0.48f),
        borderAlpha = (tint.border(isDark) + 0.10f).coerceAtMost(0.34f),
        highlightAlpha = tint.highlight(isDark),
        borderColor = accent,
        surfaceTint = accent.copy(alpha = if (isDark) 0.16f else 0.10f),
        liquidBlur = 10.dp,
        liquidLensHeight = 18.dp,
        liquidLensAmount = 30.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (stage) {
                RecognitionStage.Recording -> Icon(
                    painter = painterResource(R.drawable.ic_stop_recording),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(19.dp),
                )
                RecognitionStage.Matched, RecognitionStage.NoMatch, RecognitionStage.Error -> Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(19.dp),
                )
                RecognitionStage.Ready -> Icon(
                    painter = painterResource(R.drawable.ic_music_recognition),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
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
        shape = RoundedCornerShape(24.dp),
        backgroundAlpha = tint.tint.background(isDark),
        borderAlpha = tint.tint.border(isDark),
        highlightAlpha = tint.tint.highlight(isDark),
        borderColor = if (isDark) accent else scheme.outline,
        liquidBlur = tint.liquid.blur,
        liquidLensHeight = tint.liquid.lensHeight,
        liquidLensAmount = tint.liquid.lensAmount,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = UrlConfig.buildMusicCoverUrl(match.music.id, match.music.coverFilePath),
                contentDescription = stringResource(R.string.content_description_cover),
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = match.music.title,
                    color = if (isDark) Color(0xFFF0F0F5) else scheme.onSurface,
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = match.music.artist,
                    color = if (isDark) Color(0xFFB8B8D1).copy(alpha = 0.88f) else scheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accent.copy(alpha = if (isDark) 0.16f else 0.10f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.recognition_confidence,
                            (match.confidence * 100).toInt().coerceIn(0, 100),
                        ),
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = if (isDark) 0.16f else 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.recognition_open_result),
                    tint = accent,
                    modifier = Modifier.size(24.dp),
                )
            }
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
