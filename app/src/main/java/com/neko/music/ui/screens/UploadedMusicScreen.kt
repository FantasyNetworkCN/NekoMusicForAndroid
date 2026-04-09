package com.neko.music.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.neko.music.R
import com.neko.music.data.api.UploadedMusic
import com.neko.music.util.LrcParser
import com.neko.music.ui.theme.*
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val baseUrl = "https://music.cnmsb.xin"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadedMusicScreen(
    onBackClick: () -> Unit = {},
    onMusicClick: (UploadedMusic) -> Unit = {},
    token: String? = null,
    userId: Int = -1
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var musicList by remember { mutableStateOf<List<UploadedMusic>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showUploadDialog by remember { mutableStateOf(false) }

    // 预加载字符串资源
    val pleaseLoginFirst = stringResource(id = R.string.please_login_first)

    LaunchedEffect(token) {
        if (token != null) {
            isLoading = true
            // 调用API获取上传音乐列表
            scope.launch {
                try {
                    val userApi = com.neko.music.data.api.UserApi(token)
                    val response = userApi.getUploadedMusic()
                    if (response.success) {
                        musicList = response.musicList
                    } else {
                        showError = true
                        errorMessage = response.message
                    }
                } catch (e: Exception) {
                    showError = true
                    errorMessage = context.getString(R.string.get_data_failed, e.message ?: "Unknown error")
                } finally {
                    isLoading = false
                }
            }
        } else {
            showError = true
            errorMessage = pleaseLoginFirst
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.my_uploads),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(id = R.string.back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (!isLoading && !showError) {
                        IconButton(
                            onClick = { showUploadDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = stringResource(id = R.string.upload_music),
                                tint = RoseRed
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                isLoading -> {
                    LoadingView()
                }
                showError -> {
                    ErrorView(
                        message = errorMessage,
                        onRetry = {
                            showError = false
                            isLoading = true
                        }
                    )
                }
                musicList.isEmpty() -> {
                    EmptyView()
                }
                else -> {
                    MusicList(
                        musicList = musicList,
                        onMusicClick = onMusicClick
                    )
                }
            }
        }
    }

    // 上传对话框
    if (showUploadDialog) {
        UploadMusicDialog(
            onDismiss = { showUploadDialog = false },
            onUploadSuccess = {
                showUploadDialog = false
                // 重新加载音乐列表
                isLoading = true
                scope.launch {
                    try {
                        val userApi = com.neko.music.data.api.UserApi(token)
                        val response = userApi.getUploadedMusic()
                        if (response.success) {
                            musicList = response.musicList
                        } else {
                            showError = true
                            errorMessage = response.message
                        }
                    } catch (e: Exception) {
                        showError = true
                        errorMessage = context.getString(R.string.get_data_failed, e.message ?: "Unknown error")
                    } finally {
                        isLoading = false
                    }
                }
            },
            token = token,
            userId = userId
        )
    }
}

@Composable
fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = RoseRed,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.loading),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = stringResource(id = R.string.error),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoseRed
                )
            ) {
                Text(stringResource(id = R.string.retry))
            }
        }
    }
}

@Composable
fun EmptyView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.UploadFile,
                contentDescription = stringResource(id = R.string.empty),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.no_uploaded_music),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.uploaded_music_pending),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MusicList(
    musicList: List<UploadedMusic>,
    onMusicClick: (UploadedMusic) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 140.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(musicList) { music ->
            MusicItem(
                music = music,
                onClick = { onMusicClick(music) }
            )
        }
    }
}

