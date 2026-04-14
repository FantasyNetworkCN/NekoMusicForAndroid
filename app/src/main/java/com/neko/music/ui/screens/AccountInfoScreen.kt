package com.neko.music.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.neko.music.ui.theme.*
import com.neko.music.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountInfoScreen(
    onBackClick: () -> Unit = {},
    userId: Int = -1,
    username: String = "",
    email: String = "",
    onAvatarUpdate: (ByteArray) -> Unit = {},
    onPasswordUpdate: suspend (oldPassword: String, newPassword: String) -> Boolean = { _, _ -> false },
    onShowBottomControls: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    
    // 头像更新时间戳，用于绕过缓存
    var avatarUpdateTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    // 选中的图片 URI
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    // 显示裁剪界面
    var showCropDialog by remember { mutableStateOf(false) }
    
    // 监听裁剪对话框状态，控制底部控件显示
    LaunchedEffect(showCropDialog) {
        onShowBottomControls(!showCropDialog)
    }
    
    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            showCropDialog = true
        }
    }
    
    // 显示更换头像对话框
    var showAvatarDialog by remember { mutableStateOf(false) }
    
    // 显示修改密码对话框
    var showPasswordDialog by remember { mutableStateOf(false) }
    
    // 显示加载状态
    var isLoading by remember { mutableStateOf(false) }
    
    // 显示Toast消息
    var toastMessage by remember { mutableStateOf<String?>(null) }
    
    // 显示成功提示
    var showSuccess by remember { mutableStateOf(false) }

    // Preload string resources for non-Composable contexts
    val avatarUploadSuccess = stringResource(id = R.string.avatar_upload_success)
    val avatarUpdateSuccess = stringResource(id = R.string.avatar_update_success)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDarkTheme) Color(0xFF121228) else Color(0xFFFAFAFA))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 顶部导航栏
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.account_info),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else Color.Black
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(id = R.string.back),
                            tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.9f) else Color.Black
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = if (isDarkTheme) Color(0xFF1A1A2E).copy(alpha = 0.95f) else Color.White
                )
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 头像区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(RoseRed, SakuraPink)
                            )
                        )
                        .clickable { showAvatarDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("$baseUrl/api/user/avatar/$userId?t=$avatarUpdateTime")
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(id = R.string.user_avatar),
                        modifier = Modifier.fillMaxSize()
                    )

                }
            }
            
            Spacer(modifier = Modifier.height(30.dp))
            
            // 用户信息卡片
            InfoCard(
                icon = R.drawable.user,
                title = stringResource(id = R.string.username),
                value = username,
                showArrow = false,
                colorFilter = ColorFilter.tint(RoseRed)
            )

            Spacer(modifier = Modifier.height(16.dp))

            InfoCard(
                icon = R.drawable.email,
                title = stringResource(id = R.string.email),
                value = email,
                showArrow = false
            )

            Spacer(modifier = Modifier.height(16.dp))

            InfoCard(
                icon = R.drawable.password,
                title = stringResource(id = R.string.password),
                value = stringResource(id = R.string.modify_password),
                showArrow = true,
                onClick = { showPasswordDialog = true },
                colorFilter = ColorFilter.tint(RoseRed)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // 提示信息
            Text(
                text = stringResource(id = R.string.click_avatar_to_change),
                fontSize = 13.sp,
                color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.7f) else Color.Gray,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )
        }
        
        // 更换头像对话框
        if (showAvatarDialog) {
            AlertDialog(
                onDismissRequest = { showAvatarDialog = false },
                title = {
                    Text(
                        stringResource(id = R.string.change_avatar),
                        color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else Color.Black
                    )
                },
                text = {
                    Text(
                        stringResource(id = R.string.change_avatar_confirm),
                        color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showAvatarDialog = false
                            imagePickerLauncher.launch("image/*")
                        }
                    ) {
                        Text(stringResource(id = R.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAvatarDialog = false }) {
                        Text(stringResource(id = R.string.cancel))
                    }
                },
                containerColor = if (isDarkTheme) Color(0xFF1A1A2E).copy(alpha = 0.95f) else Color.White
            )
        }
        
        // 修改密码对话框
        if (showPasswordDialog) {
            ChangePasswordDialog(
                onDismiss = { showPasswordDialog = false },
                onConfirm = onPasswordUpdate
            )
        }
        
        // 加载中提示
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = RoseRed)
            }
        }
        
        // 成功提示
        AnimatedVisibility(
            visible = showSuccess,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
            
            Box(
                modifier = Modifier
                    .background(
                        color = if (isDarkTheme) Color(0xFF1A1A2E).copy(alpha = 0.95f) else Color.White,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(20.dp)
                    .shadow(
                        elevation = 8.dp,
                        spotColor = RoseRed.copy(alpha = 0.3f)
                    )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(id = R.string.success),
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = toastMessage ?: stringResource(id = R.string.operation_success),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else Color.Black
                    )
                }
            }
            
            LaunchedEffect(showSuccess) {
                kotlinx.coroutines.delay(2000)
                showSuccess = false
            }
        }
    }
    
    // 裁剪对话框（在最顶层显示，覆盖整个屏幕）
    if (showCropDialog && selectedImageUri != null) {
        AvatarCropDialog(
            imageUri = selectedImageUri!!,
            onDismiss = {
                showCropDialog = false
                selectedImageUri = null
            },
            onConfirm = { imageData ->
                onAvatarUpdate(imageData)
                // 重置时间戳以刷新头像
                avatarUpdateTime = System.currentTimeMillis()
                showCropDialog = false
                selectedImageUri = null
                // 显示系统 Toast
                android.widget.Toast.makeText(
                    context,
                    avatarUploadSuccess,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                // 显示成功提示
                toastMessage = avatarUpdateSuccess
                showSuccess = true
            }        )
    }
}

