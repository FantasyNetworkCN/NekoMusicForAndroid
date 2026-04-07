package com.neko.music.ui.screens

import android.util.Log
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import coil.compose.AsyncImage
import com.neko.music.R
import com.neko.music.data.api.FavoriteApi
import com.neko.music.data.api.PlaylistApi
import com.neko.music.data.api.PlaylistMusic
import com.neko.music.data.api.PlaylistMusicListResponse
import com.neko.music.data.api.PlaylistResponse
import com.neko.music.data.manager.TokenManager
import com.neko.music.ui.theme.RoseRed
import kotlinx.coroutines.launch

@Composable
fun PlaylistDetailScreen(
    playlistId: Int,
    playlistName: String,
    playlistCover: String?,
    playlistDescription: String = "",
    creatorUsername: String? = null,
    creatorUserId: Int? = null,
    isOwner: Boolean = true,
    onBackClick: () -> Unit,
    onMusicClick: (com.neko.music.data.model.Music) -> Unit,
    onPlayAll: (List<PlaylistMusic>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenManager = remember { TokenManager(context) }
    val playlistApi = remember { PlaylistApi(tokenManager.getToken(), context) }
    val favoriteApi = remember { FavoriteApi(context) }

    var musicList by remember { mutableStateOf<List<PlaylistMusic>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var actualCreatorUsername by remember { mutableStateOf<String?>(null) }
    var actualCreatorUserId by remember { mutableStateOf<Int?>(null) }
    
    var currentDescription by remember { mutableStateOf(playlistDescription) }
    
    var showEditDescriptionDialog by remember { mutableStateOf(false) }
    var editingDescription by remember { mutableStateOf(playlistDescription) }
    var showShareDialog by remember { mutableStateOf(false) }
    
    var isFavorited by remember { mutableStateOf(false) }
    var isCheckingFavorite by remember { mutableStateOf(true) }

    LaunchedEffect(playlistId) {
        try {
            isLoading = true
            isCheckingFavorite = true
            
            // 先获取歌单详情（包含创建者信息）
            val detailResponse: PlaylistResponse = playlistApi.getPlaylistDetail(playlistId)
            Log.d("PlaylistDetailScreen", "歌单详情: playlist=${detailResponse.playlist}")
            if (detailResponse.success && detailResponse.playlist != null) {
                actualCreatorUsername = detailResponse.playlist.creator?.username
                actualCreatorUserId = detailResponse.playlist.creator?.id ?: detailResponse.playlist.userId
                Log.d("PlaylistDetailScreen", "创建者: username=$actualCreatorUsername, userId=$actualCreatorUserId")
            }
            
            // 再获取歌单音乐列表
            val musicResponse: PlaylistMusicListResponse = playlistApi.getPlaylistMusic(playlistId)
            Log.d("PlaylistDetailScreen", "加载歌单音乐: playlistId=$playlistId, success=${musicResponse.success}")
            if (musicResponse.success) {
                musicList = musicResponse.musicList ?: emptyList()
                Log.d("PlaylistDetailScreen", "加载到${musicList.size}首音乐")
            } else {
                errorMessage = musicResponse.message
            }
            
            // 检查收藏状态（仅当不是自己的歌单时）
            val currentUserId = tokenManager.getUserId()
            if (currentUserId != -1 && actualCreatorUserId != currentUserId) {
                val token = tokenManager.getToken()
                if (token != null) {
                    isFavorited = favoriteApi.isPlaylistFavorited(token, playlistId)
                }
            }
        } catch (e: Exception) {
            Log.e("PlaylistDetailScreen", "加载歌单音乐失败", e)
            errorMessage = context.getString(R.string.load_failed_msg, e.message ?: "")
        } finally {
            isLoading = false
            isCheckingFavorite = false
        }
    }

    val coverUrl = remember(playlistCover, musicList) {
        if (!playlistCover.isNullOrEmpty()) {
            // 如果 playlistCover 已经是完整 URL，直接使用；否则拼接
            if (playlistCover.startsWith("http")) {
                playlistCover
            } else {
                "https://music.cnmsb.xin$playlistCover"
            }
        } else {
            val firstMusic = musicList.firstOrNull()
            if (firstMusic != null) {
                "https://music.cnmsb.xin/api/music/cover/${firstMusic.id}"
            } else {
                "https://music.cnmsb.xin/api/user/avatar/default"
            }
        }
    }

    // 判断是否是自己的歌单
    val isOwnPlaylist = tokenManager.getUserId() == actualCreatorUserId

    // 收藏/取消收藏歌单的函数
    val toggleFavorite: () -> Unit = {
        scope.launch {
            try {
                val token = tokenManager.getToken()
                if (token != null) {
                    val response = if (isFavorited) {
                        favoriteApi.removeFavoritePlaylist(token, playlistId)
                    } else {
                        favoriteApi.addFavoritePlaylist(token, playlistId)
                    }
                    
                    if (response.success) {
                        isFavorited = !isFavorited
                        android.widget.Toast.makeText(
                            context,
                            if (isFavorited) context.getString(R.string.add_to_favorites_success) else context.getString(R.string.remove_from_favorites_success),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            response.message,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.operation_failed_msg, e.message ?: ""),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 移除音乐的函数
    val removeMusic: (PlaylistMusic) -> Unit = { music ->
        scope.launch {
            try {
                val token = tokenManager.getToken()
                if (token != null) {
                    val response = playlistApi.removeMusicFromPlaylist(playlistId, music.id)
                    if (response.success) {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.removed_from_playlist_success),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        // 重新加载歌单音乐列表
                        val newResponse: PlaylistMusicListResponse = playlistApi.getPlaylistMusic(playlistId)
                        if (newResponse.success) {
                            musicList = newResponse.musicList ?: emptyList()
                        }
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            response.message,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.remove_from_playlist_failed, e.message ?: ""),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val isDarkTheme = isSystemInDarkTheme()
        
        // 背景图片
        Image(
            painter = painterResource(id = R.drawable.playlist_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 暗色模式遮罩
        if (isDarkTheme) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDarkTheme) {
                    Color(0xFF121228).copy(alpha = 0.4f)
                } else {
                    Color.White.copy(alpha = 0.5f)
                })
        )
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
                        contentDescription = "返回",
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
                        text = playlistName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) {
                            Color(0xFFF0F0F5).copy(alpha = 0.95f)
                        } else {
                            Color.Black
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!isOwnPlaylist && !isCheckingFavorite) {
                    IconButton(
                        onClick = toggleFavorite,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isFavorited) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
                            ),
                            contentDescription = if (isFavorited) "取消收藏" else "收藏",
                            tint = RoseRed
                        )
                    }
                }
                
                IconButton(
                    onClick = { showShareDialog = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "分享",
                        tint = Color.Black
                    )
                }
            }
            }

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
                        .background(if (isDarkTheme) {
                            Color(0xFF252545).copy(alpha = 0.6f)
                        } else {
                            Color(0xFFF5F5F5)
                        }),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = "歌单封面",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = playlistName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) {
                            Color(0xFFF0F0F5).copy(alpha = 0.95f)
                        } else {
                            Color.Black
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = stringResource(id = R.string.songs_count_label, musicList.size),
                        fontSize = 14.sp,
                        color = if (isDarkTheme) {
                            Color(0xFFB8B8D1).copy(alpha = 0.8f)
                        } else {
                            Color.Gray
                        }
                    )

                    if (currentDescription.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = currentDescription,
                            fontSize = 13.sp,
                            color = if (isDarkTheme) {
                                Color(0xFFB8B8D1).copy(alpha = 0.8f)
                            } else {
                                Color.Gray
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                editingDescription = currentDescription
                                showEditDescriptionDialog = true
                            }
                        )
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(id = R.string.no_description_click_to_edit),
                            fontSize = 13.sp,
                            color = if (isDarkTheme) {
                                Color(0xFFB8B8D1).copy(alpha = 0.8f)
                            } else {
                                Color.Gray
                            },
                            modifier = Modifier.clickable {
                                editingDescription = ""
                                showEditDescriptionDialog = true
                            }
                        )
                    }

                    // 创建者信息
                    val displayCreatorUsername = actualCreatorUsername ?: creatorUsername
                    val displayCreatorUserId = actualCreatorUserId ?: creatorUserId
                    
                    if (displayCreatorUserId != null && displayCreatorUserId != -1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AsyncImage(
                                model = "https://music.cnmsb.xin/api/user/avatar/$displayCreatorUserId",
                                contentDescription = "创建者头像",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                            )
                            Text(
                                text = if (displayCreatorUsername != null) 
                                    stringResource(id = R.string.creator_info, displayCreatorUsername) 
                                else 
                                    stringResource(id = R.string.creator_id_info, displayCreatorUserId ?: 0),
                                fontSize = 12.sp,
                                color = if (isDarkTheme) {
                                    Color(0xFFB8B8D1).copy(alpha = 0.8f)
                                } else {
                                    Color.Gray
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (musicList.isNotEmpty()) {
                                onPlayAll(musicList)
                            }
                        },
                        enabled = musicList.isNotEmpty(),
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoseRed,
                            disabledContainerColor = RoseRed.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isDarkTheme) {
                                Color.White.copy(alpha = 0.95f)
                            } else {
                                Color.White
                            },
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(id = R.string.play_all),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkTheme) {
                                Color.White.copy(alpha = 0.95f)
                            } else {
                                Color.White
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = RoseRed)
                }
            } else if (errorMessage.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage,
                        fontSize = 16.sp,
                        color = if (isDarkTheme) {
                            Color(0xFFB8B8D1).copy(alpha = 0.8f)
                        } else {
                            Color.Gray
                        }
                    )
                }
            } else if (musicList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(id = R.string.no_songs),
                        fontSize = 16.sp,
                        color = if (isDarkTheme) {
                            Color(0xFFB8B8D1).copy(alpha = 0.8f)
                        } else {
                            Color.Gray
                        }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 150.dp)
                ) {
                    itemsIndexed(musicList) { index, music ->
                        PlaylistMusicItem(
                            music = music,
                            position = index + 1,
                            onClick = {
                                onMusicClick(
                                    com.neko.music.data.model.Music(
                                        music.id,
                                        music.title,
                                        music.artist,
                                        music.coverPath ?: "",
                                        music.duration,
                                        "",
                                        "",
                                        0,
                                        ""
                                    )
                                )
                            },
                            onRemove = { removeMusic(music) },
                            showDeleteButton = isOwner
                        )
                    }
                }
            }
        }

        if (showEditDescriptionDialog) {
            AlertDialog(
                onDismissRequest = { showEditDescriptionDialog = false },
                title = {
                    Text(
                        text = stringResource(id = R.string.edit_description),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkTheme) {
                            Color(0xFFF0F0F5).copy(alpha = 0.95f)
                        } else {
                            Color.Black
                        }
                    )
                },
                text = {
                    OutlinedTextField(
                        value = editingDescription,
                        onValueChange = { editingDescription = it },
                        placeholder = { 
                            Text(
                                stringResource(id = R.string.enter_description),
                                color = if (isDarkTheme) {
                                    Color(0xFFB8B8D1).copy(alpha = 0.6f)
                                } else {
                                    Color.Gray
                                }
                            )
                        },
                        singleLine = false,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
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
                            }
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val token = tokenManager.getToken()
                                    if (token != null) {
                                        val response = playlistApi.updatePlaylist(playlistId, playlistName, editingDescription)
                                        if (response.success) {
                                            currentDescription = editingDescription
                                            android.widget.Toast.makeText(
                                                context,
                                                context.getString(R.string.description_updated_success),
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                            showEditDescriptionDialog = false
                                        } else {
                                            android.widget.Toast.makeText(
                                                context,
                                                response.message,
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.description_update_failed_msg, e.message ?: ""),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        enabled = editingDescription != currentDescription,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoseRed
                        )
                    ) {
                        Text(
                            stringResource(id = R.string.confirm),
                            color = if (isDarkTheme) {
                                Color.White.copy(alpha = 0.95f)
                            } else {
                                Color.White
                            }
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDescriptionDialog = false }) {
                        Text(
                            stringResource(id = R.string.cancel),
                            color = if (isDarkTheme) {
                                Color(0xFFB8B8D1).copy(alpha = 0.8f)
                            } else {
                                Color.Gray
                            }
                        )
                    }
                }
            )
        }
    }

    // 分享对话框
    if (showShareDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showShareDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { showShareDialog = false }
                    )
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = true,
                    enter = androidx.compose.animation.slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    ),
                    exit = androidx.compose.animation.slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = androidx.compose.animation.core.tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                ) {
                    val shareDialogIsDarkTheme = isSystemInDarkTheme()
                    
                    Surface(
                        shape = RoundedCornerShape(0.dp),
                        color = if (shareDialogIsDarkTheme) {
                            Color(0xFF1A1A2E).copy(alpha = 0.95f)
                        } else {
                            Color.White
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))

                            // 分享标题
                            Text(
                                text = stringResource(id = R.string.share_playlist),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (shareDialogIsDarkTheme) {
                                    Color(0xFFF0F0F5).copy(alpha = 0.95f)
                                } else {
                                    Color.Black
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // 分享选项
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                item {
                                    ShareGridItem(
                                        iconRes = R.drawable.twitter,
                                        label = stringResource(id = R.string.share_to_twitter),
                                        color = Color(0xFF1DA1F2),
                                        onClick = {
                                            val shareText = "我在Neko云音乐发现了宝藏歌单《$playlistName》- https://music.cnmsb.xin/playlist/$playlistId，里面超好听！大家快来听喵~"
                                            val encodedText = java.net.URLEncoder.encode(shareText, "UTF-8")
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                data = android.net.Uri.parse("https://twitter.com/intent/tweet?text=$encodedText")
                                            }
                                            context.startActivity(intent)
                                            showShareDialog = false
                                        }
                                    )
                                }
                                item {
                                    ShareGridItem(
                                        iconRes = R.drawable.qq,
                                        label = stringResource(id = R.string.share_to_qq),
                                        color = Color(0xFF12B7F5),
                                        onClick = {
                                            android.widget.Toast.makeText(context, "我他妈注册补上傻逼QQ开放平台！", android.widget.Toast.LENGTH_SHORT).show()
                                            showShareDialog = false
                                        }
                                    )
                                }
                                item {
                                    ShareGridItem(
                                        iconRes = R.drawable.copy_link,
                                        label = stringResource(id = R.string.copy_link),
                                        color = RoseRed,
                                        onClick = {
                                            val shareUrl = "https://music.cnmsb.xin/playlist/$playlistId"
                                            val shareText = context.getString(R.string.share_playlist_text, playlistName, playlistId)
                                            val clip = android.content.ClipData.newPlainText(context.getString(R.string.playlist_link), shareText)
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            clipboard.setPrimaryClip(clip)
                                            android.widget.Toast.makeText(context, context.getString(R.string.link_copied), android.widget.Toast.LENGTH_SHORT).show()
                                            showShareDialog = false
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // 取消按钮
                            Divider()
                            TextButton(
                                onClick = { showShareDialog = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(id = R.string.cancel),
                                    fontSize = 16.sp,
                                    color = if (shareDialogIsDarkTheme) {
                                        Color(0xFFB8B8D1).copy(alpha = 0.8f)
                                    } else {
                                        Color.Gray
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

@Composable
fun ShareGridItem(
    iconRes: Int,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    color = color.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                modifier = Modifier.size(28.dp),
                colorFilter = ColorFilter.tint(color)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (isDarkTheme) {
                Color(0xFFB8B8D1).copy(alpha = 0.8f)
            } else {
                Color.Gray
            }
        )
    }
}

@Composable
fun PlaylistMusicItem(
    music: PlaylistMusic,
    position: Int,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    showDeleteButton: Boolean = true
) {
    val isDarkTheme = isSystemInDarkTheme()
    var isPressed by remember { mutableStateOf(false) }

    val coverUrl = remember(music.id) {
        "https://music.cnmsb.xin/api/music/cover/${music.id}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDarkTheme) {
                Color(0xFF252545).copy(alpha = 0.6f)
            } else {
                Color(0xFFF5F5F5)
            })
            .clickable {
                isPressed = true
                onClick()
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                .background(if (isDarkTheme) {
                    Color(0xFF353558).copy(alpha = 0.6f)
                } else {
                    Color(0xFFE0E0E0)
                }),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = "封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
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
                overflow = TextOverflow.Ellipsis
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
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = formatTime(music.duration * 1000L),
            fontSize = 13.sp,
            color = if (isDarkTheme) {
                Color(0xFFB8B8D1).copy(alpha = 0.8f)
            } else {
                Color.Gray
            }
        )

        if (showDeleteButton) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "移除",
                    tint = if (isDarkTheme) {
                        Color(0xFFB8B8D1).copy(alpha = 0.8f)
                    } else {
                        Color.Gray
                    }
                )
            }
        }
    }
}