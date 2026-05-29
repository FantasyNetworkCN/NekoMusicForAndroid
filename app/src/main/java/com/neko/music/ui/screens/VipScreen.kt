package com.neko.music.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.R
import com.neko.music.data.api.PlaylistApi
import com.neko.music.data.api.VipApi
import com.neko.music.data.api.VipPricingItem
import com.neko.music.data.manager.TokenManager
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LiquidGlassPanel
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.VipPill
import com.neko.music.ui.components.PlaylistPageDarkTintOverlay
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.theme.RoseRed
import com.neko.music.util.PayLauncher
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipScreen(
    token: String,
    onBackClick: () -> Unit,
    onVipStatusUpdated: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenManager = remember { TokenManager(context) }
    val vipApi = remember(token) { VipApi(token) }

    var loading by remember { mutableStateOf(true) }
    var payingId by remember { mutableStateOf<Int?>(null) }
    var items by remember { mutableStateOf<List<VipPricingItem>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isVip by remember { mutableStateOf(tokenManager.isVip()) }
    var vipExpiresAt by remember { mutableStateOf(tokenManager.getVipExpiresAt()) }
    var payWeb by remember { mutableStateOf<Pair<String, String>?>(null) }

    val scheme = MaterialTheme.colorScheme
    val isDarkTheme = isSystemInDarkTheme()
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)
    val heroGlass = LiquidGlassDefaults.vipCenterHero
    val pricingGlass = LiquidGlassDefaults.vipCenterPricingCard
    val listBottomInset = LiquidGlassDefaults.vipCenterListBottomInsetDp

    LaunchedEffect(token) {
        loading = true
        errorMessage = null
        val env = vipApi.fetchPricing()
        loading = false
        if (env.success) {
            items = env.data.sortedBy { it.sortOrder }
        } else {
            errorMessage = env.message.ifBlank { context.getString(R.string.vip_pricing_load_failed) }
        }
    }

    LaunchedEffect(token) {
        val pl = PlaylistApi(token, context).getMyPlaylists()
        if (pl.success) {
            tokenManager.updateVipStatus(pl.isVip, pl.vipExpiresAt)
            isVip = pl.isVip
            vipExpiresAt = pl.vipExpiresAt
            onVipStatusUpdated()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop)
        ) {
            Image(
                painter = painterResource(id = R.drawable.playlist_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            PlaylistPageDarkTintOverlay(enabled = isDarkTheme)
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = if (isDarkTheme) Color(0xFFF0F0F5) else scheme.onSurface,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.vip_center_title),
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) {
                                Color(0xFFF0F0F5).copy(alpha = 0.95f)
                            } else {
                                scheme.onSurface
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = if (isDarkTheme) {
                                    Color(0xFFB8B8D1).copy(alpha = 0.9f)
                                } else {
                                    scheme.onSurface
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
                    when {
                        loading -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = RoseRed)
                            }
                        }

                        errorMessage != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = {
                                    scope.launch {
                                        loading = true
                                        errorMessage = null
                                        val env = vipApi.fetchPricing()
                                        loading = false
                                        if (env.success) {
                                            items = env.data.sortedBy { it.sortOrder }
                                        } else {
                                            errorMessage = env.message.ifBlank {
                                                context.getString(R.string.vip_pricing_load_failed)
                                            }
                                        }
                                    }
                                }) {
                                    Text(stringResource(R.string.retry))
                                }
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 16.dp,
                                    bottom = listBottomInset
                                ),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                item {
                                    VipStatusHeroCard(
                                        isVip = isVip,
                                        vipExpiresAt = vipExpiresAt,
                                        isDarkTheme = isDarkTheme,
                                        glass = heroGlass
                                    )
                                }
                                item {
                                    Text(
                                        text = stringResource(R.string.vip_choose_plan),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkTheme) {
                                            Color(0xFFF0F0F5).copy(alpha = 0.92f)
                                        } else {
                                            scheme.onSurface
                                        },
                                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                    )
                                }
                                items(items, key = { it.id }) { item ->
                                    VipPricingCard(
                                        item = item,
                                        paying = payingId == item.id,
                                        isDarkTheme = isDarkTheme,
                                        glass = pricingGlass,
                                        onPay = { payType ->
                                            scope.launch {
                                                payingId = item.id
                                                val res = vipApi.createPayOrder(item.id, payType)
                                                payingId = null
                                                if (res.success && res.data != null) {
                                                    when (
                                                        val outcome = PayLauncher.launch(
                                                            context,
                                                            payType,
                                                            res.data
                                                        )
                                                    ) {
                                                        is PayLauncher.PayLaunchOutcome.Ok -> { }
                                                        is PayLauncher.PayLaunchOutcome.OpenWeb -> {
                                                            val title = if (payType == "wxpay") {
                                                                context.getString(R.string.vip_pay_wechat)
                                                            } else {
                                                                context.getString(R.string.vip_pay_alipay)
                                                            }
                                                            payWeb = outcome.url to title
                                                        }
                                                        is PayLauncher.PayLaunchOutcome.Fail -> {
                                                            val msg = when (outcome.reason) {
                                                                "wechat_missing" ->
                                                                    context.getString(R.string.vip_wechat_not_installed)
                                                                "wechat_failed", "alipay_failed" ->
                                                                    context.getString(R.string.vip_pay_launch_failed)
                                                                "empty" -> context.getString(R.string.vip_pay_no_url)
                                                                else -> context.getString(R.string.vip_pay_launch_failed)
                                                            }
                                                            android.widget.Toast.makeText(
                                                                context,
                                                                msg,
                                                                android.widget.Toast.LENGTH_LONG
                                                            ).show()
                                                        }
                                                    }
                                                } else {
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        res.message
                                                            ?: context.getString(R.string.vip_pay_create_failed),
                                                        android.widget.Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        payWeb?.let { (webUrl, webTitle) ->
            VipPayWebScreen(
                url = webUrl,
                title = webTitle,
                onClose = { payWeb = null },
                onPaymentAppLaunched = { payWeb = null }
            )
        }
    }
}

@Composable
private fun VipStatusHeroCard(
    isVip: Boolean,
    vipExpiresAt: String?,
    isDarkTheme: Boolean,
    glass: LiquidGlassPanel
) {
    val shape = RoundedCornerShape(20.dp)
    val scheme = MaterialTheme.colorScheme

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        backgroundAlpha = glass.tint.background(isDarkTheme),
        borderAlpha = glass.tint.border(isDarkTheme),
        highlightAlpha = glass.tint.highlight(isDarkTheme),
        liquidBlur = glass.liquid.blur,
        liquidLensHeight = glass.liquid.lensHeight,
        liquidLensAmount = glass.liquid.lensAmount,
        borderColor = if (isVip) {
            Color(0xFFFFB300).copy(alpha = if (isDarkTheme) 0.45f else 0.35f)
        } else {
            if (isDarkTheme) Color.White else scheme.outline
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isVip) {
                            stringResource(R.string.vip_status_active)
                        } else {
                            stringResource(R.string.vip_open_membership)
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) {
                            Color(0xFFF0F0F5).copy(alpha = 0.95f)
                        } else {
                            scheme.onSurface
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val exp = vipExpiresAt?.take(10)?.takeIf { it.length == 10 }
                    Text(
                        text = when {
                            isVip && exp != null -> stringResource(R.string.vip_status_expires, exp)
                            isVip -> stringResource(R.string.vip_member_benefits)
                            else -> stringResource(R.string.vip_non_member_benefits)
                        },
                        fontSize = 13.sp,
                        color = if (isDarkTheme) {
                            Color(0xFFB8B8D1).copy(alpha = 0.85f)
                        } else {
                            scheme.onSurfaceVariant
                        },
                        lineHeight = 18.sp
                    )
                }
                VipPill(isVip = isVip, onClick = { })
            }
        }
    }
}

