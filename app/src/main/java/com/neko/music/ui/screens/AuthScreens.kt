package com.neko.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neko.music.R
import com.neko.music.ui.components.AuthErrorText
import com.neko.music.ui.components.AuthFooterPrompt
import com.neko.music.ui.components.AuthGlassTextField
import androidx.compose.material3.MaterialTheme
import com.neko.music.ui.components.AuthPageShell
import com.neko.music.ui.components.AuthPrimaryButton
import com.neko.music.ui.components.AuthTextLink
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.theme.RoseRed
import kotlinx.coroutines.launch

private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onBackClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onPrivacyClick: () -> Unit = {},
) {
    val context = LocalContext.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var hasAgreedPrivacy by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val tokenManager = com.neko.music.data.manager.TokenManager(context)
    val userApi = com.neko.music.data.api.UserApi()

    val pleaseEnterEmailAndPassword = stringResource(id = R.string.please_enter_email_and_password)
    val loginFailed = stringResource(id = R.string.login_failed)
    val privacyAgreementRequired = "请先阅读并勾选同意《隐私政策》"

    BackHandler(onBack = onBackClick)

    AuthPageShell(
        topBarTitle = stringResource(id = R.string.login),
        headline = stringResource(id = R.string.app_title),
        subtitle = stringResource(id = R.string.welcome_back),
        onBack = onBackClick,
    ) { pageBackdrop ->
        AuthGlassTextField(
            value = username,
            onValueChange = {
                username = it
                errorMessage = ""
            },
            label = stringResource(id = R.string.email),
            leadingIcon = Icons.Default.Email,
        )
        Spacer(modifier = Modifier.height(14.dp))
        AuthGlassTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = ""
            },
            label = stringResource(id = R.string.password),
            leadingIcon = Icons.Default.Lock,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        AuthErrorText(message = errorMessage)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            AuthTextLink(
                text = stringResource(id = R.string.forgot_password) + "?",
                onClick = onForgotPasswordClick,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        PrivacyAgreementRow(
            checked = hasAgreedPrivacy,
            onCheckedChange = {
                hasAgreedPrivacy = it
                errorMessage = ""
            },
            onPrivacyClick = onPrivacyClick,
        )
        Spacer(modifier = Modifier.height(20.dp))
        AuthPrimaryButton(
            text = stringResource(id = R.string.login),
            onClick = {
                if (!hasAgreedPrivacy) {
                    errorMessage = privacyAgreementRequired
                    return@AuthPrimaryButton
                }
                if (username.isEmpty() || password.isEmpty()) {
                    errorMessage = pleaseEnterEmailAndPassword
                    return@AuthPrimaryButton
                }
                isLoading = true
                scope.launch {
                    try {
                        val response = userApi.login(username, password)
                        isLoading = false
                        if (response.success && response.data != null) {
                            tokenManager.saveToken(
                                token = response.data.token,
                                userId = response.data.user.id,
                                username = response.data.user.username,
                                email = response.data.user.email,
                                isVip = response.data.user.isVip,
                                vipExpiresAt = response.data.user.vipExpiresAt,
                            )
                            onLoginSuccess()
                        } else {
                            errorMessage = response.message
                        }
                    } catch (e: Exception) {
                        isLoading = false
                        errorMessage = loginFailed.format(e.message ?: "")
                    }
                }
            },
            pageBackdrop = pageBackdrop,
            enabled = !isLoading,
            loading = isLoading,
        )
        Spacer(modifier = Modifier.height(14.dp))
        AuthFooterPrompt(
            prompt = stringResource(id = R.string.no_account_yet),
            link = stringResource(id = R.string.register_now),
            onLinkClick = onRegisterClick,
        )
    }
}

