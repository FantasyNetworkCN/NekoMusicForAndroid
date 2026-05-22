package com.neko.music.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.neko.music.R
import com.neko.music.data.api.SliderCaptchaChallengeDto
import com.neko.music.data.api.SliderCaptchaLoadResult
import com.neko.music.data.api.SliderCaptchaVerifyResult
import com.neko.music.data.api.UserApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private const val CAPTCHA_LOG_TAG = "RegisterSliderCaptcha"

/** 与 Web `railThumbW` 一致 */
private val RailThumbW = 48.dp

private val RailH = 44.dp

private val RailThumbH = 36.dp

/**
 * Web 与后端：`sliderX = (thumbLeft / (railW - thumbW)) * (bgW - pieceW)`（再 round 提交）。
 * 返回连续浮点，避免「先取整再 .dp」与滑块 thumb 的浮点轨迹不一致导致的系统性偏左。
 */
private fun slideXFloatFromThumb(thumbLeftPx: Float, railWpx: Float, thumbWpx: Float, mx: Int): Float {
    if (mx <= 0) return 0f
    val tm = (railWpx - thumbWpx).coerceAtLeast(1f)
    val clamped = thumbLeftPx.coerceIn(0f, tm)
    return (clamped / tm) * mx.toFloat()
}

private fun slideOffsetXIntFromThumb(thumbLeftPx: Float, railWpx: Float, thumbWpx: Float, mx: Int): Int =
    slideXFloatFromThumb(thumbLeftPx, railWpx, thumbWpx, mx).roundToInt().coerceIn(0, mx)

