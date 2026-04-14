package com.neko.music.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.neko.music.R
import com.neko.music.data.api.UploadedMusic
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.SakuraPink
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

const val baseUrl = "https://music.cnmsb.xin"

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
    
    // 下拉刷新状态
    var isRefreshing by remember { mutableStateOf(false) }

    // 预加载字符串资源
    val pleaseLoginFirst = stringResource(id = R.string.please_login_first)

    // 刷新数据的函数
    val refreshData = suspend {
        if (token != null) {
            isRefreshing = true
            try {
                val userApi = com.neko.music.data.api.UserApi(token)
                val response = userApi.getUploadedMusic()
                if (response.success) {
                    musicList = response.musicList
                    showError = false
                } else {
                    showError = true
                    errorMessage = response.message
                }
            } catch (e: Exception) {
                showError = true
                errorMessage = context.getString(R.string.get_data_failed, e.message ?: "Unknown error")
            } finally {
                isRefreshing = false
            }
        }
    }

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
                        }
                    )
                }
                musicList.isEmpty() -> {
                    EmptyView()
                }
                else -> {
                    // 下拉刷新状态
                    val pullRefreshState = rememberPullToRefreshState()

                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { scope.launch { refreshData() } },
                        state = pullRefreshState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        MusicList(
                            musicList = musicList,
                            onMusicClick = onMusicClick
                        )
                    }
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
            bottom = 80.dp
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
    
    // 预加载字符串资源，用于在onClick中使用
    val instrumentalText = stringResource(id = R.string.language_instrumental)
    
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
    
    // 上传流程控制
    var showMissingInfoDialog by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var showPreviewPlayDialog by remember { mutableStateOf(false) }
    var missingLyrics by remember { mutableStateOf(false) }
    var missingCover by remember { mutableStateOf(false) }
    
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
            // 直接读取文件，不做任何校验
            scope.launch {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader(StandardCharsets.UTF_8).use { it?.readText() }
                    if (content == null) {
                        android.widget.Toast.makeText(
                            context,
                            lyricsFileReadFailed,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }
                    
                    // 直接保存歌词文件和内容
                    lyricsFile = uri
                    lyricsPreview = content.take(200)  // 显示前200个字符作为预览
                    fullLyricsContent = content
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
                    enabled = !isUploading && language != stringResource(id = R.string.language_instrumental)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            language == stringResource(id = R.string.language_instrumental) -> 
                                "${stringResource(id = R.string.select_lyrics_file)} (纯音乐不需要歌词文件)"
                            lyricsFile != null -> stringResource(id = R.string.lyrics_file_selected)
                            else -> "${stringResource(id = R.string.select_lyrics_file)} ${stringResource(id = R.string.optional_field)}"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // 歌词预览
                if (language == stringResource(id = R.string.language_instrumental)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "纯音乐不需要歌词文件",
                            modifier = Modifier.padding(12.dp),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                } else if (lyricsFile != null) {
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
                                    
                                    // 如果选择的是纯音乐，清空歌词文件
                                    if (value == "instrumental") {
                                        lyricsFile = null
                                        lyricsPreview = ""
                                        fullLyricsContent = ""
                                    }
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
                    
                    // 检查是否缺失歌词或封面
                    missingLyrics = (lyricsFile == null && language != instrumentalText)
                    missingCover = (coverImage == null && coverBitmap == null)
                    
                    if (missingLyrics || missingCover) {
                        // 显示缺失信息确认对话框
                        showMissingInfoDialog = true
                    } else {
                        // 直接显示预览对话框
                        showPreviewDialog = true
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
    
    // 缺失信息确认对话框
    if (showMissingInfoDialog) {
        AlertDialog(
            onDismissRequest = { showMissingInfoDialog = false },
            title = {
                Text(
                    text = "提示",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "您上传的音乐缺少以下信息：",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (missingLyrics) {
                        Text(
                            text = "• 歌词文件",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (missingCover) {
                        Text(
                            text = "• 封面图片",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "是否继续上传？",
                        fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMissingInfoDialog = false
                        showPreviewDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoseRed
                    )
                ) {
                    Text("继续上传")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMissingInfoDialog = false }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            }
        )
    }
    
    // 试听对话框
    if (showPreviewPlayDialog && audioFile != null) {
        val currentAudioFile = audioFile!!
        val currentLyricsText = if (language != stringResource(id = R.string.language_instrumental) && fullLyricsContent.isNotEmpty()) {
            fullLyricsContent
        } else {
            null
        }
        PreviewPlayDialog(
            onDismiss = { showPreviewPlayDialog = false },
            audioUri = currentAudioFile,
            lyricsText = currentLyricsText,
            context = context
        )
    }
    
    // 预览确认对话框
    if (showPreviewDialog) {
        AlertDialog(
            onDismissRequest = { showPreviewDialog = false },
            title = {
                Text(
                    text = "确认上传",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // 音乐信息预览
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "音乐信息",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "标题: $title",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "艺术家: $artist",
                                fontSize = 14.sp
                            )
                            if (album.isNotBlank()) {
                                Text(
                                    text = "专辑: $album",
                                    fontSize = 14.sp
                                )
                            }
                            Text(
                                text = "语言: $language",
                                fontSize = 14.sp
                            )
                            if (duration.isNotBlank()) {
                                Text(
                                    text = "时长: $duration",
                                    fontSize = 14.sp
                                )
                            }
                            if (tags.isNotBlank()) {
                                Text(
                                    text = "标签: $tags",
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 文件状态预览
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "文件状态",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "音频文件: 已选择",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (language != stringResource(id = R.string.language_instrumental)) {
                                if (lyricsFile != null) {
                                    Text(
                                        text = "歌词文件: 已选择",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Text(
                                        text = "歌词文件: 未选择",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                Text(
                                    text = "歌词文件: 纯音乐不需要",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (coverImage != null || coverBitmap != null) {
                                Text(
                                    text = "封面图片: 已选择",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = "封面图片: 未选择",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "确认上传后将进入审核流程",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 试听按钮
                    Button(
                        onClick = {
                            showPreviewDialog = false
                            showPreviewPlayDialog = true
                        },
                        enabled = audioFile != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("试听")
                    }
                    
                    // 确认上传按钮
                    Button(
                        onClick = {
                            showPreviewDialog = false
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
                                            language = language,
                                            tags = tags,
                                            duration = durationSeconds,
                                            uploadUserId = userId,
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoseRed
                        )
                    ) {
                        Text("确认上传")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showPreviewDialog = false }) {
                    Text(stringResource(id = android.R.string.cancel))
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

// 试听对话框
@Composable
private fun PreviewPlayDialog(
    onDismiss: () -> Unit,
    audioUri: android.net.Uri,
    lyricsText: String?,
    context: android.content.Context
) {
    val scope = rememberCoroutineScope()
    
    // 创建ExoPlayer
    val exoPlayer = remember {
        com.google.android.exoplayer2.ExoPlayer.Builder(context).build().apply {
            val mediaItem = com.google.android.exoplayer2.MediaItem.fromUri(audioUri)
            setMediaItem(mediaItem)
            prepare()
        }
    }
    
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var lyrics by remember { mutableStateOf<List<LrcLine>>(emptyList()) }
    val lyricsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    
    // 监听播放状态
    LaunchedEffect(exoPlayer) {
        exoPlayer.addListener(object : com.google.android.exoplayer2.Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    com.google.android.exoplayer2.Player.STATE_READY -> {
                        duration = exoPlayer.duration
                    }
                    com.google.android.exoplayer2.Player.STATE_ENDED -> {
                        isPlaying = false
                    }
                }
            }
        })
    }
    
    // 更新当前位置
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            kotlinx.coroutines.delay(100)
            currentPosition = exoPlayer.currentPosition
        }
    }
    
    // 解析歌词
    LaunchedEffect(lyricsText) {
        if (lyricsText != null) {
            lyrics = parseLrcLyrics(lyricsText)
        }
    }
    
    // 自动滚动到当前歌词
    val currentLrcIndex = remember(currentPosition) {
        val currentTimeSeconds = currentPosition / 1000f
        lyrics.indexOfLast { it.time <= currentTimeSeconds }
    }
    
    LaunchedEffect(currentLrcIndex) {
        if (currentLrcIndex >= 0) {
            kotlinx.coroutines.delay(300) // 延迟滚动，避免跳动
            lyricsListState.animateScrollToItem(currentLrcIndex)
        }
    }
    
    // 清理播放器
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "试听",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                // 播放控制
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatTime(duration),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // 进度条
                Slider(
                    value = currentPosition.toFloat(),
                    onValueChange = { newValue ->
                        exoPlayer.seekTo(newValue.toLong())
                        currentPosition = newValue.toLong()
                    },
                    valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 播放/暂停按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            modifier = Modifier.fillMaxSize(),
                            tint = RoseRed
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 歌词显示
                if (lyrics.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        androidx.compose.foundation.lazy.LazyColumn(
                            state = lyricsListState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(lyrics) { index, lrcLine ->
                                val isActive = index == currentLrcIndex
                                val currentTimeSeconds = currentPosition / 1000f
                                val isNext = index == currentLrcIndex + 1
                                val scale = if (isActive) 1.1f else 1.0f
                                val alpha = when {
                                    isActive -> 1.0f
                                    isNext -> 0.7f
                                    else -> 0.5f
                                }
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = 16.dp,
                                            vertical = 8.dp
                                        )
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = lrcLine.text,
                                        fontSize = if (isActive) 16.sp else 14.sp,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isActive) RoseRed else MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    if (lrcLine.translation.isNotEmpty()) {
                                        Text(
                                            text = lrcLine.translation,
                                            fontSize = if (isActive) 14.sp else 12.sp,
                                            color = if (isActive) RoseRed.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (lyricsText == null) "无歌词文件" else "歌词解析失败",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    exoPlayer.stop()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoseRed
                )
            ) {
                Text("关闭")
            }
        }
    )
}