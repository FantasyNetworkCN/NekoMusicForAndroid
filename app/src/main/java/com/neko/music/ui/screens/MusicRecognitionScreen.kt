package com.neko.music.ui.screens

import com.neko.music.ui.icons.NekoIcon

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
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.neko.music.ui.icons.NekoSymbol
import com.neko.music.ui.icons.NekoSymbolIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.neko.music.data.manager.RecognitionHistoryManager
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.PlaylistPageDarkTintOverlay
import com.neko.music.service.MusicPlayerManager
import com.neko.music.service.MusicRecognitionPlaybackService
import com.neko.music.service.RecognitionAudioProcessor
import com.neko.music.ui.components.AppPageBackgroundImage
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.theme.isAppDarkTheme
import com.neko.music.util.recognitionCoverUrl
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
    val history = remember(context) { RecognitionHistoryManager.getInstance(context) }
    val historyEntries by history.entries.collectAsState()
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val isDark = isAppDarkTheme()
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)

    var match by remember { mutableStateOf(history.entries.value.firstOrNull()?.toMatch()) }
    var stage by remember { mutableStateOf(if (match == null) RecognitionStage.Ready else RecognitionStage.Matched) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var recordingStartedAt by remember { mutableLongStateOf(0L) }
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
                                history.record(matched)
                                match = matched
                                runCatching {
                                    context.startService(Intent(context, MusicRecognitionPlaybackService::class.java)
                                        .setAction(MusicRecognitionPlaybackService.ACTION_SET_MATCH))
                                }
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
        match = history.entries.value.firstOrNull()?.toMatch()
        stage = if (match == null) RecognitionStage.Ready else RecognitionStage.Matched
    }

    LaunchedEffect(overlayPermissionGranted) {
        startIdleFloatingWindow()
        if (overlayPermissionGranted) {
            runCatching {
                context.startService(Intent(context, MusicRecognitionPlaybackService::class.java)
                    .setAction(MusicRecognitionPlaybackService.ACTION_HIDE_FLOATING))
            }
        }
    }

    LaunchedEffect(stage, recordingStartedAt) {
        if (stage == RecognitionStage.Recording) {
            while (stage == RecognitionStage.Recording) {
                val elapsedSeconds = ((SystemClock.elapsedRealtime() - recordingStartedAt) / 1000L)
                    .toInt()
                    .coerceAtMost(MAX_RECORDING_SECONDS)
                if (elapsedSeconds >= MAX_RECORDING_SECONDS) {
                    // 达到录音上限后关闭麦克风，让连接继续发送剩余音频。
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
                        stage = RecognitionStage.Matched
                    }
                    MusicRecognitionPlaybackService.STATUS_NO_MATCH -> {
                        match = null
                        errorMessage = null
                        stage = RecognitionStage.NoMatch
                    }
                    MusicRecognitionPlaybackService.STATUS_STOPPED -> {
                        match = history.entries.value.firstOrNull()?.toMatch()
                        errorMessage = null
                        stage = if (match == null) RecognitionStage.Ready else RecognitionStage.Matched
                    }
                    MusicRecognitionPlaybackService.STATUS_ERROR -> {
                        match = null
                        errorMessage = intent.getStringExtra(MusicRecognitionPlaybackService.EXTRA_MESSAGE)
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
                        stage = RecognitionStage.Error
                        errorMessage = recordingFailedText
                    }
                } else {
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

    val titleColor = scheme.onSurface
    val mutedColor = scheme.onSurfaceVariant
    val accent = scheme.primary
    val toggleRecognition = {
        if (stage == RecognitionStage.Recording) stopListening() else requestRecording()
    }
    val authorizeOverlay = {
        runCatching {
            context.startActivity(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}"),
                ),
            )
        }.onFailure {
            Toast.makeText(context, R.string.recognition_overlay_open_failed, Toast.LENGTH_SHORT).show()
        }
        Unit
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
            AppPageBackgroundImage(modifier = Modifier.fillMaxSize())
            PlaylistPageDarkTintOverlay(enabled = isDark)
        }
        CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBackClick) {
                        NekoIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = titleColor)
                    }
                    Text(stringResource(R.string.music_recognition), Modifier.weight(1f),
                        fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
                    TextButton(onClick = { showHistory = true }) {
                        NekoSymbolIcon(NekoSymbol.Clock, null, Modifier.size(18.dp), accent)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.recognition_history_title))
                    }
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth >= 680.dp
                    val compact = maxHeight < 600.dp
                    val ready = stage == RecognitionStage.Ready
                    val showResult = stage == RecognitionStage.Matched && match != null
                    if (wide) {
                        Row(
                            Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 32.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                        ) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                RecognitionDisc(stage, accent, compact, toggleRecognition)
                            }
                            Column(
                                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                RecognitionStatusCopy(stage, errorMessage, requestFailedText, titleColor, mutedColor)
                                if (showResult) RecognitionMatchCard(requireNotNull(match), accent) { onMusicClick(requireNotNull(match).music) }
                                RecognitionSecondaryAction(stage, accent, toggleRecognition)
                                RecognitionOverlayEntry(overlayPermissionGranted, accent, authorizeOverlay)
                                RecognitionPrivacyHint(mutedColor)
                            }
                        }
                    } else {
                        Column(
                            Modifier.align(Alignment.TopCenter).widthIn(max = 560.dp).fillMaxSize()
                                .verticalScroll(rememberScrollState()).navigationBarsPadding()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (ready) {
                                Text(stringResource(R.string.recognition_listen_title), color = titleColor,
                                    fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center)
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.recognition_listen_subtitle), color = mutedColor,
                                    fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
                            }
                            Spacer(Modifier.height(if (compact || showResult) 12.dp else 24.dp))
                            RecognitionDisc(stage, accent, compact || showResult, toggleRecognition)
                            Spacer(Modifier.height(16.dp))
                            RecognitionStatusCopy(stage, errorMessage, requestFailedText, titleColor, mutedColor)
                            if (showResult) {
                                Spacer(Modifier.height(20.dp))
                                RecognitionMatchCard(requireNotNull(match), accent) { onMusicClick(requireNotNull(match).music) }
                            }
                            Spacer(Modifier.height(12.dp))
                            RecognitionSecondaryAction(stage, accent, toggleRecognition)
                            Spacer(Modifier.height(24.dp))
                            RecognitionOverlayEntry(overlayPermissionGranted, accent, authorizeOverlay)
                            Spacer(Modifier.height(20.dp))
                            RecognitionPrivacyHint(mutedColor)
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
        if (showHistory) {
            CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
                RecognitionHistoryPanel(
                    entries = historyEntries,
                    onBack = { showHistory = false },
                    onMusicClick = {
                        if (stage == RecognitionStage.Recording) stopListening()
                        showHistory = false
                        onMusicClick(it)
                    },
                )
            }
        }
    }
}

