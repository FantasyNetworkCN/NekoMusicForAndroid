package com.neko.music.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.graphics.BitmapFactory
import android.util.Base64
import com.neko.music.R
import com.neko.music.data.api.SliderCaptchaChallengeDto
import com.neko.music.data.api.SliderCaptchaLoadResult
import com.neko.music.data.api.SliderCaptchaVerifyResult
import com.neko.music.data.api.UserApi
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 服务端返回的 PNG Data URL（`data:image/png;base64,...`）解码为 [ImageBitmap]。
 * 不使用 Coil：全局 ImageLoader 面向网络图，data URI 往往无法显示。
 */
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
    var loading by remember { mutableStateOf(true) }
    var challenge by remember { mutableStateOf<SliderCaptchaChallengeDto?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var sliderX by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    fun maxOffset(d: SliderCaptchaChallengeDto): Float =
        max(0, d.bgWidth - d.sliderWidth).toFloat()

    suspend fun reloadChallenge() {
        loading = true
        loadError = null
        challenge = null
        sliderX = 0f
        status = ""
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

    LaunchedEffect(visible) {
        if (visible) {
            reloadChallenge()
        }
    }

    suspend fun verifyAndSendAfterRelease() {
        if (busy) return
        val cur = challenge ?: return
        busy = true
        status = ""
        val off = sliderX.roundToInt().coerceIn(0, max(0, cur.bgWidth - cur.sliderWidth))
        when (val v = userApi.verifySliderCaptcha(cur.captchaToken, off)) {
            is SliderCaptchaVerifyResult.Err -> {
                status = v.message
                busy = false
                reloadChallenge()
            }
            is SliderCaptchaVerifyResult.Ok -> {
                val uname = username.ifBlank { "用户" }
                val send = userApi.sendVerificationCode(
                    email,
                    uname,
                    v.captchaPassToken,
                )
                busy = false
                if (send.success) {
                    onCodeSent()
                } else {
                    status = send.message
                    reloadChallenge()
                }
            }
        }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
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
                                scope.launch { reloadChallenge() }
                            },
                        ) { Text(stringResource(R.string.captcha_retry)) }
                    }
                    challenge != null -> {
                        val d = challenge!!
                        val maxX = maxOffset(d)
                        val bgBitmap = remember(d.bgImage) { decodePngDataUrlToImageBitmap(d.bgImage) }
                        val pieceBitmap = remember(d.sliderImage) { decodePngDataUrlToImageBitmap(d.sliderImage) }
                        if (bgBitmap == null || pieceBitmap == null) {
                            Text(
                                text = stringResource(R.string.captcha_image_decode_error),
                                color = Color(0xFFE94560),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            OutlinedButton(onClick = { scope.launch { reloadChallenge() } }) {
                                Text(stringResource(R.string.captcha_retry))
                            }
                        } else {
                        Box(
                            modifier = Modifier
                                .size(d.bgWidth.dp, d.bgHeight.dp)
                                .align(Alignment.CenterHorizontally),
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
                                    .offset(
                                        x = sliderX.roundToInt().dp,
                                        y = d.puzzleY.dp,
                                    )
                                    .width(d.sliderWidth.dp)
                                    .height(d.sliderHeight.dp),
                                contentScale = ContentScale.FillBounds,
                            )
                        }
                        Slider(
                            value = sliderX.coerceIn(0f, maxX),
                            onValueChange = { sliderX = it },
                            onValueChangeFinished = {
                                scope.launch { verifyAndSendAfterRelease() }
                            },
                            valueRange = 0f..maxX,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (busy) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color(0xFFE94560),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                        if (status.isNotEmpty()) {
                            Text(
                                text = status,
                                color = Color(0xFFE94560),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    status = ""
                                    reloadChallenge()
                                    busy = false
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) { Text(stringResource(R.string.captcha_refresh)) }
                        }
                    }
                }
            }
        }
    }
}
