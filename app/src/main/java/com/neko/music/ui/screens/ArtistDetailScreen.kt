package com.neko.music.ui.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.R
import com.neko.music.data.api.MusicApi
import com.neko.music.data.model.Music
import com.neko.music.service.MusicPlayerManager
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.theme.RoseRed
import com.neko.music.util.UrlConfig
import com.neko.music.util.preferHttp2AlpnOverHttp1
import com.neko.music.util.protocolLogSuffix
import com.neko.music.util.protocolLogSuffixOrEmpty
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.launch
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun ArtistDetailScreen(
    artistName: String,
    musicCount: Int,
    coverPath: String?,
    onBackClick: () -> Unit,
    onMusicClick: (Music) -> Unit
) {
    val context = LocalContext.current
    val musicApi = remember { MusicApi(context) }
    val playerManager = remember { MusicPlayerManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    var musicList by remember { mutableStateOf<List<Music>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val noMusicFound = stringResource(id = R.string.no_music_found)

    LaunchedEffect(artistName) {
        scope.launch {
            try {
                isLoading = true
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
                                "query": "$artistName"
                            }
                            """.trimIndent()
                    )
                }

                val responseText = response.body<String>()
                Log.d("ArtistDetailScreen", "歌手详情响应: $responseText${response.protocolLogSuffix()}")

                try {
                    val jsonObject = kotlinx.serialization.json.Json.parseToJsonElement(responseText)
                    val artistObj = jsonObject.jsonObject["artist"]?.jsonObject
                    val musicListArray = artistObj?.get("musicList")?.jsonArray

                    if (musicListArray != null) {
                        val musics = mutableListOf<Music>()

                        musicListArray.forEach { element ->
                            val musicJson = element.jsonObject
                            val id = musicJson["id"]?.jsonPrimitive?.int ?: 0
                            val title = musicJson["title"]?.jsonPrimitive?.content ?: ""
                            val artist = musicJson["artist"]?.jsonPrimitive?.content ?: ""
                            val album = musicJson["album"]?.jsonPrimitive?.content ?: ""
                            val duration = musicJson["duration"]?.jsonPrimitive?.int ?: 0

                            musics.add(
                                Music(
                                    id = id,
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    duration = duration,
                                    filePath = "$baseUrl/api/music/file/$id",
                                    coverFilePath = "$baseUrl/api/music/cover/$id",
                                    uploadUserId = 0,
                                    createdAt = ""
                                )
                            )
                        }

                        musicList = musics
                        isLoading = false
                        Log.d("ArtistDetailScreen", "加载到 ${musics.size} 首歌曲${response.protocolLogSuffix()}")
                    } else {
                        isLoading = false
                        errorMessage = noMusicFound
                    }
                } catch (e: Exception) {
                    Log.e("ArtistDetailScreen", "JSON解析失败${e.protocolLogSuffixOrEmpty()}", e)
                    val musicListRegex = """"musicList":\s*\[([^\]]*)\]""".toRegex()
                    val match = musicListRegex.find(responseText)

                    if (match != null) {
                        val musicListJson = match.groupValues[1]
                        val musics = mutableListOf<Music>()

                        val musicRegex =
                            """"id":\s*(\d+),\s*"title":\s*"([^"]*)",\s*"artist":\s*"([^"]*)",\s*"album":\s*"([^"]*)",\s*"duration":\s*(\d+),\s*"coverPath":\s*"([^"]*)",\s*"filePath":\s*"([^"]*)",\s*"fileFormat":\s*"([^"]*)",\s*"language":\s*"([^"]*)"""".toRegex()
                        musicRegex.findAll(musicListJson).forEach { matchResult ->
                            val id = matchResult.groupValues[1].toIntOrNull() ?: 0
                            val title = matchResult.groupValues[2]
                            val artist = matchResult.groupValues[3]
                            val album = matchResult.groupValues[4]
                            val duration = matchResult.groupValues[5].toIntOrNull() ?: 0

                            musics.add(
                                Music(
                                    id = id,
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    duration = duration,
                                    filePath = "$baseUrl/api/music/file/$id",
                                    coverFilePath = "$baseUrl/api/music/cover/$id",
                                    uploadUserId = 0,
                                    createdAt = ""
                                )
                            )
                        }

                        musicList = musics
                        isLoading = false
                        Log.d(
                            "ArtistDetailScreen",
                            "加载到 ${musics.size} 首歌曲（正则解析）${response.protocolLogSuffix()}"
                        )
                    } else {
                        isLoading = false
                        errorMessage = noMusicFound
                    }
                }
            } catch (e: Exception) {
                Log.e("ArtistDetailScreen", "加载歌手音乐失败${e.protocolLogSuffixOrEmpty()}", e)
                isLoading = false
                errorMessage = e.message
            }
        }
    }

    val isDarkTheme = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val pageBackdrop = rememberLiquidPageBackdrop(
        if (isDarkTheme) Color(0xFF121228) else scheme.background
    )
    val glassTint = LiquidGlassDefaults.screenListCard
    val glassBg = glassTint.background(isDarkTheme)
    val glassBorder = glassTint.border(isDarkTheme)
    val glassHighlight = glassTint.highlight(isDarkTheme)
    val listBottomInset = LiquidGlassDefaults.vipCenterListBottomInsetDp

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
                .background(
                    scheme.background.copy(
                        alpha = if (isDarkTheme) 0.55f else 0.88f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(id = R.string.back),
                        tint = if (isDarkTheme) Color(0xFFB8B8D1).copy(alpha = 0.9f) else scheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = stringResource(id = R.string.artist_detail),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(pageBackdrop)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isDarkTheme) Color(0xFF121228).copy(alpha = 0.35f)
                                else Color.White.copy(alpha = 0.25f)
                            )
                    )
                }

                CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = RoseRed)
                            }
                        }

                        errorMessage != null -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = errorMessage
                                        ?: context.getString(R.string.loading_failed_format, ""),
                                    color = if (isDarkTheme) {
                                        Color(0xFFB8B8D1).copy(alpha = 0.8f)
                                    } else {
                                        scheme.onSurfaceVariant
                                    },
                                    fontSize = 16.sp
                                )
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = listBottomInset
                                ),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
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
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = artistName,
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDarkTheme) {
                                                    Color(0xFFF0F0F5).copy(alpha = 0.95f)
                                                } else {
                                                    scheme.onSurface
                                                }
                                            )
                                            Text(
                                                text = stringResource(
                                                    id = R.string.songs_count_suffix,
                                                    musicCount
                                                ),
                                                fontSize = 14.sp,
                                                color = if (isDarkTheme) {
                                                    Color(0xFFB8B8D1).copy(alpha = 0.8f)
                                                } else {
                                                    scheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }
                                }

                                item {
                                    GlassSurface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                scope.launch {
                                                    try {
                                                        val playlistManager =
                                                            com.neko.music.data.manager.PlaylistManager.getInstance(
                                                                context
                                                            )

                                                        playlistManager.clearPlaylist()

                                                        musicList.forEach { music ->
                                                            val url = musicApi.getMusicFileUrl(music)
                                                            playlistManager.addToPlaylist(
                                                                Music(
                                                                    music.id,
                                                                    music.title,
                                                                    music.artist,
                                                                    "",
                                                                    music.duration,
                                                                    url,
                                                                    "",
                                                                    0,
                                                                    ""
                                                                )
                                                            )
                                                        }

                                                        if (musicList.isNotEmpty()) {
                                                            val firstMusic = musicList[0]
                                                            val url = musicApi.getMusicFileUrl(firstMusic)
                                                            val fullCoverUrl =
                                                                UrlConfig.getMusicCoverUrl(firstMusic.id)
                                                            playerManager.playMusic(
                                                                url,
                                                                firstMusic.id,
                                                                firstMusic.title,
                                                                firstMusic.artist,
                                                                firstMusic.coverFilePath ?: "",
                                                                fullCoverUrl
                                                            )
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e("ArtistDetailScreen", "播放全部失败", e)
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            context.getString(
                                                                R.string.play_failed,
                                                                e.message ?: ""
                                                            ),
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            },
                                        shape = RoundedCornerShape(24.dp),
                                        backgroundAlpha = glassBg,
                                        borderAlpha = glassBorder,
                                        highlightAlpha = glassHighlight
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(48.dp)
                                                .padding(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = stringResource(id = R.string.play_all),
                                                tint = RoseRed,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(id = R.string.play_all),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = RoseRed
                                            )
                                        }
                                    }
                                }

                                items(musicList) { music ->
                                    ArtistMusicItem(
                                        music = music,
                                        onClick = { onMusicClick(music) },
                                        glassBg = glassBg,
                                        glassBorder = glassBorder,
                                        glassHighlight = glassHighlight
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

@Composable
fun ArtistMusicItem(
    music: Music,
    onClick: () -> Unit,
    glassBg: Float,
    glassBorder: Float,
    glassHighlight: Float
) {
    val coverUrl = music.coverFilePath?.takeIf { it.isNotEmpty() }
    val isDarkTheme = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        backgroundAlpha = glassBg,
        borderAlpha = glassBorder,
        highlightAlpha = glassHighlight
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
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = stringResource(id = R.string.cover),
                        modifier = Modifier.size(44.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.music),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

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
                        scheme.onSurface
                    },
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = music.album,
                    fontSize = 13.sp,
                    color = if (isDarkTheme) {
                        Color(0xFFB8B8D1).copy(alpha = 0.8f)
                    } else {
                        scheme.onSurfaceVariant
                    },
                    maxLines = 1
                )
            }
        }
    }
}
