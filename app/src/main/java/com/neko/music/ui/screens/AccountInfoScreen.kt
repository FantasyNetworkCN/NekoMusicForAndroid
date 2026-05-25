package com.neko.music.ui.screens

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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.crossfade
import coil3.size.Size
import coil3.asDrawable
import androidx.core.graphics.drawable.toBitmap
import com.neko.music.R
import com.neko.music.ui.components.ChangeAvatarGlassDialog
import com.neko.music.ui.components.ChangePasswordGlassDialog
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.theme.*
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountInfoScreen(
    onBackClick: () -> Unit = {},
    userId: Int = -1,
    username: String = "",
    email: String = "",
    isVip: Boolean = false,
    vipExpiresAt: String? = null,
    onVipCenterClick: () -> Unit = {},
    onAvatarUpdate: (ByteArray) -> Unit = {},
    onPasswordUpdate: suspend (oldPassword: String, newPassword: String) -> Boolean = { _, _ -> false },
    onShowBottomControls: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDarkTheme = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)
    val glassTint = LiquidGlassDefaults.screenListCard
    val glassBg = glassTint.background(isDarkTheme)
    val glassBorder = glassTint.border(isDarkTheme)
    val glassHighlight = glassTint.highlight(isDarkTheme)
    val dividerColor =
        if (isDarkTheme) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f)
    
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
        }

        CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
        Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = if (isDarkTheme) Color(0xFFF0F0F5) else scheme.onSurface,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(id = R.string.account_info),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = stringResource(id = R.string.back),
                                tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.9f) else scheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        backgroundAlpha = glassBg,
                        borderAlpha = glassBorder,
                        highlightAlpha = glassHighlight
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
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
                                        .data("$baseUrl/api/user/avatar/$userId")
                                        .memoryCacheKey("avatar_${userId}_$avatarUpdateTime")
                                        .diskCacheKey("avatar_${userId}_$avatarUpdateTime")
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = stringResource(id = R.string.user_avatar),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(id = R.string.click_avatar_to_change),
                                fontSize = 13.sp,
                                color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.75f) else scheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    GlassSurface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        backgroundAlpha = glassBg,
                        borderAlpha = glassBorder,
                        highlightAlpha = glassHighlight
                    ) {
                        Column {
                            InfoCard(
                                icon = R.drawable.user,
                                title = stringResource(id = R.string.username),
                                value = username,
                                showArrow = false,
                                colorFilter = ColorFilter.tint(RoseRed),
                                isDarkTheme = isDarkTheme
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                color = dividerColor
                            )
                            InfoCard(
                                icon = R.drawable.email,
                                title = stringResource(id = R.string.email),
                                value = email,
                                showArrow = false,
                                isDarkTheme = isDarkTheme
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                color = dividerColor
                            )
                            InfoCard(
                                icon = R.drawable.ic_vip_star,
                                title = stringResource(id = R.string.vip_center_title),
                                value = if (isVip) {
                                    val exp = vipExpiresAt?.take(10)
                                    if (exp != null) {
                                        stringResource(R.string.vip_status_expires, exp)
                                    } else {
                                        stringResource(R.string.vip_status_active)
                                    }
                                } else {
                                    stringResource(R.string.vip_open_membership)
                                },
                                showArrow = true,
                                onClick = onVipCenterClick,
                                colorFilter = ColorFilter.tint(Color(0xFFFFB300)),
                                isDarkTheme = isDarkTheme
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                color = dividerColor
                            )
                            InfoCard(
                                icon = R.drawable.password,
                                title = stringResource(id = R.string.password),
                                value = stringResource(id = R.string.modify_password),
                                showArrow = true,
                                onClick = { showPasswordDialog = true },
                                colorFilter = ColorFilter.tint(RoseRed),
                                isDarkTheme = isDarkTheme
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(160.dp))
                }
        }

        AnimatedVisibility(
            visible = showAvatarDialog,
            enter = LiquidCenterModalTransitions.Enter,
            exit = LiquidCenterModalTransitions.Exit,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(45f),
        ) {
            ChangeAvatarGlassDialog(
                sampleBackdrop = pageBackdrop,
                onDismiss = { showAvatarDialog = false },
                onConfirm = {
                    showAvatarDialog = false
                    imagePickerLauncher.launch("image/*")
                }
            )
        }

        AnimatedVisibility(
            visible = showPasswordDialog,
            enter = LiquidCenterModalTransitions.Enter,
            exit = LiquidCenterModalTransitions.Exit,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(45f),
        ) {
            ChangePasswordGlassDialog(
                sampleBackdrop = pageBackdrop,
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
    colorFilter: ColorFilter? = null,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
) {
    var isPressed by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (showArrow) {
                    Modifier.clickable {
                        isPressed = true
                        onClick()
                    }
                } else {
                    Modifier
                }
            ),
        color = if (isPressed) {
            if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color(0xFFF5F5F5)
        } else {
            Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(RoseRed.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    colorFilter = colorFilter
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    fontSize = 16.sp,
                    color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else Color.Black,
                    fontWeight = FontWeight.Medium
                )
            }

            if (showArrow) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(id = R.string.more),
                    tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(100)
            isPressed = false
        }
    }
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
        val loader = ImageLoader.Builder(context)
            .diskCache(null)
            .memoryCache(null)
            .build()
        val request = ImageRequest.Builder(context)
            .data(imageUri)
            .size(Size(2000, 2000)) // 设置一个足够大的尺寸
            .build()
        
        try {
            val imageResult = loader.execute(request)
            // 从 ImageResult 获取图片
            if (imageResult is SuccessResult) {
                val sourceBitmap = imageResult.image.asDrawable(context.resources).toBitmap()
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
