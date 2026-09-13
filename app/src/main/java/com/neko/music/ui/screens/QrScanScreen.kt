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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class QrScanStage { Scanning, Checking, Confirming, Done, Error }

/**
 * 扫码登录：扫描 PC 端二维码 → 上报已扫描 → 用户确认后在电脑端完成登录。
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.qr_scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            if (!hasPermission) {
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
            } else {
                CameraPreview(
                    scanningEnabled = stage == QrScanStage.Scanning,
                    onDecoded = { raw -> handleDecoded(raw) },
                    modifier = Modifier.fillMaxSize()
                )
                ScanFrameHint(hint = stringResource(R.string.qr_scan_hint))
            }

            if (stage == QrScanStage.Checking) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.qr_scan_checking),
                            color = Color.White
                        )
                    }
                }
            }

            if (stage == QrScanStage.Done) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.qr_scan_success),
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
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

@Composable
private fun ScanFrameHint(hint: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = hint,
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
