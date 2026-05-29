package com.neko.music.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.PlaylistDetailLiquidGlassParams
import com.neko.music.ui.components.ShareSheetLiquidSection
import com.neko.music.ui.components.PlaylistPageDarkTintOverlay
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.neko.music.R
import com.neko.music.data.api.FavoriteApi
import com.neko.music.data.api.PlaylistApi
import com.neko.music.data.api.PlaylistMusic
import com.neko.music.data.api.PlaylistMusicListResponse
import com.neko.music.data.api.PlaylistResponse
import com.neko.music.data.manager.TokenManager
import com.neko.music.data.model.Music
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.SakuraPink
import com.neko.music.util.DownloadHelper
import com.neko.music.util.UrlConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    onPlayAll: (List<PlaylistMusic>) -> Unit,
    /** 歌单批量编辑时请求宿主暂时隐藏底部迷你播放器与底栏，避免遮挡 */
    onPlaylistBatchModeChange: (inBatchEdit: Boolean) -> Unit = {}
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

    var batchMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(playlistId) {
        batchMode = false
        selectedIds = emptySet()
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
                UrlConfig.buildFullUrl(playlistCover)
            }
        } else {
            val firstMusic = musicList.firstOrNull()
            if (firstMusic != null) {
                UrlConfig.getMusicCoverUrl(firstMusic.id)
            } else {
                UrlConfig.getDefaultAvatarUrl()
            }
        }
    }
    // 判断是否是自己的歌单
    val isOwnPlaylist = tokenManager.getUserId() == actualCreatorUserId

    BackHandler(enabled = batchMode) {
        batchMode = false
        selectedIds = emptySet()
    }

    LaunchedEffect(batchMode, isOwnPlaylist) {
        onPlaylistBatchModeChange(batchMode && isOwnPlaylist)
    }

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

    // 移除音乐（单次请求，musicIds 仅含一首）
    val removeMusic: (PlaylistMusic) -> Unit = { music ->
        scope.launch {
            try {
                val token = tokenManager.getToken()
                if (token != null) {
                    val response = playlistApi.removeMusicsFromPlaylist(playlistId, listOf(music.id))
                    val removedCount = response.removedCount ?: 0
                    val anyRemoved = response.success || removedCount > 0
                    if (anyRemoved) {
                        val failed = response.failedMusicIds?.size ?: 0
                        val toastText = when {
                            response.success && failed == 0 ->
                                context.getString(R.string.removed_from_playlist_success)
                            removedCount > 0 && failed > 0 ->
                                context.getString(R.string.playlist_batch_delete_partial, removedCount, failed)
                            response.message.isNotBlank() -> response.message
                            else -> context.getString(R.string.removed_from_playlist_success)
                        }
                        android.widget.Toast.makeText(
                            context,
                            toastText,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        val newResponse: PlaylistMusicListResponse = playlistApi.getPlaylistMusic(playlistId)
                        if (newResponse.success) {
                            musicList = newResponse.musicList ?: emptyList()
                        }
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            response.message.ifBlank { context.getString(R.string.remove_from_playlist_failed, "") },
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

    val performBatchRemove: () -> Unit = removeBatchLabel@{
        if (selectedIds.isEmpty()) return@removeBatchLabel
        scope.launch {
            try {
                val token = tokenManager.getToken()
                if (token != null) {
                    val ids = selectedIds.toList()
                    val response = playlistApi.removeMusicsFromPlaylist(playlistId, ids)
                    val removedCount = response.removedCount ?: 0
                    val failed = response.failedMusicIds?.size ?: 0
                    val anyRemoved = response.success || removedCount > 0
                    if (anyRemoved) {
                        val newResponse: PlaylistMusicListResponse = playlistApi.getPlaylistMusic(playlistId)
                        if (newResponse.success) {
                            musicList = newResponse.musicList ?: emptyList()
                        }
                        val toastText = when {
                            response.success && failed == 0 ->
                                context.getString(R.string.removed_from_playlist_success)
                            removedCount > 0 && failed > 0 ->
                                context.getString(R.string.playlist_batch_delete_partial, removedCount, failed)
                            response.message.isNotBlank() -> response.message
                            else -> context.getString(R.string.removed_from_playlist_success)
                        }
                        android.widget.Toast.makeText(
                            context,
                            toastText,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            context,
                            response.message.ifBlank { context.getString(R.string.remove_from_playlist_failed, "") },
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    batchMode = false
                    selectedIds = emptySet()
                    showBatchDeleteConfirm = false
                } else {
                    showBatchDeleteConfirm = false
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.remove_from_playlist_failed, e.message ?: ""),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                batchMode = false
                selectedIds = emptySet()
                showBatchDeleteConfirm = false
            }
        }
    }

    val scheme = MaterialTheme.colorScheme
    val liquidGlass = LiquidGlassDefaults.playlistDetail
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)
    val isDarkTheme = isSystemInDarkTheme()

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
            PlaylistPageDarkTintOverlay(enabled = isDarkTheme)
        }

        CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
            Box(modifier = Modifier.fillMaxSize()) {
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
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isOwnPlaylist) {
                                TextButton(
                                    onClick = {
                                        batchMode = !batchMode
                                        if (!batchMode) selectedIds = emptySet()
                                    },
                                    modifier = Modifier.padding(end = 2.dp)
                                ) {
                                    Text(
                                        text = if (batchMode) {
                                            stringResource(id = R.string.playlist_batch_done)
                                        } else {
                                            stringResource(id = R.string.playlist_batch_mode)
                                        },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isDarkTheme) {
                                            Color(0xFFB8B8D1).copy(alpha = 0.95f)
                                        } else {
                                            Color(0xFF2C2C2C)
                                        }
                                    )
                                }
                            }

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
                                    contentDescription = stringResource(id = R.string.share_playlist),
                                    tint = if (isDarkTheme) {
                                        Color(0xFFB8B8D1).copy(alpha = 0.92f)
                                    } else {
                                        Color(0xFF2C2C2C)
                                    }
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
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(coverUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "歌单封面",
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
                                text = stringResource(
                                    id = R.string.songs_count_label,
                                    musicList.size
                                ),
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
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(UrlConfig.getUserAvatarUrl(displayCreatorUserId))
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "创建者头像",
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape),
                                        placeholder = painterResource(R.drawable.user),
                                        error = painterResource(R.drawable.user)
                                    )
                                    Text(
                                        text = if (displayCreatorUsername != null)
                                            stringResource(
                                                id = R.string.creator_info,
                                                displayCreatorUsername
                                            )
                                        else
                                            stringResource(
                                                id = R.string.creator_id_info,
                                                displayCreatorUserId ?: 0
                                            ),
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 16.dp,
                                    bottom = if (isOwnPlaylist && batchMode) 108.dp else 150.dp
                                )
                            ) {
                                itemsIndexed(musicList) { index, music ->
                                    PlaylistMusicItem(
                                        music = music,
                                        position = index + 1,
                                        selectionMode = batchMode && isOwnPlaylist,
                                        selected = selectedIds.contains(music.id),
                                        onToggleSelect = {
                                            selectedIds =
                                                if (music.id in selectedIds) selectedIds - music.id
                                                else selectedIds + music.id
                                        },
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
                                        showDeleteButton = isOwner && !batchMode,
                                        liquidGlass = liquidGlass,
                                    )
                                }
                            }
                            if (isOwnPlaylist && batchMode) {
                                val batchBarShape = RoundedCornerShape(
                                    topStart = liquidGlass.batchDockTopCornerDp,
                                    topEnd = liquidGlass.batchDockTopCornerDp,
                                )
                                GlassSurface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .windowInsetsPadding(WindowInsets.navigationBars),
                                    shape = batchBarShape,
                                    backgroundAlpha = if (isDarkTheme) {
                                        liquidGlass.batchDockBackgroundAlphaDark
                                    } else {
                                        liquidGlass.batchDockBackgroundAlphaLight
                                    },
                                    borderAlpha = if (isDarkTheme) {
                                        liquidGlass.batchDockBorderAlphaDark
                                    } else {
                                        liquidGlass.batchDockBorderAlphaLight
                                    },
                                    highlightAlpha = if (isDarkTheme) {
                                        liquidGlass.batchDockHighlightAlphaDark
                                    } else {
                                        liquidGlass.batchDockHighlightAlphaLight
                                    },
                                    borderColor = if (isDarkTheme) {
                                        SakuraPink.copy(alpha = liquidGlass.batchDockDarkBorderSakuraAlpha)
                                    } else {
                                        scheme.outline
                                    },
                                    liquidBlur = liquidGlass.batchDockLiquidBlur,
                                    liquidLensHeight = liquidGlass.batchDockLiquidLensHeight,
                                    liquidLensAmount = liquidGlass.batchDockLiquidLensAmount,
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                selectedIds =
                                                    if (selectedIds.size == musicList.size) {
                                                        emptySet()
                                                    } else {
                                                        musicList.map { it.id }.toSet()
                                                    }
                                            },
                                            colors = ButtonDefaults.textButtonColors(
                                                containerColor = Color.Transparent,
                                                contentColor = RoseRed
                                            )
                                        ) {
                                            Text(
                                                text = if (selectedIds.size == musicList.size) {
                                                    stringResource(id = R.string.playlist_batch_clear_selection)
                                                } else {
                                                    stringResource(id = R.string.playlist_batch_select_all)
                                                },
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    if (selectedIds.isEmpty()) return@TextButton
                                                    val targets =
                                                        musicList.filter { it.id in selectedIds }
                                                    scope.launch {
                                                        val appCtx = context.applicationContext
                                                        val (total, fails) = withContext(Dispatchers.IO) {
                                                            val helper = DownloadHelper(appCtx)
                                                            var failCount = 0
                                                            for (pm in targets) {
                                                                val model = Music(
                                                                    pm.id,
                                                                    pm.title,
                                                                    pm.artist,
                                                                    pm.coverPath ?: "",
                                                                    pm.duration,
                                                                    "",
                                                                    "",
                                                                    0,
                                                                    ""
                                                                )
                                                                try {
                                                                    val r =
                                                                        helper.downloadMusicWithLyrics(model)
                                                                    if (r.isFailure) failCount++
                                                                } catch (_: Exception) {
                                                                    failCount++
                                                                }
                                                            }
                                                            targets.size to failCount
                                                        }
                                                        val msg = when {
                                                            fails == 0 ->
                                                                context.getString(
                                                                    R.string.playlist_batch_download_all_ok,
                                                                    total
                                                                )
                                                            fails == total ->
                                                                context.getString(
                                                                    R.string.playlist_batch_download_all_fail,
                                                                    total
                                                                )
                                                            else ->
                                                                context.getString(
                                                                    R.string.playlist_batch_download_partial,
                                                                    total - fails,
                                                                    fails
                                                                )
                                                        }
                                                        Toast.makeText(
                                                            context,
                                                            msg,
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                },
                                                enabled = selectedIds.isNotEmpty(),
                                                colors = ButtonDefaults.textButtonColors(
                                                    containerColor = Color.Transparent,
                                                    contentColor = RoseRed,
                                                    disabledContentColor = RoseRed.copy(alpha = 0.35f)
                                                )
                                            ) {
                                                Text(
                                                    text = stringResource(
                                                        id = R.string.playlist_batch_download,
                                                        selectedIds.size
                                                    ),
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            Button(
                                                onClick = {
                                                    if (selectedIds.isNotEmpty()) {
                                                        showBatchDeleteConfirm = true
                                                    }
                                                },
                                                enabled = selectedIds.isNotEmpty(),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = RoseRed,
                                                    disabledContainerColor = RoseRed.copy(alpha = 0.35f)
                                                ),
                                                shape = RoundedCornerShape(20.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(
                                                        id = R.string.playlist_batch_delete,
                                                        selectedIds.size
                                                    ),
                                                    color = Color.White,
                                                    fontSize = 15.sp
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

        if (showShareDialog) {
            PlaylistShareSheet(
                playlistName = playlistName,
                playlistId = playlistId,
                liquidBackdrop = pageBackdrop,
                liquidGlass = liquidGlass,
                onDismiss = { showShareDialog = false },
            )
        }

        if (showBatchDeleteConfirm) {
            PlaylistBatchDeleteGlassDialog(
                selectedCount = selectedIds.size,
                pageBackdrop = pageBackdrop,
                liquidGlass = liquidGlass,
                onDismiss = { showBatchDeleteConfirm = false },
                onConfirm = { performBatchRemove() },
            )
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
}

@Composable
private fun PlaylistBatchDeleteGlassDialog(
    selectedCount: Int,
    pageBackdrop: LayerBackdrop,
    liquidGlass: PlaylistDetailLiquidGlassParams,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isDarkTheme = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    BackHandler(onBack = onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = liquidGlass.batchDialogScrimAlpha))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                        shape = RoundedCornerShape(liquidGlass.batchDialogCornerDp),
                        backgroundAlpha = if (isDarkTheme) {
                            liquidGlass.batchDockBackgroundAlphaDark
                        } else {
                            liquidGlass.batchDockBackgroundAlphaLight
                        },
                        borderAlpha = if (isDarkTheme) {
                            liquidGlass.batchDockBorderAlphaDark
                        } else {
                            liquidGlass.batchDockBorderAlphaLight
                        },
                        highlightAlpha = if (isDarkTheme) {
                            liquidGlass.batchDockHighlightAlphaDark
                        } else {
                            liquidGlass.batchDockHighlightAlphaLight
                        },
                        borderColor = if (isDarkTheme) {
                            SakuraPink.copy(alpha = liquidGlass.batchDockDarkBorderSakuraAlpha)
                        } else {
                            scheme.outline
                        },
                        liquidBlur = liquidGlass.batchDockLiquidBlur,
                        liquidLensHeight = liquidGlass.batchDockLiquidLensHeight,
                        liquidLensAmount = liquidGlass.batchDockLiquidLensAmount,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    id = R.string.playlist_batch_delete_confirm,
                                    selectedCount,
                                ),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) {
                                    Color(0xFFF0F0F5).copy(alpha = 0.95f)
                                } else {
                                    scheme.onSurface
                                },
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = onDismiss,
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = Color.Transparent,
                                    ),
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.cancel),
                                        color = if (isDarkTheme) {
                                            Color(0xFFB8B8D1).copy(alpha = 0.9f)
                                        } else {
                                            scheme.onSurfaceVariant
                                        },
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(
                                    onClick = onConfirm,
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = RoseRed,
                                    ),
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.confirm),
                                        fontWeight = FontWeight.Medium,
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
private fun PlaylistShareSheet(
    playlistName: String,
    playlistId: Int,
    liquidBackdrop: LayerBackdrop,
    liquidGlass: PlaylistDetailLiquidGlassParams,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheetShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { sheetShown = true }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (sheetShown) 1f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "playlist_share_scrim",
    )
    BackHandler(onBack = onDismiss)
    val isDarkTheme = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val shareLabelTint =
        if (isDarkTheme) Color.White.copy(alpha = 0.82f) else scheme.onSurfaceVariant
    val dividerColor = if (isDarkTheme) Color.White.copy(alpha = 0.1f) else scheme.outlineVariant
    val cancelTextColor = if (isDarkTheme) Color.White.copy(alpha = 0.8f) else scheme.onSurface
    val copyLinkIconColor = if (isDarkTheme) RoseRed.copy(alpha = 0.92f) else RoseRed

    fun shareText(): String =
        context.getString(R.string.share_playlist_text, playlistName, playlistId)

    // Dialog 独立窗口，叠在 MainActivity 迷你播放器/底栏之上；内层仍提供本页录屏供 Kyant 采样。
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        CompositionLocalProvider(LocalLiquidLayerBackdrop provides liquidBackdrop) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.42f * scrimAlpha))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
                )
                AnimatedVisibility(
                    visible = sheetShown,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                    ) + fadeIn(tween(240, easing = FastOutSlowInEasing)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                    ) + fadeOut(tween(180)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    val panelShape = RoundedCornerShape(
                        topStart = liquidGlass.sharePanelTopCornerDp,
                        topEnd = liquidGlass.sharePanelTopCornerDp,
                    )
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars),
                        shape = panelShape,
                        backgroundAlpha = if (isDarkTheme) {
                            liquidGlass.sharePanelBackgroundAlphaDark
                        } else {
                            liquidGlass.sharePanelBackgroundAlphaLight
                        },
                        borderAlpha = if (isDarkTheme) {
                            liquidGlass.sharePanelBorderAlphaDark
                        } else {
                            liquidGlass.sharePanelBorderAlphaLight
                        },
                        highlightAlpha = if (isDarkTheme) {
                            liquidGlass.sharePanelHighlightAlphaDark
                        } else {
                            liquidGlass.sharePanelHighlightAlphaLight
                        },
                        borderColor = if (isDarkTheme) {
                            RoseRed.copy(alpha = liquidGlass.sharePanelDarkBorderRoseAlpha)
                        } else {
                            scheme.outline
                        },
                        liquidBlur = liquidGlass.sharePanelLiquidBlur,
                        liquidLensHeight = liquidGlass.sharePanelLiquidLensHeight,
                        liquidLensAmount = liquidGlass.sharePanelLiquidLensAmount,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = stringResource(id = R.string.share_playlist),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ShareSheetLiquidSection(modifier = Modifier.padding(horizontal = 4.dp)) {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                ) {
                                    item {
                                        ShareGridItem(
                                            iconRes = R.drawable.twitter,
                                            label = stringResource(id = R.string.share_to_twitter),
                                            color = Color(0xFF1DA1F2),
                                            labelColor = shareLabelTint,
                                            onClick = {
                                                val text = shareText()
                                                val encoded = java.net.URLEncoder.encode(text, "UTF-8")
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW).apply {
                                                        data = Uri.parse("https://twitter.com/intent/tweet?text=$encoded")
                                                    },
                                                )
                                                onDismiss()
                                            },
                                        )
                                    }
                                    item {
                                        ShareGridItem(
                                            iconRes = R.drawable.qq,
                                            label = stringResource(id = R.string.share_to_qq),
                                            color = Color(0xFF12B7F5),
                                            labelColor = shareLabelTint,
                                            onClick = {
                                                scope.launch {
                                                    onDismiss()
                                                    try {
                                                        val text = shareText()
                                                        val qqIntent = Intent(Intent.ACTION_SEND).apply {
                                                            type = "text/plain"
                                                            putExtra(Intent.EXTRA_TEXT, text)
                                                            setPackage("com.tencent.mobileqq")
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        try {
                                                            context.startActivity(qqIntent)
                                                        } catch (_: Exception) {
                                                            Toast.makeText(
                                                                context,
                                                                context.getString(R.string.qq_not_installed),
                                                                Toast.LENGTH_SHORT,
                                                            ).show()
                                                        }
                                                    } catch (_: Exception) {
                                                        Toast.makeText(
                                                            context,
                                                            context.getString(R.string.share_failed),
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    }
                                                }
                                            },
                                        )
                                    }
                                    item {
                                        ShareGridItem(
                                            iconRes = R.drawable.copy_link,
                                            label = stringResource(id = R.string.copy_link),
                                            color = copyLinkIconColor,
                                            labelColor = shareLabelTint,
                                            onClick = {
                                                val text = shareText()
                                                val clip = ClipData.newPlainText(
                                                    context.getString(R.string.playlist_link),
                                                    text,
                                                )
                                                val clipboard =
                                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.link_copied),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                                onDismiss()
                                            },
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = dividerColor)
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = stringResource(id = R.string.cancel),
                                    fontSize = 17.sp,
                                    color = cancelTextColor,
                                    fontWeight = FontWeight.Medium,
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
fun PlaylistMusicItem(
    music: PlaylistMusic,
    position: Int,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onClick: () -> Unit,
    onRemove: () -> Unit,
    showDeleteButton: Boolean = true,
    liquidGlass: PlaylistDetailLiquidGlassParams = LiquidGlassDefaults.playlistDetail,
) {
    val isDarkTheme = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme

    val coverUrl = remember(music.id) {
        UrlConfig.getMusicCoverUrl(music.id)
    }

    val rowClick = {
        if (selectionMode) onToggleSelect() else onClick()
    }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(
                interactionSource = remember(music.id, selectionMode) { MutableInteractionSource() },
                indication = ripple(),
                onClick = rowClick
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
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(
                            width = 2.dp,
                            color = RoseRed,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .background(
                            if (selected) RoseRed.copy(alpha = 0.28f) else Color.Transparent
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = RoseRed,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

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
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "封面",
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
}
