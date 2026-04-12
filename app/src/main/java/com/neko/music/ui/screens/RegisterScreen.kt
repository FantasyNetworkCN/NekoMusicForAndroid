package com.neko.music.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neko.music.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onBackClick: () -> Unit,
    onLoginClick: () -> Unit
) {

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSendingCode by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf("") }

    // 动画状态
    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.95f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 150, delayMillis = 30),
        label = "alpha"
    )

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val userApi = com.neko.music.data.api.UserApi(context = context)

    // Preload string resources for non-Composable contexts
    val pleaseFillAllFields = stringResource(id = R.string.please_fill_all_fields)
    val usernameLengthError = stringResource(id = R.string.username_length_error)
    val passwordLengthError = stringResource(id = R.string.password_length_error)
    val passwordMismatch = stringResource(id = R.string.password_mismatch)
    val emailFormatError = stringResource(id = R.string.email_format_error)
    val registerFailed = stringResource(id = R.string.register_failed)
    val pleaseEnterEmailFirst = stringResource(id = R.string.please_enter_email_first)
    val sendVerificationCodeFailed = stringResource(id = R.string.send_verification_code_failed)

    // 倒计时
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets(0.dp))
    ) {
        // 处理返回键
        BackHandler {
            onBackClick()
        }
        // 返回按钮
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(durationMillis = 150, delayMillis = 60)
                                    ) + fadeIn(animationSpec = tween(durationMillis = 150, delayMillis = 60))        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(id = R.string.back),
                    tint = Color.White
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(top = 80.dp)
                .verticalScroll(rememberScrollState())
                .scale(scale)
                .alpha(alpha),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {

            Spacer(modifier = Modifier.height(24.dp))

            // 标题
            Text(
                text = stringResource(id = R.string.app_title),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = stringResource(id = R.string.create_new_account),
                fontSize = 16.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            // 用户名输入框
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 150, delayMillis = 90)
                                        ) + fadeIn(animationSpec = tween(durationMillis = 150, delayMillis = 90))            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMessage = ""
                    },
                    label = { Text(stringResource(id = R.string.username)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = stringResource(id = R.string.username),
                            tint = Color.Gray
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE94560),
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        unfocusedContainerColor = Color(0xFF1A1A2E)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 邮箱输入框
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 150, delayMillis = 105)
                                        ) + fadeIn(animationSpec = tween(durationMillis = 150, delayMillis = 105))            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        errorMessage = ""
                    },
                    label = { Text(stringResource(id = R.string.email)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = stringResource(id = R.string.email),
                            tint = Color.Gray
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE94560),
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        unfocusedContainerColor = Color(0xFF1A1A2E)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 验证码输入框
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 150, delayMillis = 120)
                                        ) + fadeIn(animationSpec = tween(durationMillis = 150, delayMillis = 120))            ) {
                OutlinedTextField(
                    value = verificationCode,
                    onValueChange = {
                        verificationCode = it
                        errorMessage = ""
                    },
                    label = { Text(stringResource(id = R.string.verification_code)) },
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                if (email.isEmpty()) {
                                    errorMessage = pleaseEnterEmailFirst
                                    return@TextButton
                                }
                                if (countdown > 0) return@TextButton

                                isSendingCode = true
                                scope.launch {
                                    try {
                                        val response = userApi.sendVerificationCode(email, username)
                                        isSendingCode = false

                                        if (response.success) {
                                            countdown = 60
                                        } else {
                                            errorMessage = response.message
                                        }
                                    } catch (e: Exception) {
                                        isSendingCode = false
                                        errorMessage = sendVerificationCodeFailed.format(e.message ?: "")
                                    }
                                }
                            },
                            enabled = !isSendingCode && countdown == 0
                        ) {
                            if (isSendingCode) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFFE94560)
                                )
                            } else {
                                Text(
                                    text = if (countdown > 0) stringResource(id = R.string.retry_after_seconds, countdown) else stringResource(id = R.string.send_verification_code),
                                    color = if (countdown > 0) Color.Gray else Color(0xFFE94560),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE94560),
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        unfocusedContainerColor = Color(0xFF1A1A2E)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 密码输入框
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 150, delayMillis = 135)
                                        ) + fadeIn(animationSpec = tween(durationMillis = 150, delayMillis = 135))            ) {
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = ""
                    },
                    label = { Text(stringResource(id = R.string.password)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(id = R.string.password),
                            tint = Color.Gray
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE94560),
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        unfocusedContainerColor = Color(0xFF1A1A2E)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 确认密码输入框
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 150, delayMillis = 150)
                                        ) + fadeIn(animationSpec = tween(durationMillis = 150, delayMillis = 150))            ) {
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = ""
                    },
                    label = { Text(stringResource(id = R.string.confirm_password)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(id = R.string.confirm_password),
                            tint = Color.Gray
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE94560),
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        unfocusedContainerColor = Color(0xFF1A1A2E)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
            }

            // 错误提示
            AnimatedVisibility(
                visible = errorMessage.isNotEmpty(),
                enter = expandVertically(animationSpec = tween(durationMillis = 150)) + fadeIn(),
                                    exit = shrinkVertically(animationSpec = tween(durationMillis = 150)) + fadeOut()            ) {
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 注册按钮
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 150, delayMillis = 165)
                                        ) + fadeIn(animationSpec = tween(durationMillis = 150, delayMillis = 165))            ) {
                Button(
                    onClick = {
                        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || verificationCode.isEmpty()) {
                            errorMessage = pleaseFillAllFields
                            return@Button
                        }

                        if (username.length < 3 || username.length > 20) {
                            errorMessage = usernameLengthError
                            return@Button
                        }

                        if (password.length < 6 || password.length > 30) {
                            errorMessage = passwordLengthError
                            return@Button
                        }

                        if (password != confirmPassword) {
                            errorMessage = passwordMismatch
                            return@Button
                        }

                        // 简单的邮箱格式验证
                        if (!email.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) {
                            errorMessage = emailFormatError
                            return@Button
                        }

                        isLoading = true
                        scope.launch {
                            try {
                                val response = userApi.register(username, password, email, verificationCode)
                                isLoading = false

                                if (response.success) {
                                    // 注册成功，跳转到登录页面
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE94560)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = stringResource(id = R.string.register),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 登录提示
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(animationSpec = tween(durationMillis = 150, delayMillis = 180))
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.already_have_account),
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = onLoginClick) {
                        Text(
                            text = stringResource(id = R.string.login_now),
                            color = Color(0xFFE94560),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}