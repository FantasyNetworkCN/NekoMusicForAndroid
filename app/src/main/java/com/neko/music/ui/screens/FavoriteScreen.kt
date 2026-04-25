package com.neko.music.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import com.neko.music.R
import coil3.compose.AsyncImage
import com.neko.music.util.UrlConfig
import com.neko.music.ui.components.GlassSurface
import com.neko.music.data.api.FavoriteApi
import com.neko.music.data.manager.TokenManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteScreen(
    onBackClick: () -> Unit,
    onMusicClick: (com.neko.music.data.model.Music) -> Unit
) {
    val context = LocalContext.current
    val tokenManager = TokenManager(context)
    val scope = rememberCoroutineScope()
    val favoriteApi = FavoriteApi(context)

    var favorites by remember { mutableStateOf<List<com.neko.music.data.api.FavoriteMusic>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    val isLoggedIn = tokenManager.isLoggedIn()

    // 预加载字符串资源
    val getFavoritesFailed = stringResource(id = R.string.get_favorites_failed)
    val networkErrorMsg = stringResource(id = R.string.network_error_msg)

    LaunchedEffect(Unit) {
        if (isLoggedIn) {
            isLoading = true
            val token = tokenManager.getToken()
            if (token != null) {
                try {
                    val response = favoriteApi.getFavorites(token)
                    if (response.success) {
                        favorites = response.favorites
                    } else {
                        errorMessage = getFavoritesFailed
                    }
                } catch (e: Exception) {
                    errorMessage = networkErrorMsg
                }
            }
            isLoading = false
        }
    }

    val filteredFavorites = if (searchQuery.isEmpty()) {
        favorites
    } else {
        favorites.filter { favorite ->
            favorite.title.contains(searchQuery, ignoreCase = true) ||
                    favorite.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    val playerManager = com.neko.music.service.MusicPlayerManager.getInstance(context)
    val playlistManager = com.neko.music.data.manager.PlaylistManager.getInstance(context)
    val musicApi = com.neko.music.data.api.MusicApi(context)

    suspend fun playAllFavorites() {
        playlistManager.clearPlaylist()
        filteredFavorites.forEach { favorite ->
            val music = com.neko.music.data.model.Music(
                id = favorite.id,
                title = favorite.title,
                artist = favorite.artist,
                album = favorite.album,
                duration = favorite.duration,
                filePath = favorite.filename,
                coverFilePath = favorite.cover,
                uploadUserId = 0,
                createdAt = ""
            )
            playlistManager.addToPlaylist(music)
        }

        if (filteredFavorites.isNotEmpty()) {
            val firstFavorite = filteredFavorites[0]
            val url = musicApi.getMusicFileUrl(
                com.neko.music.data.model.Music(
                    id = firstFavorite.id,
                    title = firstFavorite.title,
                    artist = firstFavorite.artist,
                    album = firstFavorite.album,
                    duration = firstFavorite.duration,
                    filePath = firstFavorite.filename,
                    coverFilePath = firstFavorite.cover,
                    uploadUserId = 0,
                    createdAt = ""
                )
            )
            val fullCoverUrl = if (firstFavorite.cover.isNotEmpty()) {
                UrlConfig.buildFullUrl("${firstFavorite.cover}")
            } else {
                UrlConfig.getMusicCoverUrl(firstFavorite.id)
            }
            playerManager.playMusic(
                url,
                firstFavorite.id,
                firstFavorite.title,
                firstFavorite.artist,
                firstFavorite.cover,
                fullCoverUrl
            )
        }
    }

    val isDarkTheme = isSystemInDarkTheme()

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.playlist_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(id = R.string.my_favorites),
                                color = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = stringResource(id = R.string.back),
                                    tint = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                    if (isLoggedIn) {
                        FavoriteSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            isDarkTheme = isDarkTheme
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when {
                    !isLoggedIn -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(id = R.string.please_login_first),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(id = R.string.login_after_view),
                                fontSize = 14.sp,
                                color = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = if (isDarkTheme) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary
                        )
                    }
                    errorMessage.isNotEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = errorMessage,
                                fontSize = 16.sp,
                                color = if (isDarkTheme) Color(0xFFFF6B6B) else Color.Red
                            )
                        }
                    }
                    filteredFavorites.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (searchQuery.isEmpty()) stringResource(id = R.string.no_favorites) else stringResource(id = R.string.no_search_results),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color.White.copy(alpha = 0.6f) else Color.Gray
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (searchQuery.isEmpty()) stringResource(id = R.string.go_discover) else stringResource(id = R.string.try_other_keywords),
                                fontSize = 14.sp,
                                color = if (isDarkTheme) Color.White.copy(alpha = 0.4f) else Color.Gray
                            )
                        }
                    }
                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (isDarkTheme) {
                                GlassSurface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .clickable {
                                            scope.launch {
                                                playAllFavorites()
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
                                            contentDescription = stringResource(id = R.string.play_all),
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(id = R.string.play_all_count, filteredFavorites.size),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .background(
                                            color = Color(0xFFE94560),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            scope.launch {
                                                playAllFavorites()
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
                                        contentDescription = stringResource(id = R.string.play_all),
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(id = R.string.play_all_count, filteredFavorites.size),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 160.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(filteredFavorites) { favorite ->
                                        FavoriteItem(
                                            music = favorite,
                                            onClick = {
                                                val music = com.neko.music.data.model.Music(
                                                    id = favorite.id,
                                                    title = favorite.title,
                                                    artist = favorite.artist,
                                                    album = favorite.album,
                                                    duration = favorite.duration,
                                                    filePath = favorite.filename,
                                                    coverFilePath = "",
                                                    uploadUserId = 0,
                                                    createdAt = ""
                                                )
                                                onMusicClick(music)
                                            }
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
}

@Composable
fun FavoriteItem(
    music: com.neko.music.data.api.FavoriteMusic,
    onClick: () -> Unit
) {
    val coverUrl = remember(music.cover) {
        if (!music.cover.isNullOrEmpty()) {
            UrlConfig.buildFullUrl("${music.cover}")
        } else {
            UrlConfig.getMusicCoverUrl(music.id)
        }
    }

    val isDarkTheme = isSystemInDarkTheme()

    if (isDarkTheme) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            FavoriteItemContent(
                coverUrl = coverUrl,
                music = music,
                isDarkTheme = true
            )
        }
    } else {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            FavoriteItemContent(
                coverUrl = coverUrl,
                music = music,
                isDarkTheme = false
            )
        }
    }
}

@Composable
fun FavoriteItemContent(
    coverUrl: String,
    music: com.neko.music.data.api.FavoriteMusic,
    isDarkTheme: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 封面
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isDarkTheme) Color.White.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            coil3.compose.AsyncImage(
                model = coverUrl,
                contentDescription = stringResource(id = R.string.cover),
                modifier = Modifier.fillMaxSize()
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
                color = if (isDarkTheme) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = music.artist,
                fontSize = 14.sp,
                color = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        // 时长
        Text(
            text = formatDuration(music.duration),
            fontSize = 12.sp,
            color = if (isDarkTheme) Color.White.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun FavoriteSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme()
) {
    if (isDarkTheme) {
        GlassSurface(
            modifier = modifier.height(40.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(id = R.string.search),
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp
                    ),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(id = R.string.search_music),
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }
    } else {
        Row(
            modifier = modifier
                .height(40.dp)
                .background(
                    color = Color(0xFFF5F5F5),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(id = R.string.search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                ),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(id = R.string.search_music),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%d:%02d", minutes, remainingSeconds)
}