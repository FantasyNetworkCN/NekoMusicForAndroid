package com.neko.music.ui.screens

import android.util.Log
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.neko.music.R
import com.neko.music.util.UrlConfig
import com.neko.music.data.api.MusicApi
import com.neko.music.data.model.Music
import com.neko.music.service.MusicPlayerManager
import com.neko.music.ui.theme.RoseRed
import io.ktor.client.call.body
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

    // 预加载字符串资源
    val noMusicFound = stringResource(id = R.string.no_music_found)

    // 加载歌手的音乐列表
    LaunchedEffect(artistName) {
        scope.launch {
            try {
                isLoading = true
                val client = io.ktor.client.HttpClient()
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
                Log.d("ArtistDetailScreen", "歌手详情响应: $responseText")
                
                // 解析 JSON 响应
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
                            val coverPath = musicJson["coverPath"]?.jsonPrimitive?.content ?: ""
                            val filePath = musicJson["filePath"]?.jsonPrimitive?.content ?: ""
                            val fileFormat = musicJson["fileFormat"]?.jsonPrimitive?.content ?: ""
                            val language = musicJson["language"]?.jsonPrimitive?.content ?: ""
                            
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
                        Log.d("ArtistDetailScreen", "加载到 ${musics.size} 首歌曲")
                        musics.forEach { music ->
                            Log.d("ArtistDetailScreen", "音乐: ${music.title}, 路径: ${music.filePath}, 封面: ${music.coverFilePath}")
                        }
                    } else {
                        isLoading = false
                        errorMessage = noMusicFound
                    }
                } catch (e: Exception) {
                    Log.e("ArtistDetailScreen", "JSON解析失败", e)
                    // 降级到正则表达式解析
                    val musicListRegex = """"musicList":\s*\[([^\]]*)\]""".toRegex()
                    val match = musicListRegex.find(responseText)
                    
                    if (match != null) {
                        val musicListJson = match.groupValues[1]
                        val musics = mutableListOf<Music>()
                        
                        // 匹配音乐信息
                        val musicRegex = """"id":\s*(\d+),\s*"title":\s*"([^"]*)",\s*"artist":\s*"([^"]*)",\s*"album":\s*"([^"]*)",\s*"duration":\s*(\d+),\s*"coverPath":\s*"([^"]*)",\s*"filePath":\s*"([^"]*)",\s*"fileFormat":\s*"([^"]*)",\s*"language":\s*"([^"]*)"""".toRegex()
                        musicRegex.findAll(musicListJson).forEach { matchResult ->
                            val id = matchResult.groupValues[1].toIntOrNull() ?: 0
                            val title = matchResult.groupValues[2]
                            val artist = matchResult.groupValues[3]
                            val album = matchResult.groupValues[4]
                            val duration = matchResult.groupValues[5].toIntOrNull() ?: 0
                            val coverPath = matchResult.groupValues[6]
                            val filePath = matchResult.groupValues[7]
                            val fileFormat = matchResult.groupValues[8]
                            val language = matchResult.groupValues[9]
                            
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
                        Log.d("ArtistDetailScreen", "加载到 ${musics.size} 首歌曲（正则解析）")
                    } else {
                        isLoading = false
                        errorMessage = noMusicFound
                    }
                }
            } catch (e: Exception) {
                Log.e("ArtistDetailScreen", "加载歌手音乐失败", e)
                isLoading = false
                errorMessage = e.message
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // 顶部导航栏
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
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = stringResource(id = R.string.artist_detail),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFE0E0E0))
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
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
                            text = errorMessage ?: context.getString(R.string.loading_failed_format, ""),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 歌手信息头部
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // 歌手信息
                                    Text(
                                        text = artistName,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(id = R.string.songs_count_suffix, musicCount),
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        // 播放全部按钮
                        item {
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            val playlistManager = com.neko.music.data.manager.PlaylistManager.getInstance(context)
                                            
                                            // 清空当前播放列表
                                            playlistManager.clearPlaylist()
                                            
                                            // 按顺序添加音乐到播放列表
                                            musicList.forEach { music ->
                                                val url = musicApi.getMusicFileUrl(music)
                                                Log.d("ArtistDetailScreen", "添加到播放列表: ${music.title}, id=${music.id}, url=$url")
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
                                            
                                            Log.d("ArtistDetailScreen", "总共添加了 ${musicList.size} 首歌曲到播放列表")
                                            
                                            // 播放第一首
                                            if (musicList.isNotEmpty()) {
                                                val firstMusic = musicList[0]
                                                val url = musicApi.getMusicFileUrl(firstMusic)
                                                val fullCoverUrl = UrlConfig.getMusicCoverUrl(firstMusic.id)
                                                Log.d("ArtistDetailScreen", "播放第一首: ${firstMusic.title}, id=${firstMusic.id}, url=$url, cover=$fullCoverUrl")
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
                                                context.getString(R.string.play_failed, e.message ?: ""),
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RoseRed
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = stringResource(id = R.string.play_all),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(id = R.string.play_all),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        // 音乐列表
                        items(musicList) { music ->
                            ArtistMusicItem(
                                music = music,
                                onClick = { onMusicClick(music) }
                            )
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
    onClick: () -> Unit
) {
    val coverUrl = music.coverFilePath?.takeIf { it.isNotEmpty() }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                color = if (isSystemInDarkTheme()) {
                    Color.White.copy(alpha = 0.05f)
                } else {
                    Color.White
                },
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
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
                coil.compose.AsyncImage(
                    model = coverUrl,
                    contentDescription = stringResource(id = R.string.cover),
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
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = music.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = music.album,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}