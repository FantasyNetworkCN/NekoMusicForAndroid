package com.neko.music.ui.screens

import android.util.Log
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.neko.music.R
import coil3.compose.AsyncImage
import com.neko.music.data.api.MusicApi
import com.neko.music.data.manager.SearchHistoryManager
import com.neko.music.data.model.Music
import com.neko.music.data.model.SearchHistory
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.PlaylistPageDarkTintOverlay
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.components.AppPageBackgroundImage
import com.neko.music.ui.theme.isAppDarkTheme
import com.neko.music.ui.search.SearchLiquidBarState
import com.neko.music.ui.search.SearchLiquidTopOverlay
import com.neko.music.ui.components.LrcBadge
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.LightRose
import com.neko.music.util.UrlConfig
import com.neko.music.util.installNekoClientHeader
import com.neko.music.util.preferHttp2AlpnOverHttp1
import com.neko.music.util.protocolLogSuffix
import com.neko.music.util.protocolLogSuffixOrEmpty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.utils.EmptyContent.headers
import io.ktor.http.headers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun SearchResultScreen(
    liquidBarState: SearchLiquidBarState,
    initialQuery: String = "",
    onBackClick: () -> Unit,
    onMusicClick: (Music) -> Unit,
    onPlaylistClick: (Int, String, String?, String?, String?, Int?) -> Unit = { _, _, _, _, _, _ -> },
    onArtistClick: (String, Int, String?) -> Unit = { _, _, _ -> },
    /** 「下一首播放」：加入播放列表并强制排在当前歌曲之后 */
    onPlayNext: (Music) -> Unit = {}
) {
    val searchFailedText = stringResource(id = R.string.search_failed)
    val noSearchMusicText = stringResource(id = R.string.no_search_music)
    val noSearchPlaylistText = stringResource(id = R.string.no_search_playlist)
    val noSearchArtistText = stringResource(id = R.string.no_search_artist)
    val searchErrorHintText = stringResource(id = R.string.search_error_hint)
    val searchEmptyHintText = stringResource(id = R.string.search_empty_hint)
    val searchHistoryEmptyTitleText = stringResource(id = R.string.search_history_empty_title)
    val searchHistoryEmptyHintText = stringResource(id = R.string.search_history_empty_hint)
    
    val context = LocalContext.current
    val historyManager = remember { SearchHistoryManager(context) }
    val searchStatePrefs = remember { context.getSharedPreferences("search_state", android.content.Context.MODE_PRIVATE) }
    var searchResults by remember { mutableStateOf<List<Music>>(emptyList()) }
    var playlistResults by remember { mutableStateOf<List<com.neko.music.data.api.PlaylistInfo>>(emptyList()) }
    var artistResults by remember { mutableStateOf<List<com.neko.music.data.model.Artist>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchHistory by remember { mutableStateOf<List<SearchHistory>>(emptyList()) }
    
    val musicApi = remember { MusicApi(context) }
    
    androidx.compose.runtime.LaunchedEffect(initialQuery) {
        searchHistory = historyManager.getSearchHistory()
        if (initialQuery.isNotEmpty()) {
            liquidBarState.searchQuery = initialQuery
        } else {
            liquidBarState.searchQuery = searchStatePrefs.getString("last_search_query", "") ?: ""
            liquidBarState.searchType = searchStatePrefs.getString("last_search_type", "music") ?: "music"
        }
    }

    val query = liquidBarState.searchQuery
    val searchType = liquidBarState.searchType
    androidx.compose.runtime.LaunchedEffect(query, searchType) {
        searchStatePrefs.edit()
            .putString("last_search_query", query)
            .putString("last_search_type", searchType)
            .apply()
        if (query.isEmpty()) {
            searchResults = emptyList()
            playlistResults = emptyList()
            artistResults = emptyList()
            errorMessage = null
            isLoading = false
            return@LaunchedEffect
        }

        errorMessage = null

        delay(300)
        if (!currentCoroutineContext().isActive) return@LaunchedEffect

        searchResults = emptyList()
        playlistResults = emptyList()
        artistResults = emptyList()
        isLoading = true

        Log.d("SearchScreen", "实时搜索: $query, 类型: $searchType")
        val requestContext = currentCoroutineContext()
        val isCurrentRequest = {
            requestContext.isActive &&
                query == liquidBarState.searchQuery &&
                searchType == liquidBarState.searchType
        }

        when (searchType) {
            "music" -> {
                val (results, error) = performSearch(musicApi, query)
                if (!isCurrentRequest()) return@LaunchedEffect
                searchResults = results
                playlistResults = emptyList()
                artistResults = emptyList()
                errorMessage = error
            }
            "playlist" -> {
                val (results, error) = performPlaylistSearch(query, context)
                if (!isCurrentRequest()) return@LaunchedEffect
                playlistResults = results
                searchResults = emptyList()
                artistResults = emptyList()
                errorMessage = error
            }
            else -> {
                val (results, error) = performArtistSearch(query, context)
                if (!isCurrentRequest()) return@LaunchedEffect
                artistResults = results
                searchResults = emptyList()
                playlistResults = emptyList()
                errorMessage = error
            }
        }
        isLoading = false
    }

    SideEffect {
        liquidBarState.onImeSearchAction = {
            val q = liquidBarState.searchQuery
            if (q.isNotEmpty()) {
                historyManager.saveSearch(q)
                searchHistory = historyManager.getSearchHistory()
            }
        }
    }

    val scheme = MaterialTheme.colorScheme
    val isDark = isAppDarkTheme()
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)
    var barInsetPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val topInsetDp = remember(barInsetPx, density) {
        if (barInsetPx > 0) with(density) { barInsetPx.toDp() } else 148.dp
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
            PlaylistPageDarkTintOverlay(enabled = isDark)
        }
        CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topInsetDp + 35.dp)
            ) {
                when {
                    isLoading -> {
                        SearchLoadingState()
                    }
                    errorMessage != null -> {
                        SearchStatePanel(
                            icon = Icons.Default.Info,
                            title = searchFailedText,
                            subtitle = errorMessage ?: searchErrorHintText,
                            accent = scheme.error,
                        )
                    }
                    searchResults.isEmpty() && playlistResults.isEmpty() && artistResults.isEmpty() && liquidBarState.searchQuery.isEmpty() && searchHistory.isNotEmpty() -> {
                        SearchHistoryList(
                            history = searchHistory,
                            onItemClick = { query ->
                                liquidBarState.searchQuery = query
                            },
                            onClearClick = {
                                historyManager.clearHistory()
                                searchHistory = emptyList()
                            }
                        )
                    }
                    searchResults.isEmpty() && playlistResults.isEmpty() && artistResults.isEmpty() && liquidBarState.searchQuery.isNotEmpty() -> {
                        SearchStatePanel(
                            icon = Icons.Default.Search,
                            title = when (liquidBarState.searchType) {
                                "music" -> noSearchMusicText
                                "playlist" -> noSearchPlaylistText
                                else -> noSearchArtistText
                            },
                            subtitle = searchEmptyHintText,
                            accent = if (isDark) LightRose else RoseRed,
                        )
                    }
                    searchResults.isEmpty() && playlistResults.isEmpty() && liquidBarState.searchQuery.isEmpty() && searchHistory.isEmpty() -> {
                        SearchStatePanel(
                            icon = Icons.Default.Search,
                            title = searchHistoryEmptyTitleText,
                            subtitle = searchHistoryEmptyHintText,
                            accent = if (isDark) LightRose else RoseRed,
                        )
                    }
                    else -> {
                        if (liquidBarState.searchType == "music") {
                            MusicList(
                                musics = searchResults,
                                onMusicClick = { music ->
                                    historyManager.addSearchHistory(music.title, liquidBarState.searchQuery)
                                    onMusicClick(music)
                                },
                                onPlayNext = onPlayNext
                            )
                        } else if (liquidBarState.searchType == "playlist") {
                            PlaylistList(
                                playlists = playlistResults,
                                onPlaylistClick = { playlist ->
                                    onPlaylistClick(playlist.id, playlist.name, playlist.coverPath, playlist.description, playlist.nickname, playlist.userId)
                                }
                            )
                        } else {
                            ArtistList(
                                artists = artistResults,
                                onArtistClick = { artist ->
                                    onArtistClick(artist.name, artist.musicCount, artist.coverPath)
                                }
                            )
                        }
                    }
                }
            }
        }
        SearchLiquidTopOverlay(
            state = liquidBarState,
            onBackClick = onBackClick,
            onBarHeightChanged = { barInsetPx = it },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .zIndex(2f),
            sampleBackdrop = pageBackdrop
        )
    }
}