private fun decodePngDataUrlToImageBitmap(dataUrl: String): ImageBitmap? {
    return try {
        val comma = dataUrl.indexOf(',')
        if (comma < 0) return null
        val b64 = dataUrl.substring(comma + 1).trim()
        val raw = Base64.decode(b64, Base64.DEFAULT)
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
        bmp.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

@Composable
fun RegisterSliderCaptchaDialog(
    visible: Boolean,
    userApi: UserApi,
    email: String,
    username: String,
    onDismiss: () -> Unit,
    onCodeSent: () -> Unit,
) {
    if (!visible) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val verifyingText = stringResource(R.string.captcha_verifying)
    val sendingEmailText = stringResource(R.string.captcha_sending_email)
    val sentOkFallback = stringResource(R.string.captcha_sent_ok_fallback)
    val verifyFailedHint = stringResource(R.string.captcha_verify_failed_hint)
    val reloadingText = stringResource(R.string.captcha_reloading)

    var loading by remember { mutableStateOf(true) }
    var reloadingChallenge by remember { mutableStateOf(false) }
    var challenge by remember { mutableStateOf<SliderCaptchaChallengeDto?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var thumbLeftPx by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    /** 发信成功等正向反馈，用于绿色文案 */
    var statusPositive by remember { mutableStateOf(false) }
    /** 结果提示停留期间禁止关窗、换题按钮，避免用户未读完就关掉 */
    var holdOutcomeUi by remember { mutableStateOf(false) }

    fun maxPieceOffset(d: SliderCaptchaChallengeDto): Int =
        max(0, d.bgWidth - d.sliderWidth)

    LaunchedEffect(challenge?.captchaToken) {
        thumbLeftPx = 0f
        statusPositive = false
    }

    suspend fun reloadChallengeInitial() {
        loading = true
        reloadingChallenge = false
        loadError = null
        challenge = null
        thumbLeftPx = 0f
        status = ""
        statusPositive = false
        holdOutcomeUi = false
        when (val r = userApi.getSliderCaptchaChallenge()) {
            is SliderCaptchaLoadResult.Ok -> {
                challenge = r.data
                loading = false
            }
            is SliderCaptchaLoadResult.Err -> {
                loadError = r.message
                loading = false
            }
        }
    }

    suspend fun reloadChallengeAfterFailure() {
        val previous = status
        reloadingChallenge = true
        try {
            when (val r = userApi.getSliderCaptchaChallenge()) {
                is SliderCaptchaLoadResult.Ok -> {
                    challenge = r.data
                    thumbLeftPx = 0f
                    status = ""
                    statusPositive = false
                    Log.w(CAPTCHA_LOG_TAG, "已加载新拼图 tokenPrefix=${r.data.captchaToken.take(8)}")
                }
                is SliderCaptchaLoadResult.Err -> {
                    status = if (previous.isNotBlank()) "$previous\n${r.message}" else r.message
                    Log.e(CAPTCHA_LOG_TAG, "换题失败: ${r.message}")
                }
            }
        } finally {
            reloadingChallenge = false
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            reloadChallengeInitial()
        }
    }

    suspend fun verifyAndSendAfterRelease(
        d: SliderCaptchaChallengeDto,
        railWpx: Float,
        thumbWpx: Float,
    ) {
        if (busy) return
        busy = true
        statusPositive = false
        status = verifyingText
        val mx = maxPieceOffset(d)
        val off = slideOffsetXIntFromThumb(thumbLeftPx, railWpx, thumbWpx, mx)
        Log.w(
            CAPTCHA_LOG_TAG,
            "松手校验 off=$off mx=$mx thumbLeftPx=$thumbLeftPx railWpx=$railWpx thumbWpx=$thumbWpx",
        )
        when (val v = userApi.verifySliderCaptcha(d.captchaToken, off)) {
            is SliderCaptchaVerifyResult.Err -> {
                val apiMsg = v.message.ifBlank { verifyFailedHint }
                status = "$apiMsg\n$verifyFailedHint"
                Log.w(CAPTCHA_LOG_TAG, "滑块未通过: $apiMsg (off=$off)")
                busy = false
                holdOutcomeUi = true
                delay(900)
                holdOutcomeUi = false
                reloadChallengeAfterFailure()
            }
            is SliderCaptchaVerifyResult.Ok -> {
                status = sendingEmailText
                val uname = username.ifBlank { "用户" }
                val send = userApi.sendVerificationCode(
                    email,
                    uname,
                    v.captchaPassToken,
                )
                if (send.success) {
                    busy = false
                    status = send.message.ifBlank { sentOkFallback }
                    statusPositive = true
                    holdOutcomeUi = true
                    Log.w(CAPTCHA_LOG_TAG, "发信成功 email=$email")
                    delay(1200)
                    holdOutcomeUi = false
                    onCodeSent()
                } else {
                    busy = false
                    statusPositive = false
                    status = "${send.message}\n$verifyFailedHint"
                    Log.w(CAPTCHA_LOG_TAG, "发信失败: ${send.message}")
                    holdOutcomeUi = true
                    delay(1400)
                    holdOutcomeUi = false
                    reloadChallengeAfterFailure()
                }
            }
        }
    }

    Dialog(onDismissRequest = { if (!busy && !reloadingChallenge && !holdOutcomeUi) onDismiss() }) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A2E),
                contentColor = Color.White,
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.captcha_security_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.captcha_security_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )

                when {
                    loading -> {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(color = Color(0xFFE94560))
                        }
                    }
                    loadError != null -> {
                        Text(loadError!!, color = Color(0xFFE94560))
                        OutlinedButton(
                            onClick = {
                                scope.launch { reloadChallengeInitial() }
                            },
                        ) { Text(stringResource(R.string.captcha_retry)) }
                    }
                    challenge != null -> {
                        val d = challenge!!
                        val mx = maxPieceOffset(d)
                        var laidOutCaptchaWpx by remember(d.captchaToken) { mutableFloatStateOf(0f) }
                        val railWpxNominal = with(density) { d.bgWidth.dp.toPx() }
                        val railWpx =
                            if (laidOutCaptchaWpx > 1f) laidOutCaptchaWpx else railWpxNominal
                        val thumbWpx = with(density) { RailThumbW.toPx() }
                        val pieceWpx = with(density) { d.sliderWidth.dp.toPx() }
                        val slideXf = slideXFloatFromThumb(thumbLeftPx, railWpx, thumbWpx, mx)

                        val bgBitmap = remember(d.bgImage) { decodePngDataUrlToImageBitmap(d.bgImage) }
                        val pieceBitmap = remember(d.sliderImage) { decodePngDataUrlToImageBitmap(d.sliderImage) }
                        if (bgBitmap == null || pieceBitmap == null) {
                            Text(
                                text = stringResource(R.string.captcha_image_decode_error),
                                color = Color(0xFFE94560),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            OutlinedButton(onClick = { scope.launch { reloadChallengeInitial() } }) {
                                Text(stringResource(R.string.captcha_retry))
                            }
                        } else {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Box(
                                modifier = Modifier
                                    .size(d.bgWidth.dp, d.bgHeight.dp)
                                    .align(Alignment.CenterHorizontally)
                                    .onGloballyPositioned { coords ->
                                        val w = coords.size.width.toFloat()
                                        if (w > 0f) laidOutCaptchaWpx = w
                                    },
                            ) {
                                Image(
                                    bitmap = bgBitmap,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.FillBounds,
                                )
                                Image(
                                    bitmap = pieceBitmap,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset {
                                            val xPx = (slideXf * (railWpx / d.bgWidth.toFloat()))
                                                .roundToInt()
                                                .coerceIn(
                                                    0,
                                                    max(0, (railWpx - pieceWpx).roundToInt()),
                                                )
                                            IntOffset(
                                                x = xPx,
                                                y = d.puzzleY.dp.roundToPx(),
                                            )
                                        }
                                        .width(d.sliderWidth.dp)
                                        .height(d.sliderHeight.dp),
                                    contentScale = ContentScale.FillBounds,
                                )
                            }

                            BoxWithConstraints(
                                modifier = Modifier
                                    .width(d.bgWidth.dp)
                                    .height(RailH)
                                    .align(Alignment.CenterHorizontally)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(Color(0x8CFFFFFF))
                                    .border(1.dp, Color(0x386A5ACD), RoundedCornerShape(22.dp))
                                    .pointerInput(
                                        d.captchaToken,
                                        busy,
                                        reloadingChallenge,
                                        holdOutcomeUi,
                                        railWpx,
                                        thumbWpx,
                                        laidOutCaptchaWpx,
                                        d,
                                    ) {
                                        detectDragGestures(
                                            onDragStart = { start ->
                                                if (busy || reloadingChallenge || holdOutcomeUi) return@detectDragGestures
                                                if (railWpx < thumbWpx + 2f) return@detectDragGestures
                                                val tm = (railWpx - thumbWpx).coerceAtLeast(1f)
                                                // 与 Web 一致：手指按在滑块上时不能把触点 x 当成滑块左缘（会偏最多约半滑块宽）；
                                                // 仅当点在滑块外时，把滑块中心移到触点（等同 Web 点轨道）。
                                                val onThumb =
                                                    start.x >= thumbLeftPx && start.x <= thumbLeftPx + thumbWpx
                                                if (!onThumb) {
                                                    thumbLeftPx = (start.x - thumbWpx / 2f).coerceIn(0f, tm)
                                                }
                                                Log.w(
                                                    CAPTCHA_LOG_TAG,
                                                    "dragStart fingerX=${start.x} thumbLeftPx=$thumbLeftPx onThumb=$onThumb",
                                                )
                                            },
                                            onDrag = { change, dragAmount ->
                                                if (busy || reloadingChallenge || holdOutcomeUi) return@detectDragGestures
                                                val tm = (railWpx - thumbWpx).coerceAtLeast(1f)
                                                thumbLeftPx = (thumbLeftPx + dragAmount.x).coerceIn(0f, tm)
                                                change.consume()
                                            },
                                            onDragEnd = {
                                                if (busy || reloadingChallenge || holdOutcomeUi) return@detectDragGestures
                                                Log.w(CAPTCHA_LOG_TAG, "dragEnd thumbLeftPx=$thumbLeftPx")
                                                scope.launch {
                                                    verifyAndSendAfterRelease(d, railWpx, thumbWpx)
                                                }
                                            },
                                            onDragCancel = {
                                                Log.w(CAPTCHA_LOG_TAG, "dragCancel")
                                            },
                                        )
                                    },
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(0.85f)
                                        .height(8.dp)
                                        .align(Alignment.Center)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFFE0E0E8), Color(0xFFC8C8D8)),
                                            ),
                                        ),
                                )
                                val thumbLeftDp = with(density) { thumbLeftPx.toDp() }
                                Box(
                                    modifier = Modifier
                                        .offset(x = thumbLeftDp, y = 4.dp)
                                        .width(RailThumbW)
                                        .height(RailThumbH)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(Color(0xFF6A5ACD), Color(0xFF9B7DD4)),
                                            ),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "››",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            if (busy || reloadingChallenge) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color(0xFFE94560),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (reloadingChallenge) {
                                            reloadingText
                                        } else {
                                            status.ifBlank { verifyingText }
                                        },
                                        color = Color.LightGray,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            if (status.isNotEmpty() && !busy && !reloadingChallenge) {
                                Text(
                                    text = status,
                                    color = if (statusPositive) Color(0xFF4CAF50) else Color(0xFFE94560),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        status = ""
                                        reloadChallengeInitial()
                                        busy = false
                                    }
                                },
                                enabled = !busy && !reloadingChallenge && !holdOutcomeUi,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            ) { Text(stringResource(R.string.captcha_refresh)) }
                            }
                        }
                    }
            }
        }
    }
}
}