@Composable
fun RegisterScreen(
    onBackClick: () -> Unit,
    onLoginClick: () -> Unit,
    onPrivacyClick: () -> Unit = {},
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf("") }
    var showCaptchaDialog by remember { mutableStateOf(false) }
    var hasAgreedPrivacy by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val userApi = com.neko.music.data.api.UserApi()

    val pleaseFillAllFields = stringResource(id = R.string.please_fill_all_fields)
    val usernameLengthError = stringResource(id = R.string.username_length_error)
    val passwordLengthError = stringResource(id = R.string.password_length_error)
    val passwordMismatch = stringResource(id = R.string.password_mismatch)
    val emailFormatError = stringResource(id = R.string.email_format_error)
    val registerFailed = stringResource(id = R.string.register_failed)
    val pleaseEnterEmailFirst = stringResource(id = R.string.please_enter_email_first)
    val privacyAgreementRequired = "请先阅读并勾选同意《隐私政策》"

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
        }
    }

    BackHandler {
        if (showCaptchaDialog) showCaptchaDialog = false else onBackClick()
    }

    val pageBackdrop = rememberLiquidPageBackdrop(MaterialTheme.colorScheme.background)

    Box(modifier = Modifier.fillMaxSize()) {
        AuthPageShell(
            topBarTitle = stringResource(id = R.string.register),
            headline = stringResource(id = R.string.app_title),
            subtitle = stringResource(id = R.string.create_new_account),
            onBack = {
                if (showCaptchaDialog) showCaptchaDialog = false else onBackClick()
            },
            pageBackdrop = pageBackdrop,
        ) { backdrop ->
            AuthGlassTextField(
                value = username,
                onValueChange = {
                    username = it
                    errorMessage = ""
                },
                label = stringResource(id = R.string.username),
                leadingIcon = Icons.Default.Person,
            )
            Spacer(modifier = Modifier.height(14.dp))
            AuthGlassTextField(
                value = email,
                onValueChange = {
                    email = it
                    errorMessage = ""
                },
                label = stringResource(id = R.string.email),
                leadingIcon = Icons.Default.Email,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            Spacer(modifier = Modifier.height(14.dp))
            AuthGlassTextField(
                value = verificationCode,
                onValueChange = {
                    verificationCode = it
                    errorMessage = ""
                },
                label = stringResource(id = R.string.verification_code),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                trailingIcon = {
                    TextButton(
                        onClick = {
                            if (email.isEmpty()) {
                                errorMessage = pleaseEnterEmailFirst
                                return@TextButton
                            }
                            if (!email.matches(EMAIL_REGEX)) {
                                errorMessage = emailFormatError
                                return@TextButton
                            }
                            if (countdown > 0 || showCaptchaDialog) return@TextButton
                            errorMessage = ""
                            showCaptchaDialog = true
                        },
                        enabled = !showCaptchaDialog && countdown == 0,
                    ) {
                        Text(
                            text = if (countdown > 0) {
                                stringResource(id = R.string.retry_after_seconds, countdown)
                            } else {
                                stringResource(id = R.string.send_verification_code)
                            },
                            color = if (countdown > 0) Color.Gray else RoseRed,
                            fontSize = 12.sp,
                        )
                    }
                },
            )
            Spacer(modifier = Modifier.height(14.dp))
            AuthGlassTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = ""
                },
                label = stringResource(id = R.string.password),
                leadingIcon = Icons.Default.Lock,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Spacer(modifier = Modifier.height(14.dp))
            AuthGlassTextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    errorMessage = ""
                },
                label = stringResource(id = R.string.confirm_password),
                leadingIcon = Icons.Default.Lock,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            AuthErrorText(message = errorMessage)
            Spacer(modifier = Modifier.height(8.dp))
            PrivacyAgreementRow(
                checked = hasAgreedPrivacy,
                onCheckedChange = {
                    hasAgreedPrivacy = it
                    errorMessage = ""
                },
                onPrivacyClick = onPrivacyClick,
            )
            Spacer(modifier = Modifier.height(20.dp))
            AuthPrimaryButton(
                text = stringResource(id = R.string.register),
                onClick = {
                    if (!hasAgreedPrivacy) {
                        errorMessage = privacyAgreementRequired
                        return@AuthPrimaryButton
                    }
                    if (username.isEmpty() || email.isEmpty() || password.isEmpty() ||
                        confirmPassword.isEmpty() || verificationCode.isEmpty()
                    ) {
                        errorMessage = pleaseFillAllFields
                        return@AuthPrimaryButton
                    }
                    if (username.length < 3 || username.length > 20) {
                        errorMessage = usernameLengthError
                        return@AuthPrimaryButton
                    }
                    if (password.length < 6 || password.length > 30) {
                        errorMessage = passwordLengthError
                        return@AuthPrimaryButton
                    }
                    if (password != confirmPassword) {
                        errorMessage = passwordMismatch
                        return@AuthPrimaryButton
                    }
                    if (!email.matches(EMAIL_REGEX)) {
                        errorMessage = emailFormatError
                        return@AuthPrimaryButton
                    }
                    isLoading = true
                    scope.launch {
                        try {
                            val response = userApi.register(
                                username.trim(),
                                password,
                                email.trim(),
                                verificationCode.trim(),
                            )
                            isLoading = false
                            if (response.success) {
                                onLoginClick()
                            } else {
                                errorMessage = response.message
                            }
                        } catch (e: Exception) {
                            isLoading = false
                            errorMessage = registerFailed.format(e.message ?: "")
                        }
                    }
                },
                pageBackdrop = backdrop,
                enabled = !isLoading,
                loading = isLoading,
            )
            Spacer(modifier = Modifier.height(14.dp))
            AuthFooterPrompt(
                prompt = stringResource(id = R.string.already_have_account),
                link = stringResource(id = R.string.login_now),
                onLinkClick = onLoginClick,
            )
        }

        RegisterSliderCaptchaDialog(
            visible = showCaptchaDialog,
            sampleBackdrop = pageBackdrop,
            userApi = userApi,
            email = email.trim(),
            username = username.trim().ifBlank { "用户" },
            onDismiss = { showCaptchaDialog = false },
            onCodeSent = {
                showCaptchaDialog = false
                countdown = 60
            },
        )
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
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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

