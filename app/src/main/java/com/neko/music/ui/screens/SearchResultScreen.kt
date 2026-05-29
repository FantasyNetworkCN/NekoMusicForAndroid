package com.neko.music.ui.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width as composeWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.neko.music.ui.theme.isAppDarkTheme
import com.neko.music.ui.search.SearchLiquidBarState
import com.neko.music.ui.search.SearchLiquidTopOverlay
import com.neko.music.ui.theme.RoseRed
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
import kotlinx.coroutines.launch

@Composable
fun SearchResultScreen(
    liquidBarState: SearchLiquidBarState,
    initialQuery: String = "",
    onBackClick: () -> Unit,
    onMusicClick: (Music) -> Unit,
    onPlaylistClick: (Int, String, String?, String?, String?, Int?) -> Unit = { _, _, _, _, _, _ -> },
    onArtistClick: (String, Int, String?) -> Unit = { _, _, _ -> }
) {
    val searchFailedText = stringResource(id = R.string.search_failed)
    val noSearchMusicText = stringResource(id = R.string.no_search_music)
    val noSearchPlaylistText = stringResource(id = R.string.no_search_playlist)
    val noSearchArtistText = stringResource(id = R.string.no_search_artist)
    val noSearchHistoryYetText = stringResource(id = R.string.no_search_history_yet)
    val noDescriptionNyaText = stringResource(id = R.string.no_description_nya)
    val songsCountText = stringResource(id = R.string.songs_count, 0)
    val authorPrefixText = stringResource(id = R.string.author_prefix, "")
    val songsCountSuffixText = stringResource(id = R.string.songs_count_suffix, 0)
    
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
    val scope = rememberCoroutineScope()
    
    androidx.compose.runtime.LaunchedEffect(initialQuery) {
        searchHistory = historyManager.getSearchHistory()
        if (initialQuery.isNotEmpty()) {
            liquidBarState.searchQuery = initialQuery
        } else {
            liquidBarState.searchQuery = searchStatePrefs.getString("last_search_query", "") ?: ""
            liquidBarState.searchType = searchStatePrefs.getString("last_search_type", "music") ?: "music"
        }
    }

    androidx.compose.runtime.LaunchedEffect(liquidBarState.searchQuery, liquidBarState.searchType) {
        searchStatePrefs.edit()
            .putString("last_search_query", liquidBarState.searchQuery)
            .putString("last_search_type", liquidBarState.searchType)
            .apply()
        if (liquidBarState.searchQuery.isNotEmpty()) {
            Log.d("SearchScreen", "实时搜索: ${liquidBarState.searchQuery}, 类型: ${liquidBarState.searchType}")
            isLoading = true
            when (liquidBarState.searchType) {
                "music" -> performSearch(musicApi, liquidBarState.searchQuery, scope) { results, error ->
                    searchResults = results
                    playlistResults = emptyList()
                    artistResults = emptyList()
                    isLoading = false
                    errorMessage = error
                }
                "playlist" -> performPlaylistSearch(liquidBarState.searchQuery, context, scope) { results, error ->
                    playlistResults = results
                    searchResults = emptyList()
                    artistResults = emptyList()
                    isLoading = false
                    errorMessage = error
                }
                else -> performArtistSearch(liquidBarState.searchQuery, context, scope) { results, error ->
                    artistResults = results
                    searchResults = emptyList()
                    playlistResults = emptyList()
                    isLoading = false
                    errorMessage = error
                }
            }
        } else {
            searchResults = emptyList()
            playlistResults = emptyList()
            artistResults = emptyList()
        }
    }

    SideEffect {
        liquidBarState.onImeSearchAction = {
            val q = liquidBarState.searchQuery
            if (q.isNotEmpty()) {
                historyManager.saveSearch(q)
                searchHistory = historyManager.getSearchHistory()
                isLoading = true
                scope.launch {
                    when (liquidBarState.searchType) {
                        "music" -> performSearch(musicApi, q, scope) { results, error ->
                            searchResults = results
                            playlistResults = emptyList()
                            artistResults = emptyList()
                            isLoading = false
                            errorMessage = error
                        }
                        "playlist" -> performPlaylistSearch(q, context, scope) { results, error ->
                            playlistResults = results
                            searchResults = emptyList()
                            artistResults = emptyList()
                            isLoading = false
                            errorMessage = error
                        }
                        else -> performArtistSearch(q, context, scope) { results, error ->
                            artistResults = results
                            searchResults = emptyList()
                            playlistResults = emptyList()
                            isLoading = false
                            errorMessage = error
                        }
                    }
                }
            }
        }
    }

    val scheme = MaterialTheme.colorScheme
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)
    var barInsetPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val topInsetDp = remember(barInsetPx, density) {
        if (barInsetPx > 0) with(density) { barInsetPx.toDp() } else 168.dp
    }

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
            PlaylistPageDarkTintOverlay()
        }
        CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topInsetDp + 20.dp)
            ) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    errorMessage != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = errorMessage ?: searchFailedText,
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                        }
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
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (liquidBarState.searchType) {
                                    "music" -> noSearchMusicText
                                    "playlist" -> noSearchPlaylistText
                                    else -> noSearchArtistText
                                },
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                    searchResults.isEmpty() && playlistResults.isEmpty() && liquidBarState.searchQuery.isEmpty() && searchHistory.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = noSearchHistoryYetText,
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                    else -> {
                        if (liquidBarState.searchType == "music") {
                            MusicList(
                                musics = searchResults,
                                onMusicClick = { music ->
                                    historyManager.addSearchHistory(music.title, liquidBarState.searchQuery)
                                    onMusicClick(music)
                                }
                            )
                        } else if (liquidBarState.searchType == "playlist") {
                            PlaylistList(
                                playlists = playlistResults,
                                onPlaylistClick = { playlist ->
                                    onPlaylistClick(playlist.id, playlist.name, playlist.coverPath, playlist.description, playlist.username, playlist.userId)
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(playlists) { playlist ->
            PlaylistItem(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist) }
            )
        }
    }
}

@Composable
fun PlaylistItem(
    playlist: com.neko.music.data.api.PlaylistInfo,
    onClick: () -> Unit
) {
    // Preload strings
    val playlistCoverText = stringResource(id = R.string.playlist_cover)
    val playlistText = stringResource(id = R.string.playlist)
    val noDescriptionNyaText = stringResource(id = R.string.no_description_nya)
    val songsCountFormatText = stringResource(id = R.string.songs_count, playlist.musicCount)
    
    val isDarkTheme = isAppDarkTheme()

    val listTint = LiquidGlassDefaults.screenListCard
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        backgroundAlpha = listTint.background(isDarkTheme),
        borderAlpha = listTint.border(isDarkTheme),
        highlightAlpha = listTint.highlight(isDarkTheme)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 封面
            if (!playlist.coverPath.isNullOrEmpty()) {
                coil3.compose.AsyncImage(
                    model = playlist.coverPath,
                    contentDescription = playlistCoverText,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                // 默认封面
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = RoseRed.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = playlistText,
                        tint = RoseRed,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // 歌单信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = playlist.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = playlist.description ?: noDescriptionNyaText,
                    fontSize = 12.sp,
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = songsCountFormatText,
                    fontSize = 11.sp,
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.7f) else Color.Gray
                )
            }
        }
    }
}

@Composable
fun MusicList(
    musics: List<Music>,
    onMusicClick: (Music) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(musics) { music ->
            MusicItem(
                music = music,
                onClick = { onMusicClick(music) }
            )
        }
    }
}