@Composable
private fun RecognitionDisc(
    stage: RecognitionStage,
    accent: Color,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = isAppDarkTheme()
    val tint = LiquidGlassDefaults.homeHeroShortcutCard
    val active = stage == RecognitionStage.Recording
    val phase = if (active) {
        val transition = rememberInfiniteTransition(label = "recognition_ripple")
        transition.animateFloat(
            0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "recognition_ripple_phase",
        )
    } else remember { mutableStateOf(0f) }
    val label = when (stage) {
        RecognitionStage.Recording -> stringResource(R.string.recognition_action_stop)
        RecognitionStage.Ready -> stringResource(R.string.recognition_action_listen)
        else -> stringResource(R.string.recognition_action_again)
    }
    val pressed = remember { MutableInteractionSource() }
    val isPressed by pressed.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) .96f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "disc_press")
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val diameter = minOf(maxWidth, if (compact) 240.dp else 300.dp)
        Box(Modifier.size(diameter).scale(scale), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension * .30f
                if (active) {
                    repeat(3) { i ->
                        val progress = (phase.value + i / 3f) % 1f
                        drawCircle(accent.copy(alpha = (1f - progress) * .18f),
                            radius = radius * (1.18f + .65f * progress), style = Stroke(1.5.dp.toPx()))
                    }
                } else {
                    drawCircle(accent.copy(alpha = .07f), radius = radius * 1.30f)
                    drawCircle(accent.copy(alpha = .08f), radius = radius * 1.55f, style = Stroke(1.dp.toPx()))
                }
            }
            GlassSurface(
                modifier = Modifier.size(diameter * .61f)
                    .clickable(interactionSource = pressed, indication = null, role = Role.Button, onClick = onClick)
                    .semantics { contentDescription = label },
                shape = CircleShape,
                backgroundAlpha = tint.tint.background(dark),
                borderAlpha = tint.tint.border(dark),
                highlightAlpha = tint.tint.highlight(dark),
                borderColor = accent.copy(alpha = .35f),
                surfaceTint = accent.copy(alpha = .10f),
                material3Container = scheme.primaryContainer,
                liquidBlur = tint.liquid.blur,
                liquidLensHeight = tint.liquid.lensHeight,
                liquidLensAmount = tint.liquid.lensAmount,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        repeat(4) { i ->
                            drawCircle(Color.White.copy(alpha = .07f), radius = size.minDimension * (.33f + i * .035f),
                                style = Stroke(1.dp.toPx()))
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (active) {
                            RecognitionWaveform({ phase.value }, accent, Modifier.size(64.dp, 36.dp))
                        } else {
                            NekoSymbolIcon(
                                if (stage == RecognitionStage.Matched) NekoSymbol.Check else NekoSymbol.MusicNote,
                                null, Modifier.size(52.dp), accent,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecognitionStatusCopy(
    stage: RecognitionStage,
    errorMessage: String?,
    requestFailedText: String,
    titleColor: Color,
    mutedColor: Color,
) {
    val text = when (stage) {
        RecognitionStage.Ready -> stringResource(R.string.recognition_tap_disc)
        RecognitionStage.Recording -> stringResource(R.string.recognition_listening)
        RecognitionStage.Matched -> stringResource(R.string.recognition_success)
        RecognitionStage.NoMatch -> stringResource(R.string.recognition_no_match)
        RecognitionStage.Error -> errorMessage?.takeIf(String::isNotBlank) ?: requestFailedText
    }
    val hint = when (stage) {
        RecognitionStage.Ready -> stringResource(R.string.recognition_ready_hint)
        RecognitionStage.Recording -> stringResource(R.string.recognition_recording_hint)
        RecognitionStage.Matched -> stringResource(R.string.recognition_matched_hint)
        RecognitionStage.NoMatch -> stringResource(R.string.recognition_no_match_hint)
        RecognitionStage.Error -> stringResource(R.string.recognition_retry_hint)
    }
    Column(Modifier.fillMaxWidth().animateContentSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = if (stage == RecognitionStage.Error) MaterialTheme.colorScheme.error else titleColor,
            fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(hint, color = mutedColor, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RecognitionSecondaryAction(stage: RecognitionStage, accent: Color, onClick: () -> Unit) {
    TextButton(onClick, colors = ButtonDefaults.textButtonColors(contentColor = accent), modifier = Modifier.heightIn(min = 48.dp)) {
        if (stage == RecognitionStage.Recording) {
            NekoIcon(R.drawable.ic_stop_recording, null, Modifier.size(16.dp), accent)
        } else {
            NekoSymbolIcon(if (stage == RecognitionStage.Ready) NekoSymbol.Microphone else NekoSymbol.Refresh,
                null, Modifier.size(18.dp), accent)
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(when (stage) {
            RecognitionStage.Ready -> R.string.recognition_action_listen
            RecognitionStage.Recording -> R.string.recognition_action_stop
            else -> R.string.recognition_action_again
        }), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RecognitionOverlayEntry(granted: Boolean, accent: Color, onAuthorize: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    RecognitionGlassCard {
    Row(
        Modifier.fillMaxWidth()
            .then(if (granted) Modifier else Modifier.clickable(role = Role.Button, onClick = onAuthorize))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
            NekoSymbolIcon(NekoSymbol.PictureInPicture, null, Modifier.size(22.dp), accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(if (granted) R.string.recognition_float_enabled else R.string.recognition_overlay_permission_title),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            Text(stringResource(if (granted) R.string.recognition_float_enabled_hint else R.string.recognition_float_enable_hint),
                fontSize = 12.sp, lineHeight = 18.sp, color = scheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        if (granted) NekoSymbolIcon(NekoSymbol.CheckCircle, null, Modifier.size(20.dp), accent)
        else NekoSymbolIcon(NekoSymbol.ChevronRight, null, Modifier.size(16.dp), scheme.onSurfaceVariant)
    }
    }
}

@Composable
private fun RecognitionMatchCard(match: MusicApi.RecognitionMatch, accent: Color, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    RecognitionGlassCard(modifier = Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = recognitionCoverUrl(match.music),
                    contentDescription = null,
                    placeholder = painterResource(R.drawable.music), error = painterResource(R.drawable.music),
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(match.music.title, color = scheme.onSurface, fontSize = 17.sp, lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(match.music.artist, color = scheme.onSurfaceVariant, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (match.music.album.isNotBlank()) Text(match.music.album, color = scheme.onSurfaceVariant,
                        fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(48.dp).clip(CircleShape).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
                    NekoSymbolIcon(NekoSymbol.Play, stringResource(R.string.recognition_open_result), Modifier.size(24.dp), scheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NekoSymbolIcon(NekoSymbol.CheckCircle, null, Modifier.size(14.dp), accent)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.recognition_confidence, (match.confidence * 100).toInt().coerceIn(0, 100)),
                    color = scheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun RecognitionWaveform(phase: () -> Float, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        repeat(7) { index ->
            val level = .22f + .78f * abs(sin(phase() * 2f * PI + index * .72f)).toFloat()
            val x = size.width * (index + .5f) / 7f
            val halfHeight = size.height * level * .45f
            drawLine(color, Offset(x, center.y - halfHeight), Offset(x, center.y + halfHeight),
                strokeWidth = size.width / 20f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun RecognitionPrivacyHint(mutedColor: Color) {
    Text(stringResource(R.string.recognition_privacy_hint), Modifier.fillMaxWidth(), color = mutedColor.copy(alpha = .75f),
        fontSize = 11.sp, lineHeight = 17.sp, textAlign = TextAlign.Center)
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