@Composable
fun ForgotPasswordScreen(
    onResetSuccess: () -> Unit,
    onBackClick: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSendingCode by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val userApi = com.neko.music.data.api.UserApi()

    val pleaseEnterEmail = stringResource(id = R.string.please_enter_email_for_reset)
    val pleaseEnterCode = stringResource(id = R.string.please_enter_code_for_reset)
    val pleaseEnterNewPassword = stringResource(id = R.string.please_enter_new_password)
    val newPasswordLengthError = stringResource(id = R.string.new_password_length_error_reset)
    val passwordMismatch = stringResource(id = R.string.password_mismatch)
    val emailFormatError = stringResource(id = R.string.email_format_error)
    val resetPasswordFailed = stringResource(id = R.string.reset_password_failed)
    val sendResetCodeFailed = stringResource(id = R.string.send_reset_code_failed)

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
        }
    }

    BackHandler(onBack = onBackClick)

    AuthPageShell(
        topBarTitle = stringResource(id = R.string.forgot_password),
        headline = stringResource(id = R.string.forgot_password),
        subtitle = stringResource(id = R.string.enter_email_to_reset),
        onBack = onBackClick,
    ) { pageBackdrop ->
        AuthGlassTextField(
            value = email,
            onValueChange = {
                email = it
                errorMessage = ""
            },
            label = stringResource(id = R.string.email),
            leadingIcon = Icons.Default.Email,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(modifier = Modifier.height(14.dp))
        AuthGlassTextField(
            value = code,
            onValueChange = {
                code = it
                errorMessage = ""
            },
            label = stringResource(id = R.string.verification_code),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            trailingIcon = {
                TextButton(
                    onClick = {
                        if (email.isEmpty()) {
                            errorMessage = pleaseEnterEmail
                            return@TextButton
                        }
                        if (countdown > 0) return@TextButton
                        isSendingCode = true
                        scope.launch {
                            try {
                                val response = userApi.sendResetCode(email)
                                isSendingCode = false
                                if (response.success) {
                                    countdown = 60
                                } else {
                                    errorMessage = response.message
                                }
                            } catch (e: Exception) {
                                isSendingCode = false
                                errorMessage = sendResetCodeFailed.format(e.message ?: "")
                            }
                        }
                    },
                    enabled = !isSendingCode && countdown == 0,
                ) {
                    if (isSendingCode) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = RoseRed,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = if (countdown > 0) {
                                stringResource(id = R.string.retry_after_seconds, countdown)
                            } else {
                                stringResource(id = R.string.send_reset_code)
                            },
                            color = if (countdown > 0) Color.Gray else RoseRed,
                            fontSize = 12.sp,
                        )
                    }
                }
            },
        )
        Spacer(modifier = Modifier.height(14.dp))
        AuthGlassTextField(
            value = newPassword,
            onValueChange = {
                newPassword = it
                errorMessage = ""
            },
            label = stringResource(id = R.string.new_password),
            leadingIcon = Icons.Default.Lock,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Spacer(modifier = Modifier.height(14.dp))
        AuthGlassTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                errorMessage = ""
            },
            label = stringResource(id = R.string.confirm_new_password),
            leadingIcon = Icons.Default.Lock,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        AuthErrorText(message = errorMessage)
        Spacer(modifier = Modifier.height(20.dp))
        AuthPrimaryButton(
            text = stringResource(id = R.string.reset_password_now),
            onClick = {
                if (email.isEmpty()) {
                    errorMessage = pleaseEnterEmail
                    return@AuthPrimaryButton
                }
                if (code.isEmpty()) {
                    errorMessage = pleaseEnterCode
                    return@AuthPrimaryButton
                }
                if (newPassword.isEmpty()) {
                    errorMessage = pleaseEnterNewPassword
                    return@AuthPrimaryButton
                }
                if (newPassword.length < 6 || newPassword.length > 30) {
                    errorMessage = newPasswordLengthError
                    return@AuthPrimaryButton
                }
                if (newPassword != confirmPassword) {
                    errorMessage = passwordMismatch
                    return@AuthPrimaryButton
                }
                if (!email.matches(EMAIL_REGEX)) {
                    errorMessage = emailFormatError
                    return@AuthPrimaryButton
                }
                isLoading = true
                scope.launch {
                    try {
                        val response = userApi.resetPassword(email, code, newPassword)
                        isLoading = false
                        if (response.success) {
                            onResetSuccess()
                        } else {
                            errorMessage = response.message
                        }
                    } catch (e: Exception) {
                        isLoading = false
                        errorMessage = resetPasswordFailed.format(e.message ?: "")
                    }
                }
            },
            pageBackdrop = pageBackdrop,
            enabled = !isLoading,
            loading = isLoading,
        )
    }
}
