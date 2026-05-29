package com.neko.music.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.kyant.backdrop.backdrops.LayerBackdrop
import coil3.compose.AsyncImage
import com.neko.music.R
import com.neko.music.util.NeteasePlaylistImport
import com.neko.music.util.UrlConfig
import androidx.compose.ui.zIndex
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.neko.music.ui.theme.isAppDarkTheme
import coil3.request.error
import coil3.request.placeholder
import com.neko.music.data.manager.TokenManager
import com.neko.music.data.api.BatchAddMusicResponse
import com.neko.music.data.api.MusicApi
import com.neko.music.data.api.MusicSearchBusyException
import com.neko.music.data.api.NeteasePlaylistApi
import com.neko.music.data.api.PlaylistApi
import com.neko.music.data.api.PlaylistMusicListResponse
import com.neko.music.data.api.PlaylistListResponse
import com.neko.music.data.api.PlaylistResponse
import com.neko.music.data.api.FavoriteApi
import com.neko.music.data.model.Playlist
import com.neko.music.data.model.SearchItem
import com.neko.music.ui.theme.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.ui.components.GlassDialogOverlay
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException as KtorSocketTimeoutException
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException

/** 暗色底图上可读的主/次文字色（避免灰紫 B8B8D1 对比不足） */
private val MyPlaylistsDarkPrimaryText = Color(0xFFFFF8FA)
private val MyPlaylistsDarkSecondaryText = LightRose
private val MyPlaylistsDarkPlaceholderText = SakuraPink.copy(alpha = 0.88f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPlaylistsScreen(
    onNavigateToPlaylistDetail: (Int, String, String?, String?, String?, Int?) -> Unit,
    onNavigateToFavorite: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenManager = remember { TokenManager(context) }
    val playlistApi = remember { PlaylistApi(tokenManager.getToken(), context) }
    val favoriteApi = remember { FavoriteApi(context) }
    val neteasePlaylistApi = remember { NeteasePlaylistApi() }
    val musicApi = remember { MusicApi(context) }
    
    // 预加载字符串资源
    val pleaseLoginFirst = stringResource(id = R.string.please_login_first)
    val myFavoritesLabel = stringResource(id = R.string.my_favorites_label)
    val loadingFailed = stringResource(id = R.string.loading_failed)

    val scheme = MaterialTheme.colorScheme
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)
    
    // 歌单数据
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var favoritePlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var favoritesCount by remember { mutableStateOf(0) }
    var favorites by remember { mutableStateOf<List<com.neko.music.data.api.FavoriteMusic>>(emptyList()) }
    var playlistFirstMusicCovers by remember { mutableStateOf<Map<Int, String>>(emptyMap()) } // 存储每个歌单第一首音乐的封面
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    
    // 初始化 - 加载歌单和收藏数据
    LaunchedEffect(Unit) {
        try {
            val token = tokenManager.getToken()
            if (token != null) {
                // 加载歌单列表
                val playlistResponse: PlaylistListResponse = playlistApi.getMyPlaylists()
                Log.d("MyPlaylistsScreen", "歌单API响应: success=${playlistResponse.success}, message=${playlistResponse.message}")
                if (playlistResponse.success) {
                    // 转换PlaylistInfo到Playlist
                    playlists = playlistResponse.playlists?.map { info ->
                        val creatorUserId = info.creator?.id ?: info.userId ?: 1
                        val creatorUsername = info.creator?.username ?: info.username
                        Log.d("MyPlaylistsScreen", "API返回歌单: id=${info.id}, name=${info.name}, userId=${info.userId}, creator=${info.creator}, creatorUsername=$creatorUsername")
                        Playlist(info.id, info.name, info.musicCount, creatorUserId, info.createdAt, info.coverPath, info.description, creatorUsername)
                    } ?: emptyList()
                    Log.d("MyPlaylistsScreen", "歌单列表: ${playlists.size}个")
                    playlists.forEach { 
                        Log.d("MyPlaylistsScreen", "转换后歌单: id=${it.id}, name=${it.name}, userId=${it.userId}, username=${it.username}")
                    }
                    
                    // 异步加载每个歌单的第一首音乐封面（仅当歌单没有封面时）
                    playlists.forEach { playlist ->
                        if (playlist.coverPath.isNullOrEmpty() && playlist.musicCount > 0) {
                            scope.launch {
                                try {
                                    val musicResponse: PlaylistMusicListResponse = playlistApi.getPlaylistMusic(playlist.id)
                                    if (musicResponse.success && musicResponse.musicList?.isNotEmpty() == true) {
                                        val firstMusic = musicResponse.musicList[0]
                                        // 使用第一首音乐的ID来获取封面
                                        val coverUrl = UrlConfig.getMusicCoverUrl(firstMusic.id)
                                        playlistFirstMusicCovers = playlistFirstMusicCovers + (playlist.id to coverUrl)
                                        Log.d("MyPlaylistsScreen", "歌单${playlist.id}第一首音乐ID: ${firstMusic.id}, 封面: $coverUrl")
                                    }
                                } catch (e: Exception) {
                                    Log.e("MyPlaylistsScreen", "加载歌单${playlist.id}第一首音乐封面失败", e)
                                }
                            }
                        }
                    }
                } else {
                    Log.e("MyPlaylistsScreen", "加载歌单失败: ${playlistResponse.message}")
                }
                
                // 加载收藏列表
                val favoriteResponse = favoriteApi.getFavorites(token)
                Log.d("MyPlaylistsScreen", "收藏API响应: success=${favoriteResponse.success}, 数量=${favoriteResponse.favorites.size}")
                if (favoriteResponse.success) {
                    favoritesCount = favoriteResponse.favorites.size
                    favorites = favoriteResponse.favorites
                    if (favorites.isNotEmpty()) {
                        Log.d("MyPlaylistsScreen", "第一首音乐: id=${favorites[0].id}, title=${favorites[0].title}")
                        
                        // 为"我喜欢的音乐"加载封面（使用第一首收藏音乐的封面）
                        val firstFavorite = favorites[0]
                        val coverUrl = UrlConfig.getMusicCoverUrl(firstFavorite.id)
                        playlistFirstMusicCovers = playlistFirstMusicCovers + (0 to coverUrl)
                    }
                }

                // 加载收藏歌单列表
                val favoritePlaylistResponse = favoriteApi.getFavoritePlaylists(token)
                Log.d("MyPlaylistsScreen", "收藏歌单API响应: success=${favoritePlaylistResponse.success}, 数量=${favoritePlaylistResponse.playlists.size}")
                if (favoritePlaylistResponse.success) {
                    favoritePlaylists = favoritePlaylistResponse.playlists.map { info ->
                        Playlist(
                            info.id,
                            info.name,
                            info.musicCount,
                            1,
                            java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(info.createdAt)),
                            null,
                            info.description,
                            info.creator?.username
                        )
                    }
                    Log.d("MyPlaylistsScreen", "收藏歌单列表: ${favoritePlaylists.size}个")

                    // 异步加载每个收藏歌单的第一首音乐封面
                    favoritePlaylists.forEach { playlist ->
                        if (playlist.musicCount > 0) {
                            scope.launch {
                                try {
                                    // 使用收藏歌单的API获取音乐列表
                                    val musicResponse: PlaylistMusicListResponse = playlistApi.getPlaylistMusic(playlist.id)
                                    if (musicResponse.success && musicResponse.musicList?.isNotEmpty() == true) {
                                        val firstMusic = musicResponse.musicList[0]
                                        val coverUrl = UrlConfig.getMusicCoverUrl(firstMusic.id)
                                        playlistFirstMusicCovers = playlistFirstMusicCovers + (playlist.id to coverUrl)
                                    }
                                } catch (e: Exception) {
                                    Log.e("MyPlaylistsScreen", "加载收藏歌单${playlist.id}封面失败", e)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MyPlaylistsScreen", "初始化失败", e)
            errorMessage = "$loadingFailed: ${e.message ?: ""}"
            showError = true
        } finally {
            isLoading = false
        }
    }

    // 刷新函数
    suspend fun refreshData() {
        try {
            val token = tokenManager.getToken()
            if (token != null) {
                // 加载歌单列表
                val playlistResponse: PlaylistListResponse = playlistApi.getMyPlaylists()
                Log.d("MyPlaylistsScreen", "刷新歌单API响应: success=${playlistResponse.success}")
                if (playlistResponse.success) {
                    playlists = playlistResponse.playlists?.map { info ->
                        Playlist(info.id, info.name, info.musicCount, 1, info.createdAt, info.coverPath, info.description, info.username)
                    } ?: emptyList()

                    // 清空之前的封面缓存
                    playlistFirstMusicCovers = emptyMap()

                    // 异步加载每个歌单的第一首音乐封面
                    playlists.forEach { playlist ->
                        if (playlist.coverPath.isNullOrEmpty() && playlist.musicCount > 0) {
                            scope.launch {
                                try {
                                    val musicResponse: PlaylistMusicListResponse = playlistApi.getPlaylistMusic(playlist.id)
                                    if (musicResponse.success && musicResponse.musicList?.isNotEmpty() == true) {
                                        val firstMusic = musicResponse.musicList[0]
                                        val coverUrl = UrlConfig.getMusicCoverUrl(firstMusic.id)
                                        playlistFirstMusicCovers = playlistFirstMusicCovers + (playlist.id to coverUrl)
                                    }
                                } catch (e: Exception) {
                                    Log.e("MyPlaylistsScreen", "加载歌单${playlist.id}封面失败", e)
                                }
                            }
                        }
                    }
                }

                // 加载收藏列表
                val favoriteResponse = favoriteApi.getFavorites(token)
                Log.d("MyPlaylistsScreen", "刷新收藏API响应: success=${favoriteResponse.success}")
                if (favoriteResponse.success) {
                    favoritesCount = favoriteResponse.favorites.size
                    favorites = favoriteResponse.favorites
                    
                    // 为"我喜欢的音乐"加载封面（使用第一首收藏音乐的封面）
                    if (favorites.isNotEmpty()) {
                        val firstFavorite = favorites[0]
                        val coverUrl = UrlConfig.getMusicCoverUrl(firstFavorite.id)
                        playlistFirstMusicCovers = playlistFirstMusicCovers + (0 to coverUrl)
                    }
                }

                // 加载收藏歌单列表
                val favoritePlaylistResponse = favoriteApi.getFavoritePlaylists(token)
                Log.d("MyPlaylistsScreen", "刷新收藏歌单API响应: success=${favoritePlaylistResponse.success}")
                if (favoritePlaylistResponse.success) {
                    favoritePlaylists = favoritePlaylistResponse.playlists.map { info ->
                        Playlist(
                            info.id,
                            info.name,
                            info.musicCount,
                            1,
                            java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(info.createdAt)),
                            null,
                            info.description,
                            info.creator?.username
                        )
                    }

                    // 异步加载每个收藏歌单的第一首音乐封面
                    favoritePlaylists.forEach { playlist ->
                        if (playlist.musicCount > 0) {
                            scope.launch {
                                try {
                                    val musicResponse: PlaylistMusicListResponse = playlistApi.getPlaylistMusic(playlist.id)
                                    if (musicResponse.success && musicResponse.musicList?.isNotEmpty() == true) {
                                        val firstMusic = musicResponse.musicList[0]
                                        val coverUrl = UrlConfig.getMusicCoverUrl(firstMusic.id)
                                        playlistFirstMusicCovers = playlistFirstMusicCovers + (playlist.id to coverUrl)
                                    }
                                } catch (e: Exception) {
                                    Log.e("MyPlaylistsScreen", "加载收藏歌单${playlist.id}封面失败", e)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MyPlaylistsScreen", "刷新失败", e)
            errorMessage = context.getString(R.string.refresh_failed_format, e.message ?: "")
            showError = true
        } finally {
            isRefreshing = false
        }
    }

    // 获取完整的歌单列表
    val allPlaylists = remember(playlists, favoritePlaylists, favoritesCount) {
        listOf(
            Playlist(0, myFavoritesLabel, favoritesCount, 1, "2026-01-15", null, null, null)
        ) + playlists + favoritePlaylists
    }
    
    // 创建/编辑歌单对话框
    var showCreateDialog by remember { mutableStateOf(false) }
    var dialogPlaylistName by remember { mutableStateOf("") }
    var editingPlaylist by remember { mutableStateOf<Playlist?>(null) }

    // 外部导入
    var showImportSourceDialog by remember { mutableStateOf(false) }
    var showNeteasePlaylistIdDialog by remember { mutableStateOf(false) }
    var neteasePlaylistId by remember { mutableStateOf("") }
    var importDestination by remember { mutableStateOf<ImportDestination?>(null) }
    var importNewPlaylistName by remember { mutableStateOf("") }
    var isNeteaseImportLoading by remember { mutableStateOf(false) }
    var showImportMatchFailedDialog by remember { mutableStateOf(false) }
    var importMatchFailedItems by remember { mutableStateOf<List<SearchItem>>(emptyList()) }
    val importNeteaseProcessing = stringResource(R.string.import_netease_processing)
    val qqMusicNotSupported = stringResource(R.string.not_supported)
    val importNewPlaylistLabel = stringResource(R.string.import_destination_new_playlist)

    val importDestinationOptions = remember(playlists, favoritePlaylists, myFavoritesLabel, importNewPlaylistLabel) {
        buildList {
            add(ImportDestinationOption(ImportDestination.Favorites, myFavoritesLabel))
            playlists.forEach { playlist ->
                add(ImportDestinationOption(ImportDestination.UserPlaylist(playlist.id, playlist.name), playlist.name))
            }
            favoritePlaylists.forEach { playlist ->
                add(
                    ImportDestinationOption(
                        ImportDestination.FavoritePlaylist(playlist.id, playlist.name),
                        playlist.name,
                    ),
                )
            }
            add(ImportDestinationOption(ImportDestination.NewPlaylist, importNewPlaylistLabel))
        }
    }
    
    // 创建或更新歌单
    val createOrUpdatePlaylist = {
        scope.launch {
            try {
                val token = tokenManager.getToken()
                if (token != null) {
                    val response: PlaylistResponse = if (editingPlaylist != null) {
                        // 更新歌单
                        playlistApi.updatePlaylist(editingPlaylist!!.id, dialogPlaylistName)
                    } else {
                        // 创建歌单
                        playlistApi.createPlaylist(dialogPlaylistName)
                    }
                    
                    if (response.success) {
                        // 重新加载歌单列表
                        val newPlaylistResponse: PlaylistListResponse = playlistApi.getMyPlaylists()
                        if (newPlaylistResponse.success) {
                            playlists = newPlaylistResponse.playlists?.map { info ->
                                Playlist(info.id, info.name, info.musicCount, 1, info.createdAt, info.coverPath, info.description)
                            } ?: emptyList()
                        }
                        showCreateDialog = false
                        dialogPlaylistName = ""
                        editingPlaylist = null
                    } else {
                        errorMessage = response.message
                        showError = true
                    }
                }
            } catch (e: Exception) {
                Log.e("MyPlaylistsScreen", "操作失败", e)
                errorMessage = context.getString(R.string.operation_failed_generic)
                showError = true
            }
        }
    }
    
    // 删除歌单
    val deletePlaylist = { playlist: Playlist ->
        scope.launch {
            try {
                val response: PlaylistResponse = playlistApi.deletePlaylist(playlist.id)
                if (response.success) {
                    // 重新加载歌单列表
                    val newPlaylistResponse: PlaylistListResponse = playlistApi.getMyPlaylists()
                    if (newPlaylistResponse.success) {
                        playlists = newPlaylistResponse.playlists?.map { info ->
                            Playlist(info.id, info.name, info.musicCount, 1, info.createdAt, info.coverPath)
                        } ?: emptyList()
                    }
                } else {
                    errorMessage = response.message
                    showError = true
                }
            } catch (e: Exception) {
                Log.e("MyPlaylistsScreen", "删除失败", e)
                errorMessage = context.getString(R.string.delete_failed_format, e.message ?: "")
                showError = true
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // 仅背景进入本页 layerBackdrop；列表在兄弟层上采样，避免 NavHost 内多行共享 export 导致 SIGSEGV（对齐底栏「录屏在下、玻璃在上」）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop)
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.list_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // 顶部标题栏
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .padding(top = 8.dp, bottom = 8.dp, start = 16.dp, end = 16.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.my_playlists),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAppDarkTheme()) {
                            MyPlaylistsDarkPrimaryText
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        }
                    )
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = if (isAppDarkTheme()) LightRose else MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (allPlaylists.isEmpty()) {
                    // 空状态
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.music),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = stringResource(id = R.string.no_playlists_yet),
                                fontSize = 18.sp,
                                color = if (isAppDarkTheme()) {
                                    MyPlaylistsDarkSecondaryText
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    val pullRefreshState = rememberPullToRefreshState()

                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            scope.launch {
                                refreshData()
                            }
                        },
                        state = pullRefreshState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 150.dp)
                        ) {
                            itemsIndexed(allPlaylists) { index, playlist ->
                                // 检查是否是第一个收藏歌单（"我喜欢的音乐"的索引是0，所以用户歌单从1开始）
                                val isFirstFavoritePlaylist =
                                    index == playlists.size + 1 && favoritePlaylists.isNotEmpty()

                                // 如果是第一个收藏歌单，先显示分割线
                                if (isFirstFavoritePlaylist) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                            .height(1.dp)
                                            .background(
                                                if (isAppDarkTheme()) {
                                                    Color.White.copy(alpha = 0.1f)
                                                } else {
                                                    Color.Black.copy(alpha = 0.1f)
                                                }
                                            )
                                    )
                                }

                                PlaylistItem(
                                    playlist = playlist,
                                    favorites = favorites,
                                    firstMusicCover = playlistFirstMusicCovers[playlist.id],
                                    onEdit = {
                                        if (playlist.id != 0) { // "我的收藏"不能编辑
                                            editingPlaylist = playlist
                                            dialogPlaylistName = playlist.name
                                            showCreateDialog = true
                                        }
                                    },
                                    onDelete = {
                                        if (playlist.id != 0) { // "我的收藏"不能删除
                                            deletePlaylist(playlist)
                                        }
                                    },
                                    onClick = {
                                        // 点击歌单
                                        if (playlist.id == 0) {
                                            // "我的收藏"直接跳转到收藏页面
                                            onNavigateToFavorite()
                                        } else {
                                            // 其他歌单跳转到歌单详情页面
                                            onNavigateToPlaylistDetail(
                                                playlist.id,
                                                playlist.name,
                                                playlist.coverPath,
                                                playlist.description,
                                                playlist.username,
                                                playlist.userId
                                            )
                                        }
                                    }
                                )
                            }

                            // 添加创建按钮项
                            item {
                                val isDark = isAppDarkTheme()
                                val rowScheme = MaterialTheme.colorScheme
                                val rowGlass = LiquidGlassDefaults.myPlaylistsListRow
                                GlassSurface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                        .clickable {
                                            if (tokenManager.getToken() != null) {
                                                editingPlaylist = null
                                                dialogPlaylistName = ""
                                                showCreateDialog = true
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    pleaseLoginFirst,
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    backgroundAlpha = rowGlass.tint.background(isDark),
                                    borderAlpha = rowGlass.tint.border(isDark),
                                    highlightAlpha = rowGlass.tint.highlight(isDark),
                                    borderColor = if (isDark) Color.White else rowScheme.outline,
                                    liquidBlur = rowGlass.liquid.blur,
                                    liquidLensHeight = rowGlass.liquid.lensHeight,
                                    liquidLensAmount = rowGlass.liquid.lensAmount
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                tint = if (isDark) MyPlaylistsDarkPrimaryText else Color.DarkGray
                                            )
                                            Text(
                                                text = stringResource(id = R.string.create_new_playlist),
                                                fontSize = 16.sp,
                                                color = if (isDark) MyPlaylistsDarkPrimaryText else Color.DarkGray,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            item {
                                val isDark = isAppDarkTheme()
                                val rowScheme = MaterialTheme.colorScheme
                                val rowGlass = LiquidGlassDefaults.myPlaylistsListRow
                                GlassSurface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(80.dp)
                                        .clickable {
                                            if (tokenManager.getToken() != null) {
                                                showImportSourceDialog = true
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    pleaseLoginFirst,
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    backgroundAlpha = rowGlass.tint.background(isDark),
                                    borderAlpha = rowGlass.tint.border(isDark),
                                    highlightAlpha = rowGlass.tint.highlight(isDark),
                                    borderColor = if (isDark) Color.White else rowScheme.outline,
                                    liquidBlur = rowGlass.liquid.blur,
                                    liquidLensHeight = rowGlass.liquid.lensHeight,
                                    liquidLensAmount = rowGlass.liquid.lensAmount
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.UploadFile,
                                                contentDescription = null,
                                                tint = if (isDark) MyPlaylistsDarkPrimaryText else Color.DarkGray
                                            )
                                            Text(
                                                text = stringResource(id = R.string.import_playlist_from_external),
                                                fontSize = 16.sp,
                                                color = if (isDark) MyPlaylistsDarkPrimaryText else Color.DarkGray,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showImportSourceDialog,
            enter = LiquidCenterModalTransitions.Enter,
            exit = LiquidCenterModalTransitions.Exit,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(46f),
        ) {
            ImportPlaylistSourceDialog(
                sampleBackdrop = pageBackdrop,
                onNeteaseClick = {
                    showImportSourceDialog = false
                    neteasePlaylistId = ""
                    importDestination = null
                    importNewPlaylistName = ""
                    showNeteasePlaylistIdDialog = true
                },
                onQqClick = {
                    Toast.makeText(context, qqMusicNotSupported, Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showImportSourceDialog = false },
            )
        }

        AnimatedVisibility(
            visible = showNeteasePlaylistIdDialog,
            enter = LiquidCenterModalTransitions.Enter,
            exit = LiquidCenterModalTransitions.Exit,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(47f),
        ) {
            NeteasePlaylistIdDialog(
                playlistId = neteasePlaylistId,
                destinationOptions = importDestinationOptions,
                selectedDestination = importDestination,
                newPlaylistName = importNewPlaylistName,
                isLoading = isNeteaseImportLoading,
                loadingText = importNeteaseProcessing,
                sampleBackdrop = pageBackdrop,
                onIdChange = { neteasePlaylistId = NeteasePlaylistImport.normalizePlaylistIdInput(it) },
                onDestinationChange = { importDestination = it },
                onNewPlaylistNameChange = { importNewPlaylistName = it },
                onConfirm = {
                    val sourceId = NeteasePlaylistImport.parsePlaylistId(neteasePlaylistId)
                        ?: neteasePlaylistId.trim()
                    val playlistIdLong = sourceId.toLongOrNull()
                    if (playlistIdLong == null) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.import_netease_playlist_id_invalid),
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@NeteasePlaylistIdDialog
                    }
                    scope.launch {
                        isNeteaseImportLoading = true
                        try {
                            val responseResult = neteasePlaylistApi.fetchPlaylistDetail(playlistIdLong)
                            responseResult.fold(
                                onSuccess = { response ->
                                    if (response.code != 200 || response.playlist == null) {
                                        Toast.makeText(
                                            context,
                                            neteasePlaylistApi.errorMessage(response),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } else {
                                        val playlist = response.playlist
                                        val destination = importDestination
                                        val newName = importNewPlaylistName.trim()
                                        neteasePlaylistApi.logPlaylistTracks(playlist)
                                        val matchResult = neteasePlaylistApi.matchTracksInLibrary(
                                            playlist,
                                            musicApi,
                                        )
                                        matchResult.fold(
                                            onSuccess = { stats ->
                                                Toast.makeText(
                                                    context,
                                                    context.getString(
                                                        R.string.import_netease_match_success,
                                                        stats.successCount,
                                                        stats.failCount,
                                                    ),
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                                if (stats.matchedMusicIds.isNotEmpty() && destination != null) {
                                                    val token = tokenManager.getToken()
                                                    if (token != null) {
                                                        val importResponse = importMatchedMusicToDestination(
                                                            destination = destination,
                                                            musicIds = stats.matchedMusicIds,
                                                            newPlaylistName = newName,
                                                            token = token,
                                                            playlistApi = playlistApi,
                                                            favoriteApi = favoriteApi,
                                                            context = context,
                                                        )
                                                        showNeteaseImportResultToast(context, importResponse)
                                                        if (importResponse.success ||
                                                            (importResponse.addedCount ?: 0) > 0
                                                        ) {
                                                            refreshData()
                                                        }
                                                    }
                                                } else if (stats.matchedMusicIds.isEmpty()) {
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(
                                                            R.string.import_netease_no_matched_to_import,
                                                        ),
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                }
                                                showNeteasePlaylistIdDialog = false
                                                neteasePlaylistId = ""
                                                importDestination = null
                                                importNewPlaylistName = ""
                                                if (stats.failedItems.isNotEmpty()) {
                                                    importMatchFailedItems = stats.failedItems
                                                    showImportMatchFailedDialog = true
                                                }
                                            },
                                            onFailure = { error ->
                                                val toastText = when {
                                                    isNeteaseMatchBusy(error) ->
                                                        context.getString(R.string.import_netease_match_busy)
                                                    error.message == "无可搜索曲目" ->
                                                        context.getString(R.string.import_netease_match_no_tracks)
                                                    else -> {
                                                        val detail = error.message?.takeIf { it.isNotBlank() }
                                                            ?: context.getString(R.string.import_netease_fetch_failed)
                                                        context.getString(
                                                            R.string.import_netease_match_failed,
                                                            detail,
                                                        )
                                                    }
                                                }
                                                Toast.makeText(
                                                    context,
                                                    toastText,
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            },
                                        )
                                    }
                                },
                                onFailure = {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.import_netease_fetch_failed),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                            )
                        } finally {
                            isNeteaseImportLoading = false
                        }
                    }
                },
                onDismiss = {
                    if (isNeteaseImportLoading) return@NeteasePlaylistIdDialog
                    showNeteasePlaylistIdDialog = false
                    neteasePlaylistId = ""
                    importDestination = null
                    importNewPlaylistName = ""
                },
            )
        }

        AnimatedVisibility(
            visible = showImportMatchFailedDialog,
            enter = LiquidCenterModalTransitions.Enter,
            exit = LiquidCenterModalTransitions.Exit,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(48f),
        ) {
            ImportMatchFailedDialog(
                failedItems = importMatchFailedItems,
                sampleBackdrop = pageBackdrop,
                onDismiss = {
                    showImportMatchFailedDialog = false
                    importMatchFailedItems = emptyList()
                },
            )
        }

        // 创建/编辑歌单对话框
        AnimatedVisibility(
            visible = showCreateDialog,
            enter = LiquidCenterModalTransitions.Enter,
            exit = LiquidCenterModalTransitions.Exit,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(45f),
        ) {
            PlaylistDialog(
                title = if (editingPlaylist != null) stringResource(id = R.string.edit_playlist) else stringResource(id = R.string.create_playlist_dialog),
                playlistName = dialogPlaylistName,
                sampleBackdrop = pageBackdrop,
                onNameChange = { dialogPlaylistName = it },
                onConfirm = { createOrUpdatePlaylist() },
                onDismiss = { 
                    showCreateDialog = false
                    dialogPlaylistName = ""
                    editingPlaylist = null
                }
            )
        }
        
        // 错误提示
        if (showError) {
            LaunchedEffect(showError) {
                if (showError) {
                    kotlinx.coroutines.delay(2000)
                    showError = false
                }
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 40.dp)
                        .shadow(
                            elevation = 12.dp,
                            spotColor = RoseRed.copy(alpha = 0.35f),
                            ambientColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
                        ),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistItem(
    playlist: Playlist,
    favorites: List<com.neko.music.data.api.FavoriteMusic>,
    firstMusicCover: String? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit = {}
) {
    val coverText = stringResource(id = R.string.content_description_cover)
    val songsCountLabelText = stringResource(id = R.string.songs_count_label, playlist.musicCount)
    val editText = stringResource(id = R.string.edit_playlist)
    val deleteText = stringResource(id = R.string.delete)

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    val isMyFavorites = playlist.id == 0
    val isDarkTheme = isAppDarkTheme()
    val itemScheme = MaterialTheme.colorScheme

    val coverUrl = remember(playlist, favorites, firstMusicCover) {
        val url = when {
            playlist.id == 0 && playlist.name == "我的收藏" -> {
                val firstFavorite = favorites.firstOrNull()
                if (firstFavorite != null) {
                    UrlConfig.getMusicCoverUrl(firstFavorite.id)
                } else {
                    UrlConfig.getDefaultAvatarUrl()
                }
            }
            !playlist.coverPath.isNullOrEmpty() -> {
                UrlConfig.buildFullUrl("${playlist.coverPath}")
            }
            !firstMusicCover.isNullOrEmpty() -> {
                firstMusicCover
            }
            else -> {
                UrlConfig.getDefaultAvatarUrl()
            }
        }
        Log.d("PlaylistItem", "歌单ID=${playlist.id}, 名称=${playlist.name}, coverPath=${playlist.coverPath}, firstMusicCover=$firstMusicCover, coverUrl=$url")
        url
    }

    val rowGlass = LiquidGlassDefaults.myPlaylistsListRow
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable {
                isPressed = true
                onClick()
            }
            .scale(scale),
        shape = RoundedCornerShape(16.dp),
        backgroundAlpha = rowGlass.tint.background(isDarkTheme),
        borderAlpha = rowGlass.tint.border(isDarkTheme),
        highlightAlpha = rowGlass.tint.highlight(isDarkTheme),
        borderColor = if (isDarkTheme) Color.White else itemScheme.outline,
        liquidBlur = rowGlass.liquid.blur,
        liquidLensHeight = rowGlass.liquid.lensHeight,
        liquidLensAmount = rowGlass.liquid.lensAmount
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 歌单封面
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isDarkTheme) {
                            Color(0xFF353558).copy(alpha = 0.6f)
                        } else {
                            Color(0xFFE0E0E0)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverUrl)
                        .crossfade(true)
                        .error(R.drawable.music)
                        .placeholder(R.drawable.music)
                        .build(),
                    contentDescription = coverText,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            // 歌单信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = playlist.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkTheme) {
                        MyPlaylistsDarkPrimaryText
                    } else {
                        itemScheme.onSurface
                    }
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = songsCountLabelText,
                    fontSize = 14.sp,
                    color = if (isDarkTheme) {
                        MyPlaylistsDarkSecondaryText
                    } else {
                        itemScheme.onSurfaceVariant
                    }
                )
            }

            // 操作按钮
            if (!isMyFavorites) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Transparent
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Create,
                            contentDescription = editText,
                            tint = if (isDarkTheme) {
                                MyPlaylistsDarkSecondaryText
                            } else {
                                itemScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Transparent
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = deleteText,
                            tint = if (isDarkTheme) {
                                RoseRed.copy(alpha = 0.92f)
                            } else {
                                itemScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

private sealed class ImportDestination {
    data object Favorites : ImportDestination()
    data class UserPlaylist(val id: Int, val name: String) : ImportDestination()
    data class FavoritePlaylist(val id: Int, val name: String) : ImportDestination()
    data object NewPlaylist : ImportDestination()
}

private data class ImportDestinationOption(
    val destination: ImportDestination,
    val label: String,
)

@Composable
private fun ImportPlaylistSourceDialog(
    sampleBackdrop: LayerBackdrop,
    onNeteaseClick: () -> Unit,
    onQqClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.import_playlist_source_title)
    val neteaseLabel = stringResource(R.string.import_source_netease)
    val qqLabel = stringResource(R.string.import_source_qq)
    val scheme = MaterialTheme.colorScheme
    val isDark = isAppDarkTheme()
    val dialogGlass = LiquidGlassDefaults.myPlaylistsDialog
    val optionGlass = LiquidGlassDefaults.myPlaylistsDialogInput
    val titleColor = if (isDark) MyPlaylistsDarkPrimaryText else scheme.onSurface
    val mutedColor = if (isDark) MyPlaylistsDarkSecondaryText else scheme.onSurfaceVariant

    GlassDialogOverlay(sampleBackdrop = sampleBackdrop, onDismiss = onDismiss) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            sampleBackdrop = sampleBackdrop,
            backgroundAlpha = dialogGlass.tint.background(isDark),
            borderAlpha = dialogGlass.tint.border(isDark),
            highlightAlpha = dialogGlass.tint.highlight(isDark),
            borderColor = if (isDark) {
                SakuraPink.copy(alpha = LiquidGlassDefaults.appUpdateDialogDarkBorderSakuraAlpha)
            } else {
                scheme.outline
            },
            liquidBlur = dialogGlass.liquid.blur,
            liquidLensHeight = dialogGlass.liquid.lensHeight,
            liquidLensAmount = dialogGlass.liquid.lensAmount,
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                )
                Spacer(modifier = Modifier.height(20.dp))
                ImportSourceOptionRow(
                    label = neteaseLabel,
                    sampleBackdrop = sampleBackdrop,
                    optionGlass = optionGlass,
                    isDark = isDark,
                    textColor = titleColor,
                    onClick = onNeteaseClick,
                )
                Spacer(modifier = Modifier.height(12.dp))
                ImportSourceOptionRow(
                    label = qqLabel,
                    sampleBackdrop = sampleBackdrop,
                    optionGlass = optionGlass,
                    isDark = isDark,
                    textColor = titleColor,
                    onClick = onQqClick,
                )
            }
        }
    }
}

@Composable
private fun ImportSourceOptionRow(
    label: String,
    sampleBackdrop: LayerBackdrop,
    optionGlass: com.neko.music.ui.components.LiquidGlassPanel,
    isDark: Boolean,
    textColor: Color,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        sampleBackdrop = sampleBackdrop,
        backgroundAlpha = optionGlass.tint.background(isDark),
        borderAlpha = optionGlass.tint.border(isDark),
        highlightAlpha = optionGlass.tint.highlight(isDark),
        borderColor = if (isDark) Color.White.copy(alpha = 0.22f) else scheme.outline,
        liquidBlur = optionGlass.liquid.blur,
        liquidLensHeight = optionGlass.liquid.lensHeight,
        liquidLensAmount = optionGlass.liquid.lensAmount,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
            )
        }
    }
}

@Composable
private fun NeteasePlaylistIdDialog(
    playlistId: String,
    destinationOptions: List<ImportDestinationOption>,
    selectedDestination: ImportDestination?,
    newPlaylistName: String,
    isLoading: Boolean,
    loadingText: String,
    sampleBackdrop: LayerBackdrop,
    onIdChange: (String) -> Unit,
    onDestinationChange: (ImportDestination) -> Unit,
    onNewPlaylistNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.import_netease_playlist_title)
    val idLabel = stringResource(R.string.playlist_id)
    val idHint = stringResource(R.string.netease_playlist_id_hint)
    val destinationLabel = stringResource(R.string.import_destination_label)
    val newNameLabel = stringResource(R.string.import_new_playlist_name)
    val newNameHint = stringResource(R.string.import_new_playlist_name_hint)
    val cancelText = stringResource(R.string.cancel)
    val confirmText = stringResource(R.string.confirm)

    val scheme = MaterialTheme.colorScheme
    val isDark = isAppDarkTheme()
    val dialogGlass = LiquidGlassDefaults.myPlaylistsDialog
    val inputGlass = LiquidGlassDefaults.myPlaylistsDialogInput
    val confirmGlass = LiquidGlassDefaults.myPlaylistsDialogPrimaryButton
    val titleColor = if (isDark) MyPlaylistsDarkPrimaryText else scheme.onSurface
    val mutedColor = if (isDark) MyPlaylistsDarkSecondaryText else scheme.onSurfaceVariant
    val inputTextColor = if (isDark) MyPlaylistsDarkPrimaryText else scheme.onSurface
    val placeholderColor = if (isDark) MyPlaylistsDarkPlaceholderText else scheme.onSurfaceVariant
    val scrollState = rememberScrollState()
    val showNewPlaylistName = selectedDestination is ImportDestination.NewPlaylist
    val canConfirm = !isLoading &&
        playlistId.isNotBlank() &&
        selectedDestination != null &&
        (!showNewPlaylistName || newPlaylistName.isNotBlank())

    GlassDialogOverlay(
        sampleBackdrop = sampleBackdrop,
        onDismiss = { if (!isLoading) onDismiss() },
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            shape = RoundedCornerShape(24.dp),
            sampleBackdrop = sampleBackdrop,
            backgroundAlpha = dialogGlass.tint.background(isDark),
            borderAlpha = dialogGlass.tint.border(isDark),
            highlightAlpha = dialogGlass.tint.highlight(isDark),
            borderColor = if (isDark) {
                SakuraPink.copy(alpha = LiquidGlassDefaults.appUpdateDialogDarkBorderSakuraAlpha)
            } else {
                scheme.outline
            },
            liquidBlur = dialogGlass.liquid.blur,
            liquidLensHeight = dialogGlass.liquid.lensHeight,
            liquidLensAmount = dialogGlass.liquid.lensAmount,
        ) {
            Box {
                Column(
                    modifier = Modifier
                        .padding(28.dp)
                        .verticalScroll(scrollState),
                ) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = idLabel, fontSize = 14.sp, color = mutedColor)
                Spacer(modifier = Modifier.height(8.dp))
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp),
                    shape = RoundedCornerShape(14.dp),
                    sampleBackdrop = sampleBackdrop,
                    backgroundAlpha = inputGlass.tint.background(isDark),
                    borderAlpha = inputGlass.tint.border(isDark),
                    highlightAlpha = inputGlass.tint.highlight(isDark),
                    borderColor = if (isDark) Color.White.copy(alpha = 0.22f) else scheme.outline,
                    liquidBlur = inputGlass.liquid.blur,
                    liquidLensHeight = inputGlass.liquid.lensHeight,
                    liquidLensAmount = inputGlass.liquid.lensAmount,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (playlistId.isEmpty()) {
                            Text(text = idHint, fontSize = 17.sp, color = placeholderColor)
                        }
                        BasicTextField(
                            value = playlistId,
                            onValueChange = onIdChange,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = inputTextColor, fontSize = 18.sp),
                            singleLine = true,
                            cursorBrush = SolidColor(RoseRed),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text(text = destinationLabel, fontSize = 14.sp, color = mutedColor)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    destinationOptions.forEach { option ->
                        ImportDestinationRow(
                            label = option.label,
                            selected = selectedDestination?.let { it == option.destination } == true,
                            sampleBackdrop = sampleBackdrop,
                            optionGlass = inputGlass,
                            isDark = isDark,
                            textColor = titleColor,
                            mutedColor = mutedColor,
                            onClick = { onDestinationChange(option.destination) },
                        )
                    }
                }

                if (showNewPlaylistName) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = newNameLabel, fontSize = 14.sp, color = mutedColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        sampleBackdrop = sampleBackdrop,
                        backgroundAlpha = inputGlass.tint.background(isDark),
                        borderAlpha = inputGlass.tint.border(isDark),
                        highlightAlpha = inputGlass.tint.highlight(isDark),
                        borderColor = if (isDark) Color.White.copy(alpha = 0.22f) else scheme.outline,
                        liquidBlur = inputGlass.liquid.blur,
                        liquidLensHeight = inputGlass.liquid.lensHeight,
                        liquidLensAmount = inputGlass.liquid.lensAmount,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (newPlaylistName.isEmpty()) {
                                Text(text = newNameHint, fontSize = 16.sp, color = placeholderColor)
                            }
                            BasicTextField(
                                value = newPlaylistName,
                                onValueChange = onNewPlaylistNameChange,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(color = inputTextColor, fontSize = 16.sp),
                                singleLine = true,
                                cursorBrush = SolidColor(RoseRed),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isLoading,
                    ) {
                        Text(
                            text = cancelText,
                            fontSize = 17.sp,
                            color = mutedColor,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    GlassSurface(
                        modifier = Modifier
                            .height(48.dp)
                            .clickable(
                                enabled = canConfirm,
                                onClick = onConfirm,
                            ),
                        shape = RoundedCornerShape(14.dp),
                        sampleBackdrop = sampleBackdrop,
                        backgroundAlpha = confirmGlass.background(isDark),
                        borderAlpha = confirmGlass.border(isDark),
                        highlightAlpha = confirmGlass.highlight(isDark),
                        liquidBlur = dialogGlass.liquid.blur,
                        liquidLensHeight = dialogGlass.liquid.lensHeight,
                        liquidLensAmount = dialogGlass.liquid.lensAmount,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = confirmText,
                                fontSize = 17.sp,
                                color = if (isDark) {
                                    if (canConfirm) {
                                        MyPlaylistsDarkPrimaryText
                                    } else {
                                        SakuraPink.copy(alpha = 0.45f)
                                    }
                                } else {
                                    if (canConfirm) scheme.onSurface else Color.Gray
                                },
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }
                }
                }
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            CircularProgressIndicator(color = RoseRed)
                            Text(
                                text = loadingText,
                                fontSize = 15.sp,
                                color = titleColor,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportMatchFailedDialog(
    failedItems: List<SearchItem>,
    sampleBackdrop: LayerBackdrop,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.import_netease_match_failed_dialog_title)
    val summary = stringResource(
        R.string.import_netease_match_failed_dialog_summary,
        failedItems.size,
    )
    val unknownArtist = stringResource(R.string.import_netease_match_failed_unknown_artist)
    val confirmText = stringResource(R.string.confirm)

    val scheme = MaterialTheme.colorScheme
    val isDark = isAppDarkTheme()
    val dialogGlass = LiquidGlassDefaults.myPlaylistsDialog
    val confirmGlass = LiquidGlassDefaults.myPlaylistsDialogPrimaryButton
    val titleColor = if (isDark) MyPlaylistsDarkPrimaryText else scheme.onSurface
    val mutedColor = if (isDark) MyPlaylistsDarkSecondaryText else scheme.onSurfaceVariant

    GlassDialogOverlay(sampleBackdrop = sampleBackdrop, onDismiss = onDismiss) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            shape = RoundedCornerShape(24.dp),
            sampleBackdrop = sampleBackdrop,
            backgroundAlpha = dialogGlass.tint.background(isDark),
            borderAlpha = dialogGlass.tint.border(isDark),
            highlightAlpha = dialogGlass.tint.highlight(isDark),
            borderColor = if (isDark) {
                SakuraPink.copy(alpha = LiquidGlassDefaults.appUpdateDialogDarkBorderSakuraAlpha)
            } else {
                scheme.outline
            },
            liquidBlur = dialogGlass.liquid.blur,
            liquidLensHeight = dialogGlass.liquid.lensHeight,
            liquidLensAmount = dialogGlass.liquid.lensAmount,
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = summary,
                    fontSize = 14.sp,
                    color = mutedColor,
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(failedItems, key = { index, _ -> index }) { _, item ->
                        val artistLabel = item.artist.ifBlank { unknownArtist }
                        Text(
                            text = "${item.title} — $artistLabel",
                            fontSize = 15.sp,
                            color = titleColor,
                            lineHeight = 20.sp,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    GlassSurface(
                        modifier = Modifier
                            .height(48.dp)
                            .clickable(onClick = onDismiss),
                        shape = RoundedCornerShape(14.dp),
                        sampleBackdrop = sampleBackdrop,
                        backgroundAlpha = confirmGlass.background(isDark),
                        borderAlpha = confirmGlass.border(isDark),
                        highlightAlpha = confirmGlass.highlight(isDark),
                        liquidBlur = dialogGlass.liquid.blur,
                        liquidLensHeight = dialogGlass.liquid.lensHeight,
                        liquidLensAmount = dialogGlass.liquid.lensAmount,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = confirmText,
                                fontSize = 17.sp,
                                color = if (isDark) MyPlaylistsDarkPrimaryText else scheme.onSurface,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportDestinationRow(
    label: String,
    selected: Boolean,
    sampleBackdrop: LayerBackdrop,
    optionGlass: com.neko.music.ui.components.LiquidGlassPanel,
    isDark: Boolean,
    textColor: Color,
    mutedColor: Color,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        sampleBackdrop = sampleBackdrop,
        backgroundAlpha = optionGlass.tint.background(isDark),
        borderAlpha = if (selected) 0.55f else optionGlass.tint.border(isDark),
        highlightAlpha = optionGlass.tint.highlight(isDark),
        borderColor = if (selected) {
            RoseRed.copy(alpha = if (isDark) 0.85f else 0.75f)
        } else if (isDark) {
            Color.White.copy(alpha = 0.22f)
        } else {
            scheme.outline
        },
        liquidBlur = optionGlass.liquid.blur,
        liquidLensHeight = optionGlass.liquid.lensHeight,
        liquidLensAmount = optionGlass.liquid.lensAmount,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) textColor else mutedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = RoseRed,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun PlaylistDialog(
    title: String,
    playlistName: String,
    sampleBackdrop: LayerBackdrop,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val playlistNameText = stringResource(id = R.string.playlist_name)
    val cancelText = stringResource(id = R.string.cancel)
    val confirmText = stringResource(id = R.string.confirm)

    val scheme = MaterialTheme.colorScheme
    val isDarkTheme = isAppDarkTheme()
    val dialogGlass = LiquidGlassDefaults.myPlaylistsDialog
    val inputGlass = LiquidGlassDefaults.myPlaylistsDialogInput
    val confirmGlass = LiquidGlassDefaults.myPlaylistsDialogPrimaryButton
    val titleColor = if (isDarkTheme) MyPlaylistsDarkPrimaryText else scheme.onSurface
    val mutedColor = if (isDarkTheme) MyPlaylistsDarkSecondaryText else scheme.onSurfaceVariant
    val inputTextColor = if (isDarkTheme) MyPlaylistsDarkPrimaryText else scheme.onSurface
    val placeholderColor = if (isDarkTheme) MyPlaylistsDarkPlaceholderText else scheme.onSurfaceVariant

    BackHandler(onBack = onDismiss)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(24.dp),
                sampleBackdrop = sampleBackdrop,
                backgroundAlpha = dialogGlass.tint.background(isDarkTheme),
                borderAlpha = dialogGlass.tint.border(isDarkTheme),
                highlightAlpha = dialogGlass.tint.highlight(isDarkTheme),
                borderColor = if (isDarkTheme) {
                    SakuraPink.copy(alpha = LiquidGlassDefaults.appUpdateDialogDarkBorderSakuraAlpha)
                } else {
                    scheme.outline
                },
                liquidBlur = dialogGlass.liquid.blur,
                liquidLensHeight = dialogGlass.liquid.lensHeight,
                liquidLensAmount = dialogGlass.liquid.lensAmount,
            ) {
                Column(modifier = Modifier.padding(32.dp)) {
                    Text(
                        text = title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        letterSpacing = 0.3.sp,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = playlistNameText,
                        fontSize = 14.sp,
                        color = mutedColor,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        sampleBackdrop = sampleBackdrop,
                        backgroundAlpha = inputGlass.tint.background(isDarkTheme),
                        borderAlpha = inputGlass.tint.border(isDarkTheme),
                        highlightAlpha = inputGlass.tint.highlight(isDarkTheme),
                        borderColor = if (isDarkTheme) Color.White.copy(alpha = 0.22f) else scheme.outline,
                        liquidBlur = inputGlass.liquid.blur,
                        liquidLensHeight = inputGlass.liquid.lensHeight,
                        liquidLensAmount = inputGlass.liquid.lensAmount,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (playlistName.isEmpty()) {
                                Text(
                                    text = playlistNameText,
                                    fontSize = 16.sp,
                                    color = placeholderColor,
                                )
                            }
                            BasicTextField(
                                value = playlistName,
                                onValueChange = onNameChange,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(
                                    color = inputTextColor,
                                    fontSize = 16.sp,
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(RoseRed),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = cancelText,
                                fontSize = 17.sp,
                                color = mutedColor,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        GlassSurface(
                            modifier = Modifier
                                .height(48.dp)
                                .clickable(
                                    enabled = playlistName.isNotBlank(),
                                    onClick = onConfirm,
                                ),
                            shape = RoundedCornerShape(14.dp),
                            sampleBackdrop = sampleBackdrop,
                            backgroundAlpha = confirmGlass.background(isDarkTheme),
                            borderAlpha = confirmGlass.border(isDarkTheme),
                            highlightAlpha = confirmGlass.highlight(isDarkTheme),
                            liquidBlur = dialogGlass.liquid.blur,
                            liquidLensHeight = dialogGlass.liquid.lensHeight,
                            liquidLensAmount = dialogGlass.liquid.lensAmount,
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = confirmText,
                                    fontSize = 17.sp,
                                    color = if (isDarkTheme) {
                                        if (playlistName.isNotBlank()) {
                                            MyPlaylistsDarkPrimaryText
                                        } else {
                                            SakuraPink.copy(alpha = 0.45f)
                                        }
                                    } else {
                                        if (playlistName.isNotBlank()) scheme.onSurface else Color.Gray
                                    },
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun importMatchedMusicToDestination(
    destination: ImportDestination,
    musicIds: List<Int>,
    newPlaylistName: String,
    token: String,
    playlistApi: PlaylistApi,
    favoriteApi: FavoriteApi,
    context: android.content.Context,
): BatchAddMusicResponse {
    return when (destination) {
        is ImportDestination.Favorites -> favoriteApi.addFavorites(token, musicIds)
        is ImportDestination.UserPlaylist ->
            playlistApi.addMusicsToPlaylist(destination.id, musicIds)
        is ImportDestination.FavoritePlaylist ->
            BatchAddMusicResponse(
                success = false,
                message = context.getString(R.string.import_netease_favorite_playlist_not_supported),
            )
        is ImportDestination.NewPlaylist -> {
            val createResponse = playlistApi.createPlaylist(newPlaylistName)
            val playlistId = createResponse.playlist?.id
            if (!createResponse.success || playlistId == null) {
                BatchAddMusicResponse(
                    success = false,
                    message = createResponse.message.ifBlank {
                        context.getString(R.string.import_netease_fetch_failed)
                    },
                )
            } else {
                playlistApi.addMusicsToPlaylist(playlistId, musicIds)
            }
        }
    }
}

private fun showNeteaseImportResultToast(
    context: android.content.Context,
    response: BatchAddMusicResponse,
) {
    val added = response.addedCount ?: 0
    val failedCount = response.failedMusicIds?.size ?: 0
    val text = when {
        !response.success && added > 0 ->
            context.getString(R.string.import_netease_import_partial, added, failedCount)
        response.success ->
            response.message.ifBlank {
                context.getString(R.string.import_netease_import_success, added)
            }
        else ->
            context.getString(
                R.string.import_netease_import_failed,
                response.message.ifBlank {
                    context.getString(R.string.import_netease_fetch_failed)
                },
            )
    }
    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}

private fun isNeteaseMatchBusy(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        when (current) {
            is MusicSearchBusyException,
            is HttpRequestTimeoutException,
            is KtorSocketTimeoutException,
            is SocketTimeoutException,
            -> return true
        }
        if (current.message?.contains("timeout", ignoreCase = true) == true) {
            return true
        }
        if (current.message?.contains("unexpected end of the input", ignoreCase = true) == true) {
            return true
        }
        current = current.cause
    }
    return false
}