@Composable
fun PlaylistList(
    playlists: List<com.neko.music.data.api.PlaylistInfo>,
    onPlaylistClick: (com.neko.music.data.api.PlaylistInfo) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 8.dp,
            end = 20.dp,
            bottom = 160.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SearchResultsHeader(count = playlists.size) }
        items(playlists) { playlist ->
            PlaylistItem(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist) },
            )
        }
    }
}

@Composable
fun PlaylistItem(
    playlist: com.neko.music.data.api.PlaylistInfo,
    onClick: () -> Unit
) {
    val playlistCoverText = stringResource(id = R.string.playlist_cover)
    val playlistText = stringResource(id = R.string.playlist)
    val noDescriptionNyaText = stringResource(id = R.string.no_description_nya)
    val songsCountFormatText = stringResource(id = R.string.songs_count, playlist.musicCount)
    val scheme = MaterialTheme.colorScheme
    val isDarkTheme = isAppDarkTheme()
    val listTint = LiquidGlassDefaults.screenListCard
    val (interactionSource, pressScale) = rememberSearchPressScale()

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(18.dp),
        backgroundAlpha = listTint.background(isDarkTheme),
        borderAlpha = listTint.border(isDarkTheme),
        highlightAlpha = listTint.highlight(isDarkTheme),
        borderColor = if (isDarkTheme) Color.White.copy(alpha = 0.14f) else scheme.outline,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!playlist.coverPath.isNullOrEmpty()) {
                AsyncImage(
                    model = UrlConfig.buildFullUrl(playlist.coverPath),
                    contentDescription = playlistCoverText,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            color = (if (isDarkTheme) LightRose else RoseRed).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(14.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = playlistText,
                        tint = if (isDarkTheme) LightRose else RoseRed,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = playlist.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = playlist.description ?: noDescriptionNyaText,
                    fontSize = 12.sp,
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = songsCountFormatText,
                    fontSize = 11.sp,
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.68f) else scheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@Composable
fun MusicList(
    musics: List<Music>,
    onMusicClick: (Music) -> Unit,
    onPlayNext: (Music) -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SearchResultsHeader(count = musics.size) }
        items(musics) { music ->
            MusicItem(
                music = music,
                onClick = { onMusicClick(music) },
                onPlayNext = { onPlayNext(music) },
            )
        }
    }
}