@Composable
fun InfoCard(
    icon: Int,
    title: String,
    value: String,
    showArrow: Boolean = false,
    onClick: () -> Unit = {},
    colorFilter: ColorFilter? = null
) {
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(
                color = if (isDarkTheme) Color(0xFF252545).copy(alpha = 0.6f) else Color.White,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(20.dp)
            .shadow(
                elevation = 2.dp,
                spotColor = RoseRed.copy(alpha = 0.15f),
                ambientColor = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.08f) else Color.Gray.copy(alpha = 0.08f)
            )
            .scale(scale)
            .clickable(enabled = showArrow) {
                isPressed = true
                onClick()
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            colorFilter = colorFilter
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else Color.Black,
                fontWeight = FontWeight.Bold
            )
        }
        
        if (showArrow) {
            Text(
                text = "›",
                fontSize = 20.sp,
                color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.6f) else Color.Gray.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: suspend (oldPassword: String, newPassword: String) -> Boolean
) {
    val context = LocalContext.current
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showOldPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isUpdating by remember { mutableStateOf(false) }
    
    suspend fun validateAndConfirm() {
        when {
            oldPassword.isEmpty() -> errorMessage = context.getString(R.string.please_enter_old_password)
            newPassword.isEmpty() -> errorMessage = context.getString(R.string.please_enter_new_password)
            confirmPassword.isEmpty() -> errorMessage = context.getString(R.string.please_confirm_new_password)
            newPassword != confirmPassword -> errorMessage = context.getString(R.string.password_mismatch)
            newPassword.length < 6 -> errorMessage = context.getString(R.string.new_password_length_error)
            else -> {
                isUpdating = true
                val success = onConfirm(oldPassword, newPassword)
                isUpdating = false
                if (!success) {
                    // 失败时不关闭对话框，错误消息由 onConfirm 处理
                } else {
                    onDismiss()
                }
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(id = R.string.modify_password),
                color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else Color.Black
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // 原密码
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = {
                        oldPassword = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(id = R.string.old_password)) },
                    singleLine = true,
                    visualTransformation = if (showOldPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showOldPassword = !showOldPassword }) {
                            Icon(
                                imageVector = if (showOldPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showOldPassword) stringResource(id = R.string.hide_password) else stringResource(id = R.string.show_password),
                                tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 新密码
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(id = R.string.new_password)) },
                    singleLine = true,
                    visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showNewPassword = !showNewPassword }) {
                            Icon(
                                imageVector = if (showNewPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showNewPassword) stringResource(id = R.string.hide_password) else stringResource(id = R.string.show_password),
                                tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 确认新密码
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(id = R.string.confirm_new_password)) },
                    singleLine = true,
                    visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                            Icon(
                                imageVector = if (showConfirmPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showConfirmPassword) stringResource(id = R.string.hide_password) else stringResource(id = R.string.show_password),
                                tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isError = confirmPassword.isNotEmpty() && newPassword != confirmPassword
                )
                
                // 错误提示
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            var scope by remember { mutableStateOf<kotlinx.coroutines.CoroutineScope?>(null) }
            scope = rememberCoroutineScope()
            
            TextButton(
                onClick = {
                    scope?.launch { validateAndConfirm() }
                },
                enabled = !isUpdating
            ) {
                Text(if (isUpdating) stringResource(id = R.string.modifying) else stringResource(id = R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        },
        containerColor = if (isDarkTheme) Color(0xFF1A1A2E).copy(alpha = 0.95f) else Color.White
    )
}