@Composable
fun MusicItem(
    music: UploadedMusic,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable {
                isPressed = true
                onClick()
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                RoseRed.copy(alpha = 0.1f),
                                SakuraPink.copy(alpha = 0.1f)
                            )
                        )
                    )
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("${baseUrl}/api/music/cover/${music.id}")
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(id = R.string.album_cover),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 音乐信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = music.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = music.artist,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (music.album.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = music.album,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 时长
            Text(
                text = formatDuration(music.duration),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", minutes, secs)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadMusicDialog(
    onDismiss: () -> Unit,
    onUploadSuccess: () -> Unit,
    token: String?,
    userId: Int = -1
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 预加载字符串资源（用于在非 Composable 上下文中使用）
    val pleaseSelectLanguage = stringResource(id = R.string.please_select_language)
    val pleaseFillRequiredFields = stringResource(id = R.string.please_fill_required_fields)
    val fileReadFailed = stringResource(id = R.string.file_read_failed)
    val uploadFailed = stringResource(id = R.string.upload_failed)
    val unsupportedLyricsFormat = stringResource(id = R.string.unsupported_lyrics_format)
    val invalidLyricsTimestamp = stringResource(id = R.string.invalid_lyrics_timestamp)
    val lyricsFileReadFailed = stringResource(id = R.string.lyrics_file_read_failed)
    
    // 语言选项
    val languageOptions = listOf(
        stringResource(id = R.string.language_chinese) to "zh",
        stringResource(id = R.string.language_cantonese) to "yue",
        stringResource(id = R.string.language_shanghainese) to "wuu",
        stringResource(id = R.string.language_english) to "en",
        stringResource(id = R.string.language_japanese) to "ja",
        stringResource(id = R.string.language_korean) to "ko",
        stringResource(id = R.string.language_french) to "fr",
        stringResource(id = R.string.language_german) to "de",
        stringResource(id = R.string.language_russian) to "ru",
        stringResource(id = R.string.language_instrumental) to "instrumental"
    )
    
    // 音乐信息
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(pleaseSelectLanguage) }
    var tags by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var durationSeconds by remember { mutableStateOf(0) }  // 添加时长秒数
    
    // 文件
    var audioFile by remember { mutableStateOf<android.net.Uri?>(null) }
    var audioFileName by remember { mutableStateOf("") }
    var lyricsFile by remember { mutableStateOf<android.net.Uri?>(null) }
    var lyricsPreview by remember { mutableStateOf("") }
    var coverImage by remember { mutableStateOf<android.net.Uri?>(null) }
    var coverBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0f) }
    var isParsing by remember { mutableStateOf(false) }
    
    var languageExpanded by remember { mutableStateOf(false) }
    var showFullLyricsDialog by remember { mutableStateOf(false) }
    var fullLyricsContent by remember { mutableStateOf("") }
    
    // 文件选择器
    val audioFileLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val fileName = getFileName(context, it)
            val fileExtension = fileName?.substringAfterLast('.', "")?.lowercase() ?: ""
            
            // 检查文件格式是否支持
            val supportedFormats = listOf("mp3", "wav", "flac")
            if (fileExtension !in supportedFormats) {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.unsupported_audio_format),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@let
            }
            
            audioFileName = fileName ?: "audio.mp3"
            audioFile = uri
            
            // 自动解析音乐元数据
            isParsing = true
            scope.launch {
                try {
                    val metadata = parseAudioMetadata(context, uri)
                    title = metadata.title
                    artist = metadata.artist
                    album = metadata.album
                    duration = metadata.duration
                    durationSeconds = metadata.durationSeconds  // 设置时长秒数
                    // 如果音频文件有内嵌封面，则自动显示
                    if (metadata.cover != null) {
                        coverBitmap = metadata.cover
                    }
                } catch (e: Exception) {
                    // 解析失败，保持原样
                } finally {
                    isParsing = false
                }
            }
        }
    }
    
    val lyricsFileLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val fileName = getFileName(context, it)
            
            // 使用JNI检查文件扩展名是否为lrc
            if (fileName != null && !LrcParser.isLrcFile(fileName)) {
                android.widget.Toast.makeText(
                    context,
                    unsupportedLyricsFormat,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@let
            }
            
            // 使用JNI验证 LRC 文件内容是否包含时间戳
            scope.launch {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader(StandardCharsets.UTF_8).use { it?.readText() }
                    if (content == null || !LrcParser.isValidLrcContent(content)) {
                        android.widget.Toast.makeText(
                            context,
                            invalidLyricsTimestamp,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }
                    
                    // 使用JNI解析歌词预览
                    val preview = LrcParser.parseLrcPreview(content)
                    
                    // 验证通过，设置歌词文件和预览内容
                    lyricsFile = uri
                    lyricsPreview = preview
                    fullLyricsContent = content  // 保存完整歌词内容
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        context,
                        lyricsFileReadFailed,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    val coverImageLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        coverImage = uri
        // 用户手动选择封面时，清除自动提取的封面
        coverBitmap = null
    }
    
    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUploading) stringResource(id = R.string.uploading_progress, uploadProgress.toInt())
                            else stringResource(id = R.string.upload_music_dialog_title),
                        fontWeight = FontWeight.Bold
                    )
                    if (isParsing) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                if (isUploading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { uploadProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = RoseRed
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 音频文件选择
                OutlinedButton(
                    onClick = { audioFileLauncher.launch("audio/*") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUploading
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (audioFile != null) {
                            if (isParsing) stringResource(id = R.string.parsing_metadata)
                            else "${stringResource(id = R.string.audio_file_selected, audioFileName)}"
                        } else "${stringResource(id = R.string.select_audio_file)} *",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // 时长显示（自动解析后显示）
                if (duration.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = RoseRed.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${stringResource(id = R.string.music_duration)}: $duration",
                            modifier = Modifier.padding(12.dp),
                            fontSize = 14.sp,
                            color = RoseRed,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // 歌词文件选择
                OutlinedButton(
                    onClick = { lyricsFileLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUploading
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (lyricsFile != null) stringResource(id = R.string.lyrics_file_selected) 
                            else "${stringResource(id = R.string.select_lyrics_file)} ${stringResource(id = R.string.optional_field)}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // 歌词预览
                if (lyricsFile != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clickable { showFullLyricsDialog = true },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.preview),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = lyricsPreview.ifEmpty { stringResource(id = R.string.loading_data) },
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                
                // 封面图片选择
                OutlinedButton(
                    onClick = { coverImageLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isUploading
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            coverImage != null -> stringResource(id = R.string.cover_image_selected)
                            coverBitmap != null -> "${stringResource(id = R.string.cover_image_auto_extracted)}"
                            else -> "${stringResource(id = R.string.select_cover_image)} ${stringResource(id = R.string.optional_field)}"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // 封面预览
                if (coverImage != null || coverBitmap != null) {
                    Card(
                        modifier = Modifier
                            .size(120.dp)
                            .align(Alignment.CenterHorizontally),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (coverImage != null) {
                            // 显示用户手动选择的封面
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(coverImage)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = stringResource(id = R.string.preview),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else if (coverBitmap != null) {
                            // 显示从音频文件自动提取的封面
                            Image(
                                bitmap = coverBitmap!!.asImageBitmap(),
                                contentDescription = stringResource(id = R.string.preview),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
                
                Divider()
                
                // 音乐信息输入
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("${stringResource(id = R.string.music_title)} *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isParsing && !isUploading
                )
                
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("${stringResource(id = R.string.music_artist)} *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isParsing && !isUploading
                )
                
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text(stringResource(id = R.string.music_album)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isParsing && !isUploading
                )
                
                // 语言下拉选择
                ExposedDropdownMenuBox(
                    expanded = languageExpanded,
                    onExpandedChange = { if (!isUploading) languageExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = language,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(id = R.string.music_language)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        singleLine = true,
                        enabled = !isUploading
                    )
                    
                    ExposedDropdownMenu(
                        expanded = languageExpanded,
                        onDismissRequest = { languageExpanded = false }
                    ) {
                        languageOptions.forEach { (displayName, value) ->
                            DropdownMenuItem(
                                text = { Text(displayName) },
                                onClick = {
                                    language = displayName
                                    languageExpanded = false
                                }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(stringResource(id = R.string.music_tags)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isUploading
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // 验证必填字段
                    if (title.isBlank() || artist.isBlank() || audioFile == null) {
                        android.widget.Toast.makeText(
                            context,
                            pleaseFillRequiredFields,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }
                    
                    // 验证语言选择
                    if (language == pleaseSelectLanguage) {
                        android.widget.Toast.makeText(
                            context,
                            pleaseSelectLanguage,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }
                    
                    isUploading = true
                    uploadProgress = 0f
                    
                    scope.launch {
                        try {
                            // 模拟进度更新 - 读取文件阶段
                            launch {
                                while (isUploading && uploadProgress < 30f) {
                                    kotlinx.coroutines.delay(50)
                                    uploadProgress = (uploadProgress + 2f).coerceAtMost(30f)
                                }
                            }
                            
                            // 读取文件
                            val audioBytes = context.contentResolver.openInputStream(audioFile!!)?.use { it.readBytes() }
                            val lyricsBytes = lyricsFile?.let { context.contentResolver.openInputStream(it)?.use { it.readBytes() } }
                            val coverBytes = if (coverBitmap != null) {
                                // 将Bitmap转换为ByteArray
                                val stream = java.io.ByteArrayOutputStream()
                                coverBitmap!!.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
                                stream.toByteArray()
                            } else {
                                // 使用用户手动选择的封面
                                coverImage?.let { context.contentResolver.openInputStream(it)?.use { it.readBytes() } }
                            }
                            
                            // 模拟进度更新 - 准备上传阶段
                            uploadProgress = 30f
                            
                            // 将语言名称转换为语言代码
                            val languageCode = languageOptions.find { it.first == language }?.second ?: ""
                            
                            // 模拟进度更新 - 上传中阶段
                            val uploadJob = launch {
                                while (isUploading && uploadProgress < 95f) {
                                    kotlinx.coroutines.delay(100)
                                    uploadProgress = (uploadProgress + 5f).coerceAtMost(95f)
                                }
                            }
                            
                            if (audioBytes != null) {
                                val userApi = com.neko.music.data.api.UserApi(token)
                                
                                val response = userApi.uploadMusic(
                                    audioFile = audioBytes,
                                    audioFileName = audioFileName,
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    language = languageCode,
                                    tags = tags,
                                    duration = durationSeconds,  // 直接使用解析出的秒数
                                    uploadUserId = userId,  // 使用真实的用户 ID
                                    lyricsFile = lyricsBytes,
                                    coverImage = coverBytes
                                )
                                
                                // API调用完成后，等待模拟进度完成到95%，然后设置为100%
                                uploadJob.join()
                                uploadProgress = 100f
                                
                                if (response.success) {
                                    onUploadSuccess()
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        response.message,
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    fileReadFailed,
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(
                                context,
                                "$uploadFailed ${e.message}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } finally {
                            isUploading = false
                            uploadProgress = 0f
                        }
                    }
                },
                enabled = !isUploading && !isParsing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoseRed
                )
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(id = R.string.upload_music))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isUploading
            ) {
                Text(stringResource(id = android.R.string.cancel))
            }
        }
    )
    
    // 完整歌词对话框
    if (showFullLyricsDialog) {
        AlertDialog(
            onDismissRequest = { showFullLyricsDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.full_lyrics),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = fullLyricsContent,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showFullLyricsDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoseRed
                    )
                ) {
                    Text(stringResource(id = android.R.string.ok))
                }
            }
        )
    }
}

private fun getFileName(context: android.content.Context, uri: android.net.Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = it.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != -1) {
            result = result?.substring(cut!! + 1)
        }
    }
    return result
}

// 音频元数据数据类
private data class AudioMetadata(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: String = "",
    val durationSeconds: Int = 0,  // 添加秒数字段
    val language: String = "",
    val cover: android.graphics.Bitmap? = null
)

// 解析音频文件元数据
private suspend fun parseAudioMetadata(
    context: android.content.Context,
    uri: android.net.Uri
): AudioMetadata = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val retriever = android.media.MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        
        // 提取元数据
        val title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
        val artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
        val album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
        
        // 提取时长
        val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val duration = formatDurationFromMs(durationMs)
        val durationSeconds = (durationMs / 1000).toInt()  // 计算秒数
        
        // 提取封面图片
        val cover = retriever.embeddedPicture?.let { 
            android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) 
        }
        
        AudioMetadata(
            title = title,
            artist = artist,
            album = album,
            duration = duration,
            durationSeconds = durationSeconds,  // 添加秒数
            language = "",
            cover = cover
        )
    } catch (e: Exception) {
        // 解析失败，返回空值
        AudioMetadata()
    } finally {
        retriever.release()
    }
}

// 格式化时长（毫秒转 mm:ss）
private fun formatDurationFromMs(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", minutes, secs)
}