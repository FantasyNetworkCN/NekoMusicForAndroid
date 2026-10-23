package com.neko.music.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
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
import com.neko.music.R
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.isAppDarkTheme
import coil3.compose.AsyncImage
import com.neko.music.util.UrlConfig
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.PlaylistPageDarkTintOverlay
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.components.AppPageBackgroundImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.data.api.FavoriteApi
import com.neko.music.data.manager.TokenManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteScreen(
    onBackClick: () -> Unit
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
            playlistManager.addToPlaylist(favorite.toMusic())
        }

        if (filteredFavorites.isNotEmpty()) {
            val firstFavorite = filteredFavorites[0]
            val music = firstFavorite.toMusic()
            val url = musicApi.getMusicFileUrl(music)
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

    suspend fun playFavorite(favorite: com.neko.music.data.api.FavoriteMusic) {
        val music = favorite.toMusic()
        playlistManager.addToPlaylist(music)
        val url = musicApi.getMusicFileUrl(music)
        val fullCoverUrl = if (favorite.cover.isNotEmpty()) {
            UrlConfig.buildFullUrl(favorite.cover)
        } else {
            UrlConfig.getMusicCoverUrl(favorite.id)
        }
        playerManager.playMusic(
            url,
            favorite.id,
            favorite.title,
            favorite.artist,
            favorite.cover,
            fullCoverUrl
        )
    }

    val removeFavorite: (com.neko.music.data.api.FavoriteMusic) -> Unit = { favorite ->
        scope.launch {
            val token = tokenManager.getToken()
            if (token != null) {
                val response = favoriteApi.removeFavorite(token, favorite.id)
                if (response.success) {
                    favorites = favorites.filterNot { it.id == favorite.id }
                    Toast.makeText(
                        context,
                        context.getString(R.string.remove_favorite_success),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(context, response.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val isDarkTheme = isAppDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val liquidGlass = LiquidGlassDefaults.playlistDetail
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)

    val collectionCoverUrl = remember(favorites) {
        val firstFavorite = favorites.firstOrNull()
        when {
            firstFavorite == null -> ""
            firstFavorite.cover.isNotEmpty() -> UrlConfig.buildFullUrl(firstFavorite.cover)
            else -> UrlConfig.getMusicCoverUrl(firstFavorite.id)
        }
    }

    val addAllFavoritesToQueue: () -> Unit = {
        if (filteredFavorites.isNotEmpty()) {
            scope.launch {
                filteredFavorites.forEach { favorite ->
                    playlistManager.addToPlaylist(favorite.toMusic())
                }
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.playlist_added_to_queue_format,
                        filteredFavorites.size
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
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

        CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(id = R.string.back),
                            tint = if (isDarkTheme) {
                                Color(0xFFB8B8D1).copy(alpha = 0.9f)
                            } else {
                                Color.Black
                            }
                        )
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.my_favorites),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) {
                                Color(0xFFF0F0F5).copy(alpha = 0.95f)
                            } else {
                                Color.Black
                            },
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                if (isLoggedIn && !isLoading && errorMessage.isEmpty() && favorites.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PlaylistSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        liquidGlass = liquidGlass,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }

                when {
                    !isLoggedIn -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(id = R.string.please_login_first),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkTheme) Color.White.copy(alpha = 0.8f) else scheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(id = R.string.login_after_view),
                                    fontSize = 14.sp,
                                    color = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else scheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = RoseRed)
                        }
                    }
                    errorMessage.isNotEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = errorMessage,
                                fontSize = 16.sp,
                                color = if (isDarkTheme) Color(0xFFFF6B6B) else Color.Red
                            )
                        }
                    }
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isDarkTheme) {
                                                Color(0xFF252545).copy(alpha = 0.6f)
                                            } else {
                                                Color(0xFFF5F5F5)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = collectionCoverUrl,
                                        contentDescription = stringResource(id = R.string.my_favorites),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        placeholder = painterResource(R.drawable.music),
                                        error = painterResource(R.drawable.music)
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.my_favorites_label),
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkTheme) {
                                            Color(0xFFF0F0F5).copy(alpha = 0.95f)
                                        } else {
                                            Color.Black
                                        },
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = stringResource(R.string.songs_count_label, favorites.size),
                                        fontSize = 14.sp,
                                        color = if (isDarkTheme) {
                                            Color(0xFFB8B8D1).copy(alpha = 0.8f)
                                        } else {
                                            Color.Gray
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { scope.launch { playAllFavorites() } },
                                    enabled = filteredFavorites.isNotEmpty(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = RoseRed,
                                        disabledContainerColor = RoseRed.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(21.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White.copy(
                                            alpha = if (filteredFavorites.isEmpty()) 0.5f else 0.95f
                                        ),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = stringResource(id = R.string.play_all),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White.copy(
                                            alpha = if (filteredFavorites.isEmpty()) 0.5f else 0.95f
                                        ),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }

                                PlaylistAddToQueueButton(
                                    enabled = filteredFavorites.isNotEmpty(),
                                    onClick = addAllFavoritesToQueue,
                                    liquidGlass = liquidGlass,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            if (filteredFavorites.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = if (searchQuery.isBlank()) {
                                                stringResource(id = R.string.no_favorites)
                                            } else {
                                                stringResource(id = R.string.no_search_results)
                                            },
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDarkTheme) {
                                                Color.White.copy(alpha = 0.68f)
                                            } else {
                                                scheme.onSurface
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = if (searchQuery.isBlank()) {
                                                stringResource(id = R.string.go_discover)
                                            } else {
                                                stringResource(id = R.string.try_other_keywords)
                                            },
                                            fontSize = 14.sp,
                                            color = if (isDarkTheme) {
                                                Color.White.copy(alpha = 0.45f)
                                            } else {
                                                scheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 16.dp,
                                        bottom = 150.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    itemsIndexed(
                                        items = filteredFavorites,
                                        key = { _, favorite -> favorite.id }
                                    ) { _, favorite ->
                                        FavoriteItem(
                                            music = favorite,
                                            position = favorites.indexOfFirst { it.id == favorite.id } + 1,
                                            onClick = { scope.launch { playFavorite(favorite) } },
                                            onPlayNext = { playerManager.playNext(favorite.toMusic()) },
                                            onRemove = { removeFavorite(favorite) },
                                            liquidGlass = liquidGlass
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
    position: Int,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onRemove: () -> Unit,
    liquidGlass: com.neko.music.ui.components.PlaylistDetailLiquidGlassParams = LiquidGlassDefaults.playlistDetail,
) {
    val coverUrl = remember(music.cover) {
        if (!music.cover.isNullOrEmpty()) {
            UrlConfig.buildFullUrl("${music.cover}")
        } else {
            UrlConfig.getMusicCoverUrl(music.id)
        }
    }

    val isDarkTheme = isAppDarkTheme()
    val scheme = MaterialTheme.colorScheme
    var actionsExpanded by remember { mutableStateOf(false) }
    val playNextText = stringResource(id = R.string.play_next)
    val moreActionsText = stringResource(id = R.string.more)
    val removeFavoriteText = stringResource(id = R.string.unfavorite_song)

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .combinedClickable(
                interactionSource = remember(music.id) { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
                onLongClick = { actionsExpanded = true },
            ),
        shape = RoundedCornerShape(liquidGlass.musicRowCornerDp),
        backgroundAlpha = if (isDarkTheme) {
            liquidGlass.musicRowBackgroundAlphaDark
        } else {
            liquidGlass.musicRowBackgroundAlphaLight
        },
        borderAlpha = if (isDarkTheme) {
            liquidGlass.musicRowBorderAlphaDark
        } else {
            liquidGlass.musicRowBorderAlphaLight
        },
        highlightAlpha = if (isDarkTheme) {
            liquidGlass.musicRowHighlightAlphaDark
        } else {
            liquidGlass.musicRowHighlightAlphaLight
        },
        borderColor = if (isDarkTheme) Color.White else scheme.outline,
        liquidBlur = liquidGlass.musicRowLiquidBlur,
        liquidLensHeight = liquidGlass.musicRowLiquidLensHeight,
        liquidLensAmount = liquidGlass.musicRowLiquidLensAmount
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "$position",
                fontSize = 14.sp,
                color = if (isDarkTheme) {
                    Color(0xFFB8B8D1).copy(alpha = 0.8f)
                } else {
                    Color.Gray
                },
                modifier = Modifier.width(24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isDarkTheme) {
                            Color(0xFF353558).copy(alpha = 0.6f)
                        } else {
                            Color(0xFFE0E0E0)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                coil3.compose.AsyncImage(
                    model = coverUrl,
                    contentDescription = stringResource(id = R.string.cover),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.music),
                    error = painterResource(R.drawable.music)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = music.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDarkTheme) {
                        Color(0xFFF0F0F5).copy(alpha = 0.95f)
                    } else {
                        Color.Black
                    },
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = music.artist,
                    fontSize = 13.sp,
                    color = if (isDarkTheme) {
                        Color(0xFFB8B8D1).copy(alpha = 0.8f)
                    } else {
                        Color.Gray
                    },
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            Text(
                text = formatDuration(music.duration),
                fontSize = 13.sp,
                color = if (isDarkTheme) {
                    Color(0xFFB8B8D1).copy(alpha = 0.8f)
                } else {
                    Color.Gray
                }
            )

            Box {
                IconButton(
                    onClick = { actionsExpanded = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = moreActionsText,
                        tint = if (isDarkTheme) {
                            Color(0xFFB8B8D1).copy(alpha = 0.85f)
                        } else {
                            Color.Gray
                        }
                    )
                }
                DropdownMenu(
                    expanded = actionsExpanded,
                    onDismissRequest = { actionsExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(playNextText, fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            actionsExpanded = false
                            onPlayNext()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(removeFavoriteText, fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            actionsExpanded = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%d:%02d", minutes, remainingSeconds)
}

private fun com.neko.music.data.api.FavoriteMusic.toMusic(): com.neko.music.data.model.Music =
    com.neko.music.data.model.Music(
        id = id,
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        filePath = filename,
        coverFilePath = cover,
        uploadUserId = 0,
        createdAt = ""
    )
