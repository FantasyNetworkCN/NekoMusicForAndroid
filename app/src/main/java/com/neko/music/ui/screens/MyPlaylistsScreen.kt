package com.neko.music.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import coil.compose.AsyncImage
import com.neko.music.R
import com.neko.music.util.UrlConfig
import androidx.compose.ui.zIndex
import coil.request.ImageRequest
import androidx.compose.foundation.isSystemInDarkTheme
import com.neko.music.data.manager.TokenManager
import com.neko.music.data.api.PlaylistApi
import com.neko.music.data.api.PlaylistMusic
import com.neko.music.data.api.PlaylistMusicListResponse
import com.neko.music.data.api.PlaylistListResponse
import com.neko.music.data.api.PlaylistResponse
import com.neko.music.data.api.FavoriteApi
import com.neko.music.data.model.Playlist
import com.neko.music.ui.theme.*
import kotlinx.coroutines.launch

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
    
    // 预加载字符串资源
    val pleaseLoginFirst = stringResource(id = R.string.please_login_first)
    val myFavoritesLabel = stringResource(id = R.string.my_favorites_label)
    val loadingFailed = stringResource(id = R.string.loading_failed)
    
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
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 背景图片 - 使用WindowInsets处理状态栏
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.list_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        // 暗色模式灰色遮罩
        if (isSystemInDarkTheme()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
        }
        
        // 内容层
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
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = RoseRed)
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // 歌单列表
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
                        val isFirstFavoritePlaylist = index == playlists.size + 1 && favoritePlaylists.isNotEmpty()
                        
                        // 如果是第一个收藏歌单，先显示分割线
                        if (isFirstFavoritePlaylist) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .height(1.dp)
                                    .background(
                                        if (isSystemInDarkTheme()) {
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
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .clickable {
                                    if (tokenManager.getToken() != null) {
                                        editingPlaylist = null
                                        dialogPlaylistName = ""
                                        showCreateDialog = true
                                    } else {
                                        Toast.makeText(context, pleaseLoginFirst, Toast.LENGTH_SHORT).show()
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSystemInDarkTheme()) {
                                    Color(0xFF252545).copy(alpha = 0.7f)
                                } else {
                                    Color.White.copy(alpha = 0.85f)
                                }
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 4.dp,
                                hoveredElevation = 6.dp
                            ),
                            shape = RoundedCornerShape(16.dp)
                                ) {                            Box(
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
                                        tint = RoseRed
                                    )
                                    Text(
                                        text = stringResource(id = R.string.create_new_playlist),
                                        fontSize = 16.sp,
                                        color = if (isSystemInDarkTheme()) {
                                            RoseRed.copy(alpha = 0.9f)
                                        } else {
                                            RoseRed
                                        },
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
        
        // 创建/编辑歌单对话框
        if (showCreateDialog) {
            PlaylistDialog(
                title = if (editingPlaylist != null) stringResource(id = R.string.edit_playlist) else stringResource(id = R.string.create_playlist_dialog),
                playlistName = dialogPlaylistName,
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

@Composable
fun PlaylistItem(
    playlist: Playlist,
    favorites: List<com.neko.music.data.api.FavoriteMusic>,
    firstMusicCover: String? = null, // 可选：歌单第一首音乐的封面
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit = {}
) {
    // Preload strings
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
    
    val isMyFavorites = playlist.id == 0 // "我的收藏"不能编辑/删除
    val isDarkTheme = isSystemInDarkTheme()
    
    // 确定要显示的封面URL
    val coverUrl = remember(playlist, favorites, firstMusicCover) {
        val url = when {
            playlist.id == 0 && playlist.name == "我的收藏" -> {
                // "我的收藏"使用第一首收藏音乐的封面
                val firstFavorite = favorites.firstOrNull()
                if (firstFavorite != null) {
                    UrlConfig.getMusicCoverUrl(firstFavorite.id)
                } else {
                    // 没有收藏，使用默认头像
                    UrlConfig.getDefaultAvatarUrl()
                }
            }
            !playlist.coverPath.isNullOrEmpty() -> {
                // 歌单有自己的封面
                UrlConfig.buildFullUrl("${playlist.coverPath}")
            }
            !firstMusicCover.isNullOrEmpty() -> {
                // 使用第一首音乐的封面
                firstMusicCover
            }
            else -> {
                // 没有封面，使用默认头像
                UrlConfig.getDefaultAvatarUrl()
            }
        }
        Log.d("PlaylistItem", "歌单ID=${playlist.id}, 名称=${playlist.name}, coverPath=${playlist.coverPath}, firstMusicCover=$firstMusicCover, coverUrl=$url")
        url
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isDarkTheme) {
                    Color(0xFF252545).copy(alpha = 0.7f)
                } else {
                    Color.White.copy(alpha = 0.85f)
                }
            )
            .shadow(
                elevation = 6.dp,
                spotColor = RoseRed.copy(alpha = 0.2f),
                ambientColor = if (isDarkTheme) {
                    Color(0xFFB8B8D1).copy(alpha = 0.1f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                }
            )
            .clickable {
                isPressed = true
                onClick()
            }
            .scale(scale)
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
                contentScale = ContentScale.Crop,
                error = {
                    Image(
                        painter = painterResource(R.drawable.music),
                        contentDescription = coverText,
                        modifier = Modifier.size(24.dp)
                    )
                }
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
                    Color(0xFFF0F0F5).copy(alpha = 0.95f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = songsCountLabelText,
                fontSize = 14.sp,
                color = if (isDarkTheme) {
                    Color(0xFFB8B8D1).copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        
        // 操作按钮（"我的收藏"不显示）
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
                            RoseRed.copy(alpha = 0.9f)
                        } else {
                            RoseRed
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
                            Color(0xFFB8B8D1).copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistDialog(
    title: String,
    playlistName: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // Preload strings
    val playlistNameText = stringResource(id = R.string.playlist_name)
    val cancelText = stringResource(id = R.string.cancel)
    val confirmText = stringResource(id = R.string.confirm)
    
    val isDarkTheme = isSystemInDarkTheme()
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (isDarkTheme) {
                Color(0xFF1A1A2E).copy(alpha = 0.95f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .shadow(
                    elevation = 12.dp,
                    spotColor = RoseRed.copy(alpha = 0.35f),
                    ambientColor = if (isDarkTheme) {
                        Color(0xFFB8B8D1).copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
                    }
                )
        ) {
            Column(
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoseRed,
                    letterSpacing = 0.3.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = onNameChange,
                    label = { Text(playlistNameText) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RoseRed,
                        unfocusedBorderColor = if (isDarkTheme) {
                            Color(0xFFB8B8D1).copy(alpha = 0.5f)
                        } else {
                            Color.Gray
                        },
                        cursorColor = RoseRed,
                        focusedTextColor = if (isDarkTheme) {
                            Color(0xFFF0F0F5).copy(alpha = 0.95f)
                        } else {
                            Color.Black
                        },
                        unfocusedTextColor = if (isDarkTheme) {
                            Color(0xFFF0F0F5).copy(alpha = 0.95f)
                        } else {
                            Color.Black
                        },
                        focusedPlaceholderColor = if (isDarkTheme) {
                            Color(0xFFB8B8D1).copy(alpha = 0.6f)
                        } else {
                            Color.Gray
                        },
                        unfocusedPlaceholderColor = if (isDarkTheme) {
                            Color(0xFFB8B8D1).copy(alpha = 0.6f)
                        } else {
                            Color.Gray
                        }
                    )
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = cancelText,
                            fontSize = 17.sp,
                            color = if (isDarkTheme) {
                                Color(0xFFB8B8D1).copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(14.dp))
                    
                    Button(
                        onClick = onConfirm,
                        enabled = playlistName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoseRed,
                            disabledContainerColor = RoseRed.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(
                            text = confirmText,
                            fontSize = 17.sp,
                            color = if (isDarkTheme) {
                                Color.White.copy(alpha = 0.95f)
                            } else {
                                MaterialTheme.colorScheme.onPrimary
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}