/**
 * 头像裁剪对话框 - 支持1:1比例裁剪
 */
@Composable
fun AvatarCropDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (ByteArray) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    
    // 图片缩放比例
    var scale by remember { mutableFloatStateOf(1f) }
    // 图片偏移量
    var offset by remember { mutableStateOf(Offset.Zero) }
    // 容器尺寸
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    // 原始图片尺寸
    var originalBitmapSize by remember { mutableStateOf(IntSize.Zero) }
    
    // 计算裁剪区域
    val cropSize = minOf(containerSize.width, containerSize.height) * 0.8f
    
// 加载图片
    val imageBitmap: ImageBitmap? = produceState<ImageBitmap?>(initialValue = null, imageUri) {
        val loader = coil.ImageLoader.Builder(context)
            .diskCache(null)
            .memoryCache(null)
            .build()
        val request = ImageRequest.Builder(context)
            .data(imageUri)
            .allowHardware(false) // 需要软件渲染以支持裁剪
            .size(coil.size.Size.ORIGINAL) // 加载原始尺寸
            .build()
        
        try {
            val result = loader.execute(request)
            // 从 drawable 获取图片
            val drawable = result.drawable
            if (drawable != null) {
                val sourceBitmap = if (drawable is android.graphics.drawable.BitmapDrawable) {
                    // 如果是 BitmapDrawable，直接获取 bitmap
                    var bmp = drawable.bitmap
                    // 确保是软件位图
                    if (bmp.config == android.graphics.Bitmap.Config.HARDWARE) {
                        bmp = bmp.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                    }
                    bmp
                } else {
                    // 如果是其他类型的 drawable，绘制到 canvas
                    val bmp = android.graphics.Bitmap.createBitmap(
                        drawable.intrinsicWidth,
                        drawable.intrinsicHeight,
                        android.graphics.Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(bmp)
                    drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
                    drawable.draw(canvas)
                    bmp
                }
                value = sourceBitmap.asImageBitmap()
                originalBitmapSize = IntSize(sourceBitmap.width, sourceBitmap.height)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            value = null
        }
    }.value
    
    // 图片加载完成后，计算初始缩放比例让图片适应容器
    LaunchedEffect(originalBitmapSize, containerSize) {
        if (originalBitmapSize.width > 0 && originalBitmapSize.height > 0 && containerSize.width > 0) {
            val bitmapWidth = originalBitmapSize.width.toFloat()
            val bitmapHeight = originalBitmapSize.height.toFloat()
            val containerWidth = containerSize.width.toFloat()
            val containerHeight = containerSize.height.toFloat()
            
            // 计算让图片完全显示在容器中的缩放比例
            val scaleWidth = containerWidth / bitmapWidth
            val scaleHeight = containerHeight / bitmapHeight
            val initialScale = minOf(scaleWidth, scaleHeight)
            
            scale = initialScale
            offset = Offset.Zero
        }
    }
    
    // 回弹效果动画
    val animatedOffset by androidx.compose.animation.core.animateOffsetAsState(
        targetValue = offset,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "offset_animation"
    )
    
// 计算限制后的偏移量
    val limitedOffsetState = remember(scale, containerSize, imageBitmap) {
        mutableStateOf(
            if (imageBitmap != null && containerSize.width > 0) {
                val displayedWidth = imageBitmap.width * scale
                val displayedHeight = imageBitmap.height * scale
                
                val maxOffsetX = displayedWidth / 2f
                val maxOffsetY = displayedHeight / 2f
                
                Offset(
                    x = animatedOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                    y = animatedOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                )
            } else {
                animatedOffset
            }
        )
    }
    
    // 更新限制后的偏移量
    LaunchedEffect(animatedOffset, scale, containerSize, imageBitmap) {
        if (imageBitmap != null && containerSize.width > 0) {
            val displayedWidth = imageBitmap.width * scale
            val displayedHeight = imageBitmap.height * scale
            
            val maxOffsetX = displayedWidth / 2f
            val maxOffsetY = displayedHeight / 2f
            
            limitedOffsetState.value = Offset(
                x = animatedOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                y = animatedOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
            )
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(Float.MAX_VALUE)
            .background(Color.Black)
    ) {
        // 顶部栏（最上层）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(1f)
                .background(Color.Black.copy(alpha = 0.7f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(id = R.string.cancel), color = Color.White)
                }

                Text(
                    text = stringResource(id = R.string.crop_avatar),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                TextButton(
                    onClick = {
                        // 裁剪图片并上传
                        imageBitmap?.let { bitmap ->
                            val androidBitmap = bitmap.asAndroidBitmap()
                            val bitmapWidth = bitmap.width.toFloat()
                            val bitmapHeight = bitmap.height.toFloat()
                            
                            // 裁剪框在屏幕中央
                            val cropBoxCenterX = containerSize.width / 2f
                            val cropBoxCenterY = containerSize.height / 2f
                            val cropBoxLeft = cropBoxCenterX - cropSize / 2f
                            val cropBoxTop = cropBoxCenterY - cropSize / 2f
                            
                            // 计算图片显示的左上角位置（考虑偏移）
                            val displayedImageWidth = bitmapWidth * scale
                            val displayedImageHeight = bitmapHeight * scale
                            val containerWidthF = containerSize.width.toFloat()
                            val containerHeightF = containerSize.height.toFloat()
                            
                            // 使用实际的显示偏移量（animatedOffset）
                            val imageLeft = (containerWidthF - displayedImageWidth) / 2f + animatedOffset.x
                            val imageTop = (containerHeightF - displayedImageHeight) / 2f + animatedOffset.y
                            
                            // 计算裁剪框在原图中的位置
                            val cropXInImage = ((cropBoxLeft - imageLeft) / scale).toInt()
                            val cropYInImage = ((cropBoxTop - imageTop) / scale).toInt()
                            val cropWidthInImage = (cropSize / scale).toInt()
                            val cropHeightInImage = (cropSize / scale).toInt()
                            
                            // 确保裁剪区域在图片范围内
                            val safeCropX = cropXInImage.coerceIn(0, androidBitmap.width - 1)
                            val safeCropY = cropYInImage.coerceIn(0, androidBitmap.height - 1)
                            val safeCropWidth = cropWidthInImage.coerceAtMost(androidBitmap.width - safeCropX)
                            val safeCropHeight = cropHeightInImage.coerceAtMost(androidBitmap.height - safeCropY)
                            
                            // 裁剪图片
                            val croppedBitmap = if (safeCropWidth > 0 && safeCropHeight > 0) {
                                android.graphics.Bitmap.createBitmap(
                                    androidBitmap,
                                    safeCropX,
                                    safeCropY,
                                    safeCropWidth,
                                    safeCropHeight
                                )
                            } else {
                                // 回退：使用图片中心
                                val size = minOf(androidBitmap.width, androidBitmap.height)
                                val x = (androidBitmap.width - size) / 2
                                val y = (androidBitmap.height - size) / 2
                                android.graphics.Bitmap.createBitmap(
                                    androidBitmap,
                                    x,
                                    y,
                                    size,
                                    size
                                )
                            }
                            
                            // 缩放到512x512
                            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(
                                croppedBitmap,
                                512,
                                512,
                                true
                            )
                            
                            val stream = java.io.ByteArrayOutputStream()
                            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
                            onConfirm(stream.toByteArray())
                        }
                    }
                ) {
                    Text(stringResource(id = R.string.complete), color = RoseRed)
                }
            }
        }
        
        // 裁剪预览区域
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    containerSize = coordinates.size
                }
        ) {
            if (imageBitmap != null) {
                // 图片容器
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(0.5f, 10f)
                                offset = (offset + pan)
                                scale = newScale
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // 图片
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUri)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .offset {
                                val currentOffset = limitedOffsetState.value
                                androidx.compose.ui.unit.IntOffset(
                                    x = currentOffset.x.toInt(),
                                    y = currentOffset.y.toInt()
                                )
                            }
                            .scale(scale),
                        contentScale = ContentScale.Fit
                    )
                }
                
                // 1:1 裁剪框（固定在屏幕中央）
                Box(
                    modifier = Modifier
                        .size(with(density) { cropSize.toDp() })
                        .border(
                            width = 2.dp,
                            color = RoseRed,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .align(Alignment.Center)
                )
            } else {
                CircularProgressIndicator(
                    color = RoseRed,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        
        // 底部提示文本
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(id = R.string.drag_scale_avatar),
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
