package com.neko.music.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.neko.music.R
import com.neko.music.data.api.CommentApi
import com.neko.music.data.api.CommentItem
import com.neko.music.data.api.CommentUser
import com.neko.music.data.manager.TokenManager
import com.neko.music.ui.theme.RoseRed
import com.neko.music.util.UrlConfig
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 每页楼层数，与后端契约（1~50）一致。 */
private const val COMMENT_PAGE_SIZE = 20

/** 评论内容长度上限，与后端校验一致。 */
private const val COMMENT_MAX_LENGTH = 500

private data class ReplyTarget(val id: Int, val nickname: String)

private data class DeleteTarget(val id: Int, val isFloor: Boolean, val replyCount: Int)

/**
 * 播放页左侧滑出的评论抽屉面板。
 *
 * 打开 / 关闭动画与手势由 [com.neko.music.ui.screens.PlayerScreen] 负责，这里只画玻璃面板本身。
 */
@Composable
fun CommentPage(
    musicId: Int,
    isDarkTheme: Boolean,
    onRequestLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { CommentApi(context) }
    val tokenManager = remember { TokenManager(context) }
    val isLoggedIn = tokenManager.isLoggedIn()

    var comments by remember(musicId) { mutableStateOf(emptyList<CommentItem>()) }
    var totalCount by remember(musicId) { mutableIntStateOf(0) }
    var loadedPage by remember(musicId) { mutableIntStateOf(0) }
    var hasMore by remember(musicId) { mutableStateOf(false) }
    var isLoading by remember(musicId) { mutableStateOf(false) }
    var isLoadingMore by remember(musicId) { mutableStateOf(false) }
    var loadFailed by remember(musicId) { mutableStateOf(false) }
    var draft by remember(musicId) { mutableStateOf("") }
    var replyTarget by remember(musicId) { mutableStateOf<ReplyTarget?>(null) }
    var isSending by remember(musicId) { mutableStateOf(false) }
    var expandedFloors by remember(musicId) { mutableStateOf(emptySet<Int>()) }
    var deleteTarget by remember(musicId) { mutableStateOf<DeleteTarget?>(null) }
    var isDeleting by remember(musicId) { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val textPrimary = if (isDarkTheme) Color.White else Color(0xFF17171A)
    val textSecondary = if (isDarkTheme) Color.White.copy(alpha = 0.58f) else Color(0xFF5B5B63)
    val inputBackground =
        if (isDarkTheme) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
    val dividerColor = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.07f)

    suspend fun loadComments(reset: Boolean) {
        if (isLoading || isLoadingMore) return
        if (!reset && !hasMore) return
        val targetPage = if (reset) 1 else loadedPage + 1
        if (reset) {
            isLoading = true
            loadFailed = false
        } else {
            isLoadingMore = true
        }
        val response = api.getComments(
            musicId = musicId,
            page = targetPage,
            pageSize = COMMENT_PAGE_SIZE,
            token = tokenManager.getToken(),
        )
        val data = response.data
        if (response.success && data != null) {
            comments = if (reset) data.comments else comments + data.comments
            totalCount = data.totalComments
            loadedPage = data.page
            hasMore = data.hasMore
            loadFailed = false
        } else if (reset) {
            loadFailed = true
        } else {
            Toast.makeText(
                context,
                response.message.ifBlank { context.getString(R.string.comment_load_failed) },
                Toast.LENGTH_SHORT,
            ).show()
        }
        isLoading = false
        isLoadingMore = false
    }

    LaunchedEffect(musicId) {
        // 本地音乐没有云端评论
        if (musicId < 0) return@LaunchedEffect
        loadComments(reset = true)
    }

    fun submit() {
        val token = tokenManager.getToken()
        if (token.isNullOrBlank()) {
            onRequestLogin()
            return
        }
        val content = draft.trim()
        if (content.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.comment_content_empty), Toast.LENGTH_SHORT).show()
            return
        }
        if (content.length > COMMENT_MAX_LENGTH) {
            Toast.makeText(context, context.getString(R.string.comment_content_too_long), Toast.LENGTH_SHORT).show()
            return
        }
        if (isSending) return
        isSending = true
        val parentId = replyTarget?.id
        scope.launch {
            val response = api.postComment(token, musicId, content, parentId)
            isSending = false
            if (response.success) {
                draft = ""
                replyTarget = null
                Toast.makeText(context, context.getString(R.string.comment_post_success), Toast.LENGTH_SHORT).show()
                loadComments(reset = true)
            } else {
                Toast.makeText(
                    context,
                    response.message.ifBlank { context.getString(R.string.comment_post_failed) },
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun deleteComment(target: DeleteTarget) {
        val token = tokenManager.getToken()
        if (token.isNullOrBlank()) {
            onRequestLogin()
            return
        }
        if (isDeleting) return
        isDeleting = true
        scope.launch {
            val response = api.deleteComment(token, target.id)
            isDeleting = false
            deleteTarget = null
            if (response.success) {
                val removedReplies = response.data?.repliesDeleted ?: target.replyCount
                comments = if (target.isFloor) {
                    totalCount -= removedReplies + 1
                    comments.filterNot { it.id == target.id }
                } else {
                    totalCount -= 1
                    comments.map { floor ->
                        if (floor.replies.any { it.id == target.id }) {
                            floor.copy(
                                replies = floor.replies.filterNot { it.id == target.id },
                                replyCount = (floor.replyCount - 1).coerceAtLeast(0),
                            )
                        } else {
                            floor
                        }
                    }
                }
                totalCount = totalCount.coerceAtLeast(0)
                Toast.makeText(context, context.getString(R.string.comment_delete_success), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    response.message.ifBlank { context.getString(R.string.comment_delete_failed) },
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(id = R.string.comment_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary,
            )
            if (totalCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.comment_count_format, totalCount),
                    fontSize = 11.5.sp,
                    color = textSecondary,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .height(1.dp)
                .background(dividerColor)
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading && comments.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = RoseRed,
                            strokeWidth = 2.dp,
                        )
                    }
                }

                comments.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when {
                                    musicId < 0 -> stringResource(id = R.string.local_music_cloud_action_unavailable)
                                    loadFailed -> stringResource(id = R.string.comment_load_failed)
                                    else -> stringResource(id = R.string.comment_empty)
                                },
                                fontSize = 13.sp,
                                color = textSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            if (loadFailed && musicId >= 0) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource(id = R.string.retry),
                                    fontSize = 13.sp,
                                    color = RoseRed,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { scope.launch { loadComments(reset = true) } }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 22.dp,
                            end = 22.dp,
                            top = 2.dp,
                            bottom = 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        items(items = comments, key = { it.id }) { floor ->
                            CommentFloorCard(
                                floor = floor,
                                isDarkTheme = isDarkTheme,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                expanded = expandedFloors.contains(floor.id),
                                onToggleReplies = {
                                    expandedFloors = if (expandedFloors.contains(floor.id)) {
                                        expandedFloors - floor.id
                                    } else {
                                        expandedFloors + floor.id
                                    }
                                },
                                onReply = { target -> replyTarget = target },
                                onDelete = { target -> deleteTarget = target },
                            )
                        }
                        item(key = "footer") {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    isLoadingMore -> CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = RoseRed,
                                        strokeWidth = 2.dp,
                                    )

                                    hasMore -> Text(
                                        text = stringResource(id = R.string.comment_load_more),
                                        fontSize = 13.sp,
                                        color = textSecondary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable(enabled = !isLoadingMore) {
                                                scope.launch { loadComments(reset = false) }
                                            }
                                            .padding(horizontal = 20.dp, vertical = 10.dp),
                                    )

                                    else -> Text(
                                        text = stringResource(id = R.string.comment_empty),
                                        fontSize = 12.sp,
                                        color = textSecondary.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (musicId >= 0) {
            CommentComposer(
                isLoggedIn = isLoggedIn,
                draft = draft,
                onDraftChange = { draft = it },
                replyTarget = replyTarget,
                onClearReply = { replyTarget = null },
                isSending = isSending,
                isDarkTheme = isDarkTheme,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                inputBackground = inputBackground,
                onRequestLogin = onRequestLogin,
                onSubmit = { submit() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 10.dp),
            )
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!isDeleting) deleteTarget = null },
            containerColor = if (isDarkTheme) Color(0xFF1C1C20) else Color.White,
            title = {
                Text(
                    text = stringResource(id = R.string.comment_delete_title),
                    color = textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(id = R.string.comment_delete_message),
                        color = textSecondary,
                        fontSize = 13.sp,
                    )
                    if (target.isFloor && target.replyCount > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(id = R.string.comment_delete_floor_message),
                            color = textSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { deleteComment(target) },
                    enabled = !isDeleting,
                ) {
                    Text(text = stringResource(id = R.string.delete), color = RoseRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(text = stringResource(id = R.string.cancel), color = textSecondary)
                }
            },
        )
    }
}

@Composable
private fun CommentFloorCard(
    floor: CommentItem,
    isDarkTheme: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    expanded: Boolean,
    onToggleReplies: () -> Unit,
    onReply: (ReplyTarget) -> Unit,
    onDelete: (DeleteTarget) -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        CommentRow(
            nickname = floor.user?.nickname.orEmpty(),
            userId = floor.user?.id ?: 0,
            content = floor.content,
            createdAt = floor.createdAt,
            ipRegion = floor.ipRegion,
            isDarkTheme = isDarkTheme,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            showActions = true,
            canDelete = floor.canDelete,
            onReply = {
                onReply(
                    ReplyTarget(
                        id = floor.id,
                        nickname = floor.user?.nickname.orEmpty().ifBlank {
                            context.getString(R.string.comment_title)
                        },
                    )
                )
            },
            onDelete = { onDelete(DeleteTarget(floor.id, isFloor = true, replyCount = floor.replyCount)) },
        )

        if (floor.replies.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (expanded) {
                    stringResource(id = R.string.comment_collapse_replies)
                } else {
                    stringResource(id = R.string.comment_view_replies_format, floor.replies.size)
                },
                fontSize = 12.sp,
                color = textSecondary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onToggleReplies() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        if (expanded) {
            floor.replies.forEach { reply ->
                Spacer(modifier = Modifier.height(10.dp))
                CommentRow(
                    nickname = reply.user?.nickname.orEmpty(),
                    userId = reply.user?.id ?: 0,
                    content = reply.content,
                    createdAt = reply.createdAt,
                    ipRegion = reply.ipRegion,
                    isDarkTheme = isDarkTheme,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    replyToUser = reply.replyToUser,
                    showActions = false,
                    canDelete = reply.canDelete,
                    onReply = {},
                    onDelete = { onDelete(DeleteTarget(reply.id, isFloor = false, replyCount = 0)) },
                    modifier = Modifier.padding(start = 22.dp),
                )
            }
        }
    }
}

@Composable
private fun CommentRow(
    nickname: String,
    userId: Int,
    content: String,
    createdAt: String,
    ipRegion: String,
    isDarkTheme: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    showActions: Boolean,
    canDelete: Boolean,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    replyToUser: CommentUser? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(modifier = modifier.fillMaxWidth()) {
        AsyncImage(
            model = UrlConfig.getUserAvatarUrl(userId),
            contentDescription = null,
            modifier = Modifier
                .size(if (showActions) 34.dp else 28.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = nickname,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (replyToUser != null && replyToUser.nickname.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = context.getString(R.string.comment_reply_hint_format, replyToUser.nickname),
                    fontSize = 11.5.sp,
                    color = RoseRed.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (content.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = content,
                    fontSize = 13.5.sp,
                    color = textPrimary,
                    lineHeight = 19.sp,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        if (ipRegion.isNotBlank()) {
                            append(ipRegion)
                            append(" · ")
                        }
                        append(formatCommentTime(context, createdAt))
                    },
                    fontSize = 11.sp,
                    color = textSecondary.copy(alpha = 0.85f),
                )
                if (showActions) {
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = stringResource(id = R.string.comment_reply),
                        fontSize = 11.5.sp,
                        color = textSecondary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onReply() }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (canDelete) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(id = R.string.delete),
                        tint = textSecondary.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable { onDelete() }
                            .padding(2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentComposer(
    isLoggedIn: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    replyTarget: ReplyTarget?,
    onClearReply: () -> Unit,
    isSending: Boolean,
    isDarkTheme: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    inputBackground: Color,
    onRequestLogin: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (!isLoggedIn) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(inputBackground)
                    .clickable { onRequestLogin() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(id = R.string.comment_login_hint),
                    fontSize = 13.sp,
                    color = textSecondary,
                )
            }
            return@Column
        }

        if (replyTarget != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(id = R.string.comment_reply_hint_format, replyTarget.nickname),
                    fontSize = 12.sp,
                    color = textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(id = R.string.comment_cancel_reply),
                    fontSize = 12.sp,
                    color = RoseRed,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onClearReply() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 104.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(inputBackground)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                if (draft.isEmpty()) {
                    Text(
                        text = stringResource(id = R.string.comment_input_hint),
                        fontSize = 13.5.sp,
                        color = textSecondary.copy(alpha = 0.8f),
                    )
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { value ->
                        if (value.length <= COMMENT_MAX_LENGTH) onDraftChange(value)
                    },
                    textStyle = TextStyle(fontSize = 13.5.sp, color = textPrimary),
                    cursorBrush = SolidColor(RoseRed),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            val canSend = draft.isNotBlank() && !isSending
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (canSend) RoseRed else RoseRed.copy(alpha = 0.35f))
                    .clickable(enabled = canSend) { onSubmit() }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = stringResource(id = R.string.comment_send),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/** 评论时间：一小时内显示相对时间，更早显示「日期 + 时分」。 */
private fun formatCommentTime(context: Context, raw: String): String {
    if (raw.isBlank()) return ""
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).parse(raw)
        if (parsed == null) {
            raw.take(16)
        } else {
            val diffMinutes = (System.currentTimeMillis() - parsed.time) / 60_000L
            when {
                diffMinutes < 1 -> context.getString(R.string.comment_time_just_now)
                diffMinutes < 60 -> context.getString(R.string.comment_time_minutes_format, diffMinutes)
                diffMinutes < 24 * 60 -> context.getString(R.string.comment_time_hours_format, diffMinutes / 60)
                diffMinutes < 7 * 24 * 60 -> context.getString(R.string.comment_time_days_format, diffMinutes / (24 * 60))
                else -> SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(parsed.time))
            }
        }
    } catch (e: Exception) {
        raw.take(16)
    }
}
