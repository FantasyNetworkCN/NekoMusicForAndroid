package com.neko.music.ui.screens

import com.neko.music.ui.icons.NekoIcon
import com.neko.music.ui.icons.NekoSymbol

import com.neko.music.debug.NekoLog as Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neko.music.data.cache.MusicCacheManager
import com.neko.music.data.cache.AppStorageManager
import com.neko.music.data.cache.AppStorageKind
import com.neko.music.data.cache.AppStorageUsage
import com.neko.music.util.UrlConfig
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.NekoAdaptivePageContent
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.ContentScale
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.neko.music.ui.components.PlaylistPageDarkTintOverlay
import com.neko.music.ui.components.AppPageBackgroundImage
import com.neko.music.ui.theme.isAppDarkTheme
import androidx.compose.ui.res.stringResource
import com.neko.music.R
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheManagementScreen(
    onBackClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isDarkTheme = isAppDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)
    val glassTint = LiquidGlassDefaults.screenListCard
    val glassBg = glassTint.background(isDarkTheme)
    val glassBorder = glassTint.border(isDarkTheme)
    val glassHighlight = glassTint.highlight(isDarkTheme)
    val cacheManager = remember { MusicCacheManager.getInstance(context) }
    val playlistManager = remember { com.neko.music.data.manager.PlaylistManager.getInstance(context) }
    val musicPlayerManager = remember { com.neko.music.service.MusicPlayerManager.getInstance(context) }

    // 预加载字符串资源
    val unknownArtist = stringResource(id = R.string.unknown_artist)
    val unknownSong = stringResource(id = R.string.unknown_song)

    // 缓存数据
    var cacheSize by remember { mutableStateOf(cacheManager.getCacheSizeFormatted()) }
    var cachedMusicCount by remember { mutableStateOf(cacheManager.getCachedMusicCount()) }
    var cachedItems by remember { mutableStateOf(cacheManager.getAllCachedItems()) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showDeleteItemDialog by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var storageUsage by remember { mutableStateOf(AppStorageUsage()) }
    var storageLoading by remember { mutableStateOf(true) }

    suspend fun refreshStorage() {
        storageUsage = withContext(Dispatchers.IO) { AppStorageManager.calculate(context) }
        storageLoading = false
    }
    
    // 定期更新缓存数据
    LaunchedEffect(Unit) {
        while (true) {
            refreshStorage()
            delay(5000)
            cacheSize = cacheManager.getCacheSizeFormatted()
            cachedMusicCount = cacheManager.getCachedMusicCount()
            cachedItems = cacheManager.getAllCachedItems()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop)
        ) {
            AppPageBackgroundImage(
                modifier = Modifier.fillMaxSize(),
            )
            PlaylistPageDarkTintOverlay(enabled = isDarkTheme)
        }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentColor = if (isDarkTheme) Color(0xFFF0F0F5) else scheme.onSurface,
        topBar = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                TopAppBar(
                    modifier = Modifier
                        .widthIn(max = 920.dp)
                        .fillMaxWidth(),
                    title = {
                        Text(
                            stringResource(id = R.string.space_management),
                            color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            NekoIcon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.back),
                                tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.9f) else scheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    actions = {
                    // 播放全部按钮
                    TextButton(
                        onClick = { 
                            if (cachedItems.isNotEmpty()) {
                                isLoading = true
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                    try {
                                        // 构造所有缓存音乐的 Music 对象。
                                        val musicList = mutableListOf<com.neko.music.data.model.Music>()
                                        
                                        for (item in cachedItems) {
                                            try {
                                                val musicId = item.first.toInt()
                                                // 构造一个临时的Music对象
                                                val tempMusic = com.neko.music.data.model.Music(
                                                    id = musicId,
                                                    title = item.second,
                                                    artist = item.third,
                                                    album = unknownSong,
                                                    duration = 0,
                                                    filePath = null,
                                                    coverFilePath = null,
                                                    uploadUserId = null,
                                                    createdAt = null
                                                )
                                                
                                                musicList.add(tempMusic)
                                            } catch (e: Exception) {
                                                Log.e("CacheManagementScreen", "添加音乐失败: ${item.first}", e)
                                            }
                                        }
                                        
                                        if (musicList.isNotEmpty()) {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                musicPlayerManager.playMusicListAndAwait(musicList, 0)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("CacheManagementScreen", "播放全部失败", e)
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        enabled = cachedItems.isNotEmpty() && !isLoading
                    ) {
                        Text(
                            text = stringResource(id = R.string.play_all),
                            color = if (cachedItems.isNotEmpty() && !isLoading) {
                                RoseRed
                            } else {
                                if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.45f) else scheme.onSurfaceVariant.copy(alpha = 0.45f)
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    TextButton(
                        onClick = { 
                            if (cachedItems.isNotEmpty()) {
                                showClearDialog = true
                            }
                        },
                        enabled = cachedItems.isNotEmpty()
                    ) {
                        Text(
                            text = stringResource(id = R.string.clear_all_cache),
                            color = if (cachedItems.isNotEmpty()) {
                                RoseRed
                            } else {
                                if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.45f) else scheme.onSurfaceVariant.copy(alpha = 0.45f)
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
                NekoAdaptivePageContent {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 150.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        StorageOverviewCard(
                            usage = storageUsage,
                            loading = storageLoading,
                            cachedMusicCount = cachedMusicCount,
                            cacheSize = cacheSize,
                            isLoading = isLoading,
                        )
                    }
                    item {
                        Text(
                            text = stringResource(R.string.storage_breakdown),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(storageUsage.items, key = { it.kind.name }) { item ->
                        StorageUsageRow(item = item, total = storageUsage.total, isDarkTheme = isDarkTheme)
                    }
                    item {
                        Text(
                            text = stringResource(id = R.string.cache_list),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 4.dp),
                        )
                    }
                    if (cachedItems.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = stringResource(id = R.string.no_cache),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource(id = R.string.cache_hint),
                                    fontSize = 14.sp,
                                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.6f) else scheme.onSurfaceVariant.copy(alpha = 0.85f),
                                )
                            }
                        }
                    } else {
                        items(cachedItems, key = { it.first }) { item ->
                            CacheItem(
                                musicId = item.first,
                                title = item.second,
                                artist = item.third,
                                onDelete = { showDeleteItemDialog = item },
                            )
                        }
                    }
                }
                }
            }
        }
    }
    }
    
    // 清空全部缓存对话框
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.clear_all_cache),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoseRed
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(id = R.string.confirm_clear_all_cache),
                        fontSize = 16.sp,
                        color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.clear_all_cache_info, cachedMusicCount),
                        fontSize = 14.sp,
                        color = Color.Red
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.free_space, cacheSize),
                        fontSize = 14.sp,
                        color = Color.Red
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        cacheManager.clearAllCache()
                        cacheSize = cacheManager.getCacheSizeFormatted()
                        cachedMusicCount = cacheManager.getCachedMusicCount()
                        cachedItems = cacheManager.getAllCachedItems()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoseRed
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.clear),
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(
                        text = stringResource(id = R.string.cancel),
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                }
            }
        )
    }
    
    // 删除单个缓存对话框
    showDeleteItemDialog?.let { (musicId, title, artist) ->
        AlertDialog(
            onDismissRequest = { showDeleteItemDialog = null },
            title = {
                Text(
                    text = stringResource(id = R.string.delete_cache),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoseRed
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(id = R.string.confirm_delete_cache),
                        fontSize = 16.sp,
                        color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else Color.Black
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                                cacheManager.deleteMusicCache(musicId.substringBefore('_').toIntOrNull() ?: musicId.toIntOrNull() ?: 0)
                        cacheSize = cacheManager.getCacheSizeFormatted()
                        cachedMusicCount = cacheManager.getCachedMusicCount()
                        cachedItems = cacheManager.getAllCachedItems()
                        showDeleteItemDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoseRed
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.delete),
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteItemDialog = null }) {
                    Text(
                        text = stringResource(id = R.string.cancel),
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                }
            }
        )
    }
}

