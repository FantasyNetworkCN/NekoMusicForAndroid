package com.neko.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neko.music.R
import com.neko.music.data.api.SsoStartResult
import com.neko.music.ui.components.AuthErrorText
import com.neko.music.ui.components.AuthPageShell
import com.neko.music.ui.components.AuthPrimaryButton
import com.neko.music.ui.components.AuthTextLink
import com.neko.music.ui.theme.RoseRed
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onBackClick: () -> Unit,
    onOpenAuthWeb: (url: String, title: String) -> Unit,
    onPrivacyClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userApi = remember { com.neko.music.data.api.UserApi() }
    var ssoLoading by remember { mutableStateOf(false) }
    var portalLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var hasAgreedPrivacy by remember { mutableStateOf(false) }

    fun openPortal(target: String, title: String) {
        if (!hasAgreedPrivacy) {
            errorMessage = "请先阅读并勾选同意《隐私政策》"
            return
        }
        if (portalLoading) return
        portalLoading = true
        errorMessage = ""
        scope.launch {
            when (val result = userApi.fetchPortalUrl(target)) {
                is SsoStartResult.Ok -> onOpenAuthWeb(result.url, title)
                is SsoStartResult.Err -> errorMessage = result.message
            }
            portalLoading = false
        }
    }

    fun startSso() {
        if (!hasAgreedPrivacy) {
            errorMessage = "请先阅读并勾选同意《隐私政策》"
            return
        }
        if (ssoLoading) return
        ssoLoading = true
        errorMessage = ""
        scope.launch {
            when (val result = userApi.startSso()) {
                is SsoStartResult.Ok -> {
                    try {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(result.url),
                            )
                        )
                    } catch (e: Exception) {
                        errorMessage = "无法打开 NekoAuth 登录页：${e.message}"
                    }
                }
                is SsoStartResult.Err -> errorMessage = result.message
            }
            ssoLoading = false
        }
    }

    BackHandler(onBack = onBackClick)

    AuthPageShell(
        topBarTitle = stringResource(id = R.string.login),
        headline = stringResource(id = R.string.app_title),
        subtitle = stringResource(id = R.string.nekoauth_password_hint),
        onBack = onBackClick,
    ) { pageBackdrop ->
        Text(
            text = "账号注册、登录、密码与资料管理全部由 NekoAuth 统一处理。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.height(14.dp))
        PrivacyAgreementRow(
            checked = hasAgreedPrivacy,
            onCheckedChange = {
                hasAgreedPrivacy = it
                errorMessage = ""
            },
            onPrivacyClick = onPrivacyClick,
        )
        AuthErrorText(message = errorMessage)
        Spacer(modifier = Modifier.height(16.dp))
        AuthPrimaryButton(
            text = "使用 NekoAuth 单点登录",
            onClick = { startSso() },
            pageBackdrop = pageBackdrop,
            enabled = !ssoLoading && !portalLoading,
            loading = ssoLoading,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AuthTextLink(
                text = "注册账号",
                onClick = { openPortal("register", "NekoAuth 注册") },
            )
            AuthTextLink(
                text = "账号中心",
                onClick = { openPortal("account", "NekoAuth 账号中心") },
            )
            AuthTextLink(
                text = "找回密码",
                onClick = { openPortal("forgot-password", "NekoAuth 找回密码") },
            )
        }
    }
}

@Composable
private fun PrivacyAgreementRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onPrivacyClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = RoseRed,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Text(
            text = "我已阅读并同意",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Text(
            text = "《隐私政策》",
            color = RoseRed,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onPrivacyClick),
        )
    }
}