@Composable
fun MusicItem(
    music: Music,
    onClick: () -> Unit
) {
    // Preload strings
    val coverText = stringResource(id = R.string.content_description_cover)
    val authorPrefixText = stringResource(id = R.string.author_prefix, music.artist)
    
    val context = LocalContext.current
    val musicApi = remember { MusicApi(context) }
    val scope = rememberCoroutineScope()
    var coverUrl by remember { mutableStateOf<String?>(null) }
    val isDarkTheme = isAppDarkTheme()
    
    androidx.compose.runtime.LaunchedEffect(music.id) {
        scope.launch {
            coverUrl = musicApi.getMusicCoverUrl(music)
            Log.d("MusicItem", "封面URL: $coverUrl, music.coverFilePath: ${music.coverFilePath}")
        }
    }
    
    val listTint = LiquidGlassDefaults.screenListCard
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        backgroundAlpha = listTint.background(isDarkTheme),
        borderAlpha = listTint.border(isDarkTheme),
        highlightAlpha = listTint.highlight(isDarkTheme)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = RoseRed.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!coverUrl.isNullOrEmpty()) {
                    coil3.compose.AsyncImage(
                        model = coverUrl,
                        contentDescription = coverText,
                        modifier = Modifier.size(44.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.music),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.composeWidth(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = music.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = authorPrefixText,
                    fontSize = 13.sp,
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray,
                    maxLines = 1
                )
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
    // Preload strings
    val searchHistoryText = stringResource(id = R.string.search_history)
    val clearHistoryText = stringResource(id = R.string.clear_history)
    
    val isDarkTheme = isAppDarkTheme()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = searchHistoryText,
                    tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = searchHistoryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(
                onClick = onClearClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = clearHistoryText,
                    tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 220.dp)
        ) {
            items(history) { item ->
                val chipTint = LiquidGlassDefaults.searchHistoryRow
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clickable { onItemClick(item.query) },
                    shape = RoundedCornerShape(10.dp),
                    backgroundAlpha = chipTint.background(isDarkTheme),
                    borderAlpha = chipTint.border(isDarkTheme),
                    highlightAlpha = chipTint.highlight(isDarkTheme)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.query,
                            fontSize = 14.sp,
                            color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private fun performSearch(
    api: MusicApi,
    query: String,
    scope: kotlinx.coroutines.CoroutineScope,
    onResult: (List<Music>, String?) -> Unit
) {
    scope.launch {
        val result = api.searchMusic(query)
        result.fold(
            onSuccess = { musics ->
                Log.d("SearchScreen", "请求成功 - 找到 ${musics.size} 条结果")
                onResult(musics, null)
            },
onFailure = { error ->
                Log.e("SearchScreen", "请求失败 - ${error.message}${error.protocolLogSuffixOrEmpty()}")
                onResult(emptyList(), error.message)
            }
        )
    }
}

suspend fun performPlaylistSearch(
    query: String,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onResult: (List<com.neko.music.data.api.PlaylistInfo>, String?) -> Unit
) {
    scope.launch {
        try {
            val client = HttpClient(OkHttp) {
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
                    onResult(playlists, null)
                } else {
                    onResult(emptyList(), context.getString(R.string.no_search_playlist_found))
                }
            } else {
                onResult(emptyList(), context.getString(R.string.search_failed))
            }
        } catch (e: Exception) {
            Log.e("SearchScreen", "歌单搜索请求失败 - ${e.message}${e.protocolLogSuffixOrEmpty()}", e)
            onResult(emptyList(), e.message)
        }
    }
}

suspend fun performArtistSearch(
    query: String,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onResult: (List<com.neko.music.data.model.Artist>, String?) -> Unit
) {
    scope.launch {
        try {
            val client = HttpClient(OkHttp) {
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
                    onResult(artists, null)
                } else {
                    onResult(emptyList(), context.getString(R.string.no_search_artist_found))
                }
            } else {
                onResult(emptyList(), context.getString(R.string.search_failed))
            }
        } catch (e: Exception) {
            Log.e("SearchScreen", "歌手搜索请求失败 - ${e.message}${e.protocolLogSuffixOrEmpty()}", e)
            onResult(emptyList(), e.message)
        }
    }
}

@Composable
fun SearchTypeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isDarkTheme = isAppDarkTheme()

    Column(
        modifier = Modifier
            .height(36.dp)
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource()
            )
            .padding(horizontal = 16.dp, vertical = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = if (isSelected) {
                Color.White.copy(alpha = 0.98f)
            } else {
                if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            },
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .height(3.dp)
                .composeWidth(if (isSelected) 18.dp else 0.dp)
                .background(
                    color = Color.White.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(1.5.dp)
                )
        )
    }
}

@Composable
fun ArtistList(
    artists: List<com.neko.music.data.model.Artist>,
    onArtistClick: (com.neko.music.data.model.Artist) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 160.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(artists) { artist ->
            ArtistItem(
                artist = artist,
                onClick = { onArtistClick(artist) }
            )
        }
    }
}

@Composable
fun ArtistItem(
    artist: com.neko.music.data.model.Artist,
    onClick: () -> Unit
) {
    // Preload strings
    val songsCountSuffixText = stringResource(id = R.string.songs_count_suffix, artist.musicCount)
    
    val isDarkTheme = isAppDarkTheme()

    val listTint = LiquidGlassDefaults.screenListCard
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        backgroundAlpha = listTint.background(isDarkTheme),
        borderAlpha = listTint.border(isDarkTheme),
        highlightAlpha = listTint.highlight(isDarkTheme)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 歌手信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = artist.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = songsCountSuffixText,
                    fontSize = 12.sp,
                    color = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.8f) else Color.Gray
                )
            }
        }
    }
}