@Composable
private fun StorageOverviewCard(
    usage: AppStorageUsage,
    loading: Boolean,
    cachedMusicCount: Int,
    cacheSize: String,
    isLoading: Boolean,
) {
    val isDark = isAppDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val tint = LiquidGlassDefaults.screenListCard
    val deviceFraction = if (usage.deviceTotal > 0L) usage.deviceUsed.toFloat() / usage.deviceTotal else 0f
    val reclaimable = usage.items.filter {
        it.kind in setOf(
            AppStorageKind.MUSIC, AppStorageKind.MEDIA, AppStorageKind.AI_CACHE,
            AppStorageKind.OTHER_CACHE, AppStorageKind.EXTERNAL_CACHE,
        )
    }.sumOf { it.bytes }
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        backgroundAlpha = tint.background(isDark),
        borderAlpha = tint.border(isDark),
        highlightAlpha = tint.highlight(isDark),
        borderColor = if (isDark) RoseRed.copy(alpha = 0.7f) else scheme.outline,
        material3Container = scheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(
                text = stringResource(R.string.space_management),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = if (loading) stringResource(R.string.storage_calculating) else AppStorageManager.format(usage.total),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.onSurface,
            )
            Text(
                text = stringResource(R.string.storage_app_total, AppStorageManager.format(usage.total)),
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { deviceFraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
            Text(
                text = stringResource(
                    R.string.storage_device_usage,
                    AppStorageManager.format(usage.deviceUsed),
                    AppStorageManager.format(usage.deviceTotal),
                    AppStorageManager.format(usage.deviceAvailable),
                ),
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.storage_reclaimable, AppStorageManager.format(reclaimable)),
                fontSize = 12.sp,
                color = RoseRed,
            )
            Text(
                text = stringResource(R.string.storage_cache_count, cachedMusicCount, cacheSize),
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant,
            )
            if (isLoading) {
                Text(stringResource(R.string.preparing_playlist), color = RoseRed, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StorageUsageRow(item: com.neko.music.data.cache.AppStorageItem, total: Long, isDarkTheme: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val tint = LiquidGlassDefaults.screenListCard
    val fraction = if (total > 0L) item.bytes.toFloat() / total else 0f
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundAlpha = tint.background(isDarkTheme) * 0.72f,
        borderAlpha = tint.border(isDarkTheme),
        highlightAlpha = tint.highlight(isDarkTheme),
        material3Container = scheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            com.neko.music.ui.icons.NekoSymbolIcon(
                symbol = NekoSymbol.Storage,
                contentDescription = null,
                color = scheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(storageKindLabel(item.kind), fontSize = 14.sp, color = scheme.onSurface)
                    Text(
                        stringResource(R.string.storage_percent, AppStorageManager.format(item.bytes), fraction * 100f),
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
private fun storageKindLabel(kind: AppStorageKind): String = stringResource(
    when (kind) {
        AppStorageKind.INSTALL -> R.string.storage_category_install
        AppStorageKind.MUSIC -> R.string.storage_category_music
        AppStorageKind.MEDIA -> R.string.storage_category_media
        AppStorageKind.AI_MODEL -> R.string.storage_category_ai_model
        AppStorageKind.AI_CACHE -> R.string.storage_category_ai_cache
        AppStorageKind.DATABASE -> R.string.storage_category_database
        AppStorageKind.PREFERENCES -> R.string.storage_category_preferences
        AppStorageKind.LOCAL_FILES -> R.string.storage_category_local_files
        AppStorageKind.OTHER_CACHE -> R.string.storage_category_other_cache
        AppStorageKind.OTHER_DATA -> R.string.storage_category_other_data
        AppStorageKind.EXTERNAL_CACHE -> R.string.storage_category_external_cache
    }
)

@Composable
fun CacheItem(
    musicId: String,
    title: String,
    artist: String,
    onDelete: () -> Unit
) {
    val isDarkTheme = isAppDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val glassTint = LiquidGlassDefaults.screenListCard
    val glassBg = glassTint.background(isDarkTheme)
    val glassBorder = glassTint.border(isDarkTheme)
    val glassHighlight = glassTint.highlight(isDarkTheme)
    val context = androidx.compose.ui.platform.LocalContext.current
    val cacheManager = remember { com.neko.music.data.cache.MusicCacheManager.getInstance(context) }
    val cachedCover = remember { cacheManager.getCachedCoverFile(musicId.substringBefore('_').toIntOrNull() ?: musicId.toIntOrNull()?: 0) }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDelete() },
        shape = RoundedCornerShape(12.dp),
        backgroundAlpha = glassBg,
        borderAlpha = glassBorder,
        highlightAlpha = glassHighlight
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面或默认图标
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = RoseRed.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (cachedCover != null && cachedCover.exists()) {
                    coil3.compose.AsyncImage(
                        model = cachedCover,
                        contentDescription = "封面",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.music),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "$artist · ID: $musicId",
                    fontSize = 13.sp,
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            
            NekoIcon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(id = R.string.delete),
                tint = RoseRed,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
