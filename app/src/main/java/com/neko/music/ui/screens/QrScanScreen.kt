package com.neko.music.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.neko.music.R
import com.neko.music.data.api.QrLoginApi
import com.neko.music.data.manager.TokenManager
import com.neko.music.ui.theme.SakuraPink
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class QrScanStage { Scanning, Checking, Confirming, Done, Error }

/**
 * 扫码登录：扫描 PC 端二维码 → 上报已扫描 → 用户确认后在电脑端完成登录。
 */
@Composable
fun QrScanScreen(
    onBackClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val token = remember { TokenManager(context).getToken() }
    val api = remember { QrLoginApi(token) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var stage by remember { mutableStateOf(QrScanStage.Scanning) }
    var message by remember { mutableStateOf<String?>(null) }
    var sessionId by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // 扫码确认必须带上本人账号，未登录直接退回
    if (token.isNullOrBlank()) {
        LaunchedEffect(Unit) { onBackClick() }
        return
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(stage) {
        if (stage == QrScanStage.Done) {
            delay(1500)
            onBackClick()
        }
    }

    fun handleDecoded(raw: String) {
        if (stage != QrScanStage.Scanning) return
        val sid = extractSessionId(raw)
        if (sid == null) {
            message = context.getString(R.string.qr_scan_invalid)
            stage = QrScanStage.Error
            return
        }
        stage = QrScanStage.Checking
        scope.launch {
            val result = api.scan(sid)
            if (result.success) {
                sessionId = sid
                stage = QrScanStage.Confirming
            } else {
                message = result.message ?: context.getString(R.string.qr_scan_failed)
                stage = QrScanStage.Error
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasPermission) {
            CameraPreview(
                scanningEnabled = stage == QrScanStage.Scanning,
                onDecoded = { raw -> handleDecoded(raw) },
                modifier = Modifier.fillMaxSize()
            )
            ScanOverlay(hint = stringResource(R.string.qr_scan_hint))
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.qr_scan_permission_message),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.qr_scan_permission_action))
                }
            }
        }

        // 相机页不挂 TopAppBar，改用悬浮返回键，避免遮挡取景区
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = Color.White
            )
        }

        if (stage == QrScanStage.Checking) {
            StageCard(text = stringResource(R.string.qr_scan_checking), showSpinner = true)
        }

        if (stage == QrScanStage.Done) {
            StageCard(text = stringResource(R.string.qr_scan_success), showSpinner = false)
        }
    }

    if (stage == QrScanStage.Confirming) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.qr_scan_confirm_title)) },
            text = { Text(stringResource(R.string.qr_scan_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val sid = sessionId ?: return@TextButton
                    stage = QrScanStage.Checking
                    scope.launch {
                        val result = api.confirm(sid, approve = true)
                        if (result.success) {
                            stage = QrScanStage.Done
                        } else {
                            message = result.message ?: context.getString(R.string.qr_scan_failed)
                            stage = QrScanStage.Error
                        }
                    }
                }) {
                    Text(stringResource(R.string.qr_scan_confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val sid = sessionId ?: return@TextButton
                    scope.launch {
                        api.confirm(sid, approve = false)
                        onBackClick()
                    }
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (stage == QrScanStage.Error) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.qr_scan_failed)) },
            text = { Text(message.orEmpty()) },
            confirmButton = {
                TextButton(onClick = {
                    message = null
                    sessionId = null
                    stage = QrScanStage.Scanning
                }) {
                    Text(stringResource(R.string.qr_scan_retry))
                }
            },
            dismissButton = {
                TextButton(onClick = onBackClick) {
                    Text(stringResource(R.string.back))
                }
            }
        )
    }
}

/** 从二维码文本里取会话 ID：兼容 nekomusic://qrlogin?sid=xxx 与纯 ID */
private fun extractSessionId(raw: String): String? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    val marker = text.indexOf("sid=")
    val candidate = if (marker >= 0) text.substring(marker + 4) else text
    val sid = candidate.takeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }
    return sid.takeIf { it.length >= 16 }
}

private val ScanFrameSize = 260.dp

/** 取景遮罩：框外压暗 + 四角高亮 + 上下往返的扫描线，底部给出提示文案。 */
@Composable
private fun ScanOverlay(hint: String) {
    val transition = rememberInfiniteTransition(label = "qrScanLine")
    val lineProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "qrScanLineProgress"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frame = ScanFrameSize.toPx()
            val left = (size.width - frame) / 2f
            val top = (size.height - frame) / 2f
            val right = left + frame
            val bottom = top + frame
            val scrim = Color.Black.copy(alpha = 0.62f)

            drawRect(scrim, size = GeometrySize(size.width, top))
            drawRect(scrim, topLeft = Offset(0f, bottom), size = GeometrySize(size.width, size.height - bottom))
            drawRect(scrim, topLeft = Offset(0f, top), size = GeometrySize(left, frame))
            drawRect(scrim, topLeft = Offset(right, top), size = GeometrySize(size.width - right, frame))

            val corner = 30.dp.toPx()
            val stroke = 3.dp.toPx()
            val accent = SakuraPink

            fun bracket(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(
                    color = accent,
                    start = Offset(x, y),
                    end = Offset(x + dx * corner, y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = accent,
                    start = Offset(x, y),
                    end = Offset(x, y + dy * corner),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
            bracket(left, top, 1f, 1f)
            bracket(right, top, -1f, 1f)
            bracket(left, bottom, 1f, -1f)
            bracket(right, bottom, -1f, -1f)

            val inset = 12.dp.toPx()
            val lineY = top + inset + lineProgress * (frame - inset * 2)
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, accent.copy(alpha = 0.9f), Color.Transparent),
                    startX = left,
                    endX = right
                ),
                topLeft = Offset(left + inset, lineY),
                size = GeometrySize(frame - inset * 2, 2.dp.toPx())
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.size(ScanFrameSize))
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = hint,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
        }
    }
}

/** 居中悬浮的深色提示卡（校验中 / 已完成）。 */
@Composable
private fun StageCard(text: String, showSpinner: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xCC000000))
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showSpinner) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(12.dp))
            }
            Text(
                text = text,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CameraPreview(
    scanningEnabled: Boolean,
    onDecoded: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentEnabled by rememberUpdatedState(scanningEnabled)
    val currentOnDecoded by rememberUpdatedState(onDecoded)

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    DisposableEffect(Unit) {
        val executor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, QrCodeAnalyzer({ currentEnabled }, { currentOnDecoded(it) })) }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                Log.e("QrScanScreen", "相机启动失败", e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/** 每帧取 YUV 转位图交给 zxing 解码，扫到一次即交给上层处理 */
private class QrCodeAnalyzer(
    private val isEnabled: () -> Boolean,
    private val onDecoded: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            )
        )
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun analyze(image: ImageProxy) {
        if (!isEnabled()) {
            image.close()
            return
        }
        try {
            val bitmap = image.toBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            val result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            val text = result.text
            if (!text.isNullOrBlank()) {
                mainHandler.post { onDecoded(text) }
            }
        } catch (_: NotFoundException) {
            // 当前帧没有二维码，等下一帧
        } catch (e: Exception) {
            Log.w("QrScanScreen", "二维码解码异常", e)
        } finally {
            reader.reset()
            image.close()
        }
    }
}