@Composable
private fun VipPricingCard(
    item: VipPricingItem,
    paying: Boolean,
    isDarkTheme: Boolean,
    glass: LiquidGlassPanel,
    onPay: (String) -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val scheme = MaterialTheme.colorScheme
    val label = buildString {
        if (item.months > 0) append(item.months).append(stringResource(R.string.vip_months_suffix))
        if (item.days > 0) append(item.days).append(stringResource(R.string.vip_days_suffix))
    }.ifBlank { stringResource(R.string.vip_plan_default) }

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        backgroundAlpha = glass.tint.background(isDarkTheme),
        borderAlpha = glass.tint.border(isDarkTheme),
        highlightAlpha = glass.tint.highlight(isDarkTheme),
        liquidBlur = glass.liquid.blur,
        liquidLensHeight = glass.liquid.lensHeight,
        liquidLensAmount = glass.liquid.lensAmount,
        borderColor = if (isDarkTheme) Color.White else scheme.outline
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    label,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDarkTheme) {
                        Color(0xFFF0F0F5).copy(alpha = 0.95f)
                    } else {
                        scheme.onSurface
                    },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.vip_price_yuan, item.priceYuan),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = RoseRed
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onPay("alipay") },
                    enabled = !paying,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoseRed)
                ) {
                    if (paying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text(stringResource(R.string.vip_pay_alipay))
                    }
                }
                OutlinedButton(
                    onClick = { onPay("wxpay") },
                    enabled = !paying,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.vip_pay_wechat))
                }
            }
        }
    }
}