@Composable
fun MusicItem(
    music: Music,
    onClick: () -> Unit,
    onPlayNext: () -> Unit = {}
) {
    val coverText = stringResource(id = R.string.content_description_cover)
    val playNextText = stringResource(id = R.string.play_next)
    val moreActionsText = stringResource(id = R.string.more)
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val musicApi = remember { MusicApi(context) }
    var coverUrl by remember { mutableStateOf<String?>(null) }
    var actionsExpanded by remember { mutableStateOf(false) }
    val isDarkTheme = isAppDarkTheme()
    val (interactionSource, pressScale) = rememberSearchPressScale()

    LaunchedEffect(music.id) {
        coverUrl = musicApi.getMusicCoverUrl(music)
    }

    val listTint = LiquidGlassDefaults.screenListCard
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .scale(pressScale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(18.dp),
        backgroundAlpha = listTint.background(isDarkTheme),
        borderAlpha = listTint.border(isDarkTheme),
        highlightAlpha = listTint.highlight(isDarkTheme),
        borderColor = if (isDarkTheme) Color.White.copy(alpha = 0.14f) else scheme.outline,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        color = (if (isDarkTheme) LightRose else RoseRed).copy(alpha = 0.10f),
                        shape = RoundedCornerShape(13.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (!coverUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = coverText,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(13.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.music),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = music.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    LrcBadge(
                        visible = music.lrc,
                        tint = if (isDarkTheme) Color(0xFFB8A0FF) else Color(0xFF9370DB),
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = music.artist,
                    fontSize = 13.sp,
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }

            Box {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isDarkTheme) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                        .clickable { actionsExpanded = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = moreActionsText,
                        tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.85f) else scheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp),
                    )
                }
                DropdownMenu(
                    expanded = actionsExpanded,
                    onDismissRequest = { actionsExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(playNextText, fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            actionsExpanded = false
                            onPlayNext()
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SearchHistoryList(
    history: List<SearchHistory>,
    onItemClick: (String) -> Unit,
    onClearClick: () -> Unit
) {
    val searchHistoryText = stringResource(id = R.string.search_history)
    val clearHistoryText = stringResource(id = R.string.clear_history)
    val scheme = MaterialTheme.colorScheme
    val isDarkTheme = isAppDarkTheme()
    val accent = if (isDarkTheme) LightRose else RoseRed
    val tint = LiquidGlassDefaults.homeHeroShortcutCard
    val titleColor = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface
    val mutedColor = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else scheme.onSurfaceVariant

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 220.dp),
    ) {
        item(key = "history_group") {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                backgroundAlpha = tint.tint.background(isDarkTheme),
                borderAlpha = tint.tint.border(isDarkTheme),
                highlightAlpha = tint.tint.highlight(isDarkTheme),
                borderColor = if (isDarkTheme) Color.White.copy(alpha = 0.14f) else scheme.outline,
                liquidBlur = tint.liquid.blur,
                liquidLensHeight = tint.liquid.lensHeight,
                liquidLensAmount = tint.liquid.lensAmount,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, top = 14.dp, end = 8.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = searchHistoryText,
                            color = titleColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = clearHistoryText,
                            color = accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onClearClick)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                    history.forEachIndexed { index, item ->
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 48.dp)
                                    .height(0.5.dp)
                                    .background(mutedColor.copy(alpha = 0.14f)),
                            )
                        }
                        SearchHistoryRow(
                            query = item.query,
                            titleColor = titleColor,
                            mutedColor = mutedColor,
                            onClick = { onItemClick(item.query) },
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchHistoryRow(
    query: String,
    titleColor: Color,
    mutedColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = mutedColor.copy(alpha = 0.72f),
            modifier = Modifier.size(17.dp),
        )
        Spacer(modifier = Modifier.width(13.dp))
        Text(
            text = query,
            color = titleColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SearchResultsHeader(count: Int) {
    val scheme = MaterialTheme.colorScheme
    val isDarkTheme = isAppDarkTheme()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = 8.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.search_results_title),
            color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
        )
        Text(
            text = stringResource(R.string.search_result_count, count),
            color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.78f) else scheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SearchStatePanel(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
) {
    val scheme = MaterialTheme.colorScheme
    val isDarkTheme = isAppDarkTheme()
    val tint = LiquidGlassDefaults.homeHeroShortcutCard
    val titleColor = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface
    val mutedColor = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.82f) else scheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            backgroundAlpha = tint.tint.background(isDarkTheme),
            borderAlpha = tint.tint.border(isDarkTheme),
            highlightAlpha = tint.tint.highlight(isDarkTheme),
            borderColor = if (isDarkTheme) accent.copy(alpha = 0.42f) else scheme.outline,
            liquidBlur = tint.liquid.blur,
            liquidLensHeight = tint.liquid.lensHeight,
            liquidLensAmount = tint.liquid.lensAmount,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = if (isDarkTheme) 0.16f else 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(25.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    color = mutedColor,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SearchLoadingState() {
    val scheme = MaterialTheme.colorScheme
    val isDarkTheme = isAppDarkTheme()
    val tint = LiquidGlassDefaults.screenListCard
    val mutedColor = if (isDarkTheme) Color(0xFFB8B8D1) else scheme.onSurfaceVariant
    val transition = rememberInfiniteTransition(label = "searchSkeleton")
    val shimmer by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.86f,
        animationSpec = infiniteRepeatable(
            animation = tween(820),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "searchSkeletonAlpha",
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.search_loading),
                color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.86f) else scheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
            )
        }
        items(5) { index ->
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .alpha(shimmer),
                shape = RoundedCornerShape(18.dp),
                backgroundAlpha = tint.background(isDarkTheme),
                borderAlpha = tint.border(isDarkTheme),
                highlightAlpha = tint.highlight(isDarkTheme),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(mutedColor.copy(alpha = 0.12f)),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (index % 2 == 0) 0.58f else 0.72f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(mutedColor.copy(alpha = 0.15f)),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (index % 2 == 0) 0.38f else 0.46f)
                                .height(9.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(mutedColor.copy(alpha = 0.10f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberSearchPressScale(): Pair<MutableInteractionSource, Float> {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "searchRowPress",
    )
    return interactionSource to scale
}

private suspend fun performSearch(
    api: MusicApi,
    query: String,
): Pair<List<Music>, String?> {
    return api.searchMusic(query).fold(
        onSuccess = { musics ->
            Log.d("SearchScreen", "请求成功 - 找到 ${musics.size} 条结果")
            musics to null
        },
        onFailure = { error ->
            Log.e("SearchScreen", "请求失败 - ${error.message}${error.protocolLogSuffixOrEmpty()}")
            emptyList<Music>() to error.message
        }
    )
}

private suspend fun performPlaylistSearch(
    query: String,
    context: android.content.Context,
): Pair<List<com.neko.music.data.api.PlaylistInfo>, String?> {
    return try {
            val client = HttpClient(OkHttp) {
                installNekoClientHeader()
                engine { config { preferHttp2AlpnOverHttp1() } }
            }
            val response = client.post("$baseUrl/api/playlists/search") {
                headers {
                    append("Content-Type", "application/json")
                }
                setBody(
                    """
                        {
                            "query": "$query"
                        }
                        """.trimIndent()
                )
            }
            
            val responseText = response.body<String>()
            Log.d("SearchScreen", "歌单搜索响应: $responseText${response.protocolLogSuffix()}")
            
            // 简单解析 JSON 响应
            if (responseText.contains("\"success\":true")) {
                // 提取 results 数组
                val resultsRegex = """"results":\s*\[(.*?)\]""".toRegex()
                val match = resultsRegex.find(responseText)
                if (match != null) {
                    val resultsJson = match.groupValues[1]
                    // 简化处理：从 JSON 中提取歌单信息
                    val playlists = mutableListOf<com.neko.music.data.api.PlaylistInfo>()
                    // 匹配完整的歌单信息，包括 firstMusicId 和 firstMusicCover
                    val playlistRegex = """"id":\s*(\d+),\s*"userId":\s*(\d+),\s*"name":\s*"([^"]*)"(?:,\s*"description":\s*"([^"]*)")?,\s*"musicCount":\s*(\d+).*?,"firstMusicId":\s*(\d+)""".toRegex()
                    playlistRegex.findAll(resultsJson).forEach { matchResult ->
                        val id = matchResult.groupValues[1].toIntOrNull() ?: 0
                        val userId = matchResult.groupValues[2].toIntOrNull() ?: 0
                        val name = matchResult.groupValues[3]
                        val description = matchResult.groupValues[4].ifBlank { null }
                        val musicCount = matchResult.groupValues[5].toIntOrNull() ?: 0
                        val firstMusicId = matchResult.groupValues[6].toIntOrNull() ?: 0
                        
                        playlists.add(
                            com.neko.music.data.api.PlaylistInfo(
                                id = id,
                                name = name,
                                description = description,
                                coverPath = "$baseUrl/api/music/cover/$firstMusicId",
                                musicCount = musicCount,
                                createdAt = "",
                                updatedAt = "",
                                userId = userId
                            )
                        )
                    }
                    
                    Log.d("SearchScreen", "搜索到 ${playlists.size} 个歌单${response.protocolLogSuffix()}")
                    playlists to null
                } else {
                    emptyList<com.neko.music.data.api.PlaylistInfo>() to
                        context.getString(R.string.no_search_playlist_found)
                }
            } else {
                emptyList<com.neko.music.data.api.PlaylistInfo>() to context.getString(R.string.search_failed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("SearchScreen", "歌单搜索请求失败 - ${e.message}${e.protocolLogSuffixOrEmpty()}", e)
            emptyList<com.neko.music.data.api.PlaylistInfo>() to e.message
        }
}

private suspend fun performArtistSearch(
    query: String,
    context: android.content.Context,
): Pair<List<com.neko.music.data.model.Artist>, String?> {
    return try {
            val client = HttpClient(OkHttp) {
                installNekoClientHeader()
                engine { config { preferHttp2AlpnOverHttp1() } }
            }
            val response = client.post("$baseUrl/api/artists/search") {
                headers {
                    append("Content-Type", "application/json")
                }
                setBody(
                    """
                        {
                            "query": "$query"
                        }
                        """.trimIndent()
                )
            }
            
            val responseText = response.body<String>()
            Log.d("SearchScreen", "歌手搜索响应: $responseText${response.protocolLogSuffix()}")
            
            // 解析 JSON 响应 - 新格式返回单个歌手及其音乐列表
            if (responseText.contains("\"success\":true")) {
                // 提取 artist 对象
                val artistRegex = """"artist":\s*\{([^}]*)\}""".toRegex()
                val artistMatch = artistRegex.find(responseText)
                
                if (artistMatch != null) {
                    val artistJson = artistMatch.groupValues[1]
                    val artists = mutableListOf<com.neko.music.data.model.Artist>()
                    
                    // 匹配歌手信息：name, musicCount
                    val nameMatch = """"name":\s*"([^"]*)"""".toRegex().find(artistJson)
                    val musicCountMatch = """"musicCount":\s*(\d+)""".toRegex().find(artistJson)
                    
                    val name = nameMatch?.groupValues?.get(1) ?: ""
                    val musicCount = musicCountMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    
                    // 提取第一首音乐的封面作为歌手封面
                    val musicListRegex = """"musicList":\s*\[([^\]]*)\]""".toRegex()
                    val musicListMatch = musicListRegex.find(responseText)
                    var coverPath: String? = null
                    
                    if (musicListMatch != null && musicCount > 0) {
                        val musicListJson = musicListMatch.groupValues[1]
                        Log.d("SearchScreen", "音乐列表JSON: $musicListJson${response.protocolLogSuffix()}")
                        // 匹配第一首音乐的封面
                        val coverMatch = """"coverPath":\s*"([^"]*)"""".toRegex().find(musicListJson)
                        val extractedCover = coverMatch?.groupValues?.get(1)
                        Log.d("SearchScreen", "提取到的封面路径: $extractedCover${response.protocolLogSuffix()}")
                        
                        if (!extractedCover.isNullOrEmpty() && extractedCover != "/api/music/cover/" && extractedCover != "/api/music/cover") {
                            coverPath = "$baseUrl$extractedCover"
                        }
                    }
                    
                    Log.d("SearchScreen", "歌手封面最终路径: $coverPath${response.protocolLogSuffix()}")
                    
                    if (name.isNotEmpty()) {
                        artists.add(
                            com.neko.music.data.model.Artist(
                                name = name,
                                musicCount = musicCount,
                                coverPath = coverPath
                            )
                        )
                    }
                    
                    Log.d("SearchScreen", "搜索到 ${artists.size} 个歌手: $name${response.protocolLogSuffix()}")
                    artists to null
                } else {
                    emptyList<com.neko.music.data.model.Artist>() to
                        context.getString(R.string.no_search_artist_found)
                }
            } else {
                emptyList<com.neko.music.data.model.Artist>() to context.getString(R.string.search_failed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("SearchScreen", "歌手搜索请求失败 - ${e.message}${e.protocolLogSuffixOrEmpty()}", e)
            emptyList<com.neko.music.data.model.Artist>() to e.message
        }
}

@Composable
fun ArtistList(
    artists: List<com.neko.music.data.model.Artist>,
    onArtistClick: (com.neko.music.data.model.Artist) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 8.dp,
            end = 20.dp,
            bottom = 160.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SearchResultsHeader(count = artists.size) }
        items(artists) { artist ->
            ArtistItem(
                artist = artist,
                onClick = { onArtistClick(artist) },
            )
        }
    }
}

@Composable
fun ArtistItem(
    artist: com.neko.music.data.model.Artist,
    onClick: () -> Unit
) {
    val songsCountSuffixText = stringResource(id = R.string.songs_count_suffix, artist.musicCount)
    val scheme = MaterialTheme.colorScheme
    val isDarkTheme = isAppDarkTheme()
    val listTint = LiquidGlassDefaults.screenListCard
    val (interactionSource, pressScale) = rememberSearchPressScale()

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(18.dp),
        backgroundAlpha = listTint.background(isDarkTheme),
        borderAlpha = listTint.border(isDarkTheme),
        highlightAlpha = listTint.highlight(isDarkTheme),
        borderColor = if (isDarkTheme) Color.White.copy(alpha = 0.14f) else scheme.outline,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!artist.coverPath.isNullOrBlank()) {
                AsyncImage(
                    model = UrlConfig.buildFullUrl(artist.coverPath),
                    contentDescription = artist.name,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background((if (isDarkTheme) LightRose else RoseRed).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (isDarkTheme) LightRose else RoseRed,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = artist.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = songsCountSuffixText,
                    fontSize = 12.sp,
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                )
            }
        }
    }
}
