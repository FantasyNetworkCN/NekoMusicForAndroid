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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.neko.music.R
import com.neko.music.data.api.CommentApi
import com.neko.music.data.api.CommentItem
import com.neko.music.data.api.CommentReply
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

/** 楼层默认直接展示的回复数，其余回复通过「查看更多」展开。 */
private const val COMMENT_INITIAL_REPLY_COUNT = 2

private data class ReplyTarget(val id: Int, val nickname: String)

private data class DeleteTarget(val id: Int, val isFloor: Boolean, val replyCount: Int)

/** 评论页中性色板：保持 YouTube 式平面、克制，不用卡片和重阴影。 */
private data class CommentPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val divider: Color,
    val field: Color,
)

/**
 * 播放页左侧滑出的评论抽屉面板。
 *
 * 打开 / 关闭动画与手势由 [com.neko.music.ui.screens.PlayerScreen] 负责，这里只画玻璃面板本身。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentPage(
    musicId: Int,
    isDarkTheme: Boolean,
    onRequestLogin: () -> Unit,
    modifier: Modifier = Modifier,
    /** 播放页底部控制条预留高度：键盘弹起时用它算出「还需要再往上顶多少」。 */
    bottomReserve: Dp = 196.dp,
    /** 评论总数变化时通知宿主（用于播放页角标）。 */
    onCountChanged: (Int) -> Unit = {},
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
    var isRefreshing by remember(musicId) { mutableStateOf(false) }
    var loadFailed by remember(musicId) { mutableStateOf(false) }
    var draft by remember(musicId) { mutableStateOf("") }
    var replyTarget by remember(musicId) { mutableStateOf<ReplyTarget?>(null) }
    var isSending by remember(musicId) { mutableStateOf(false) }
    var expandedReplyFloors by remember(musicId) { mutableStateOf(emptySet<Int>()) }
    var deleteTarget by remember(musicId) { mutableStateOf<DeleteTarget?>(null) }
    var isDeleting by remember(musicId) { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val palette = CommentPalette(
        primary = if (isDarkTheme) Color.White else Color(0xFF17171A),
        secondary = if (isDarkTheme) Color.White.copy(alpha = 0.62f) else Color(0xFF62626A),
        tertiary = if (isDarkTheme) Color.White.copy(alpha = 0.38f) else Color(0xFF8A8A91),
        divider = if (isDarkTheme) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.06f),
        field = if (isDarkTheme) Color.White.copy(alpha = 0.085f) else Color.Black.copy(alpha = 0.045f),
    )

    suspend fun loadComments(reset: Boolean, force: Boolean = false) {
        if (!force && (isLoading || isLoadingMore)) return
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
        if (musicId < 0) {
            onCountChanged(0)
            return@LaunchedEffect
        }
        loadComments(reset = true)
    }

    LaunchedEffect(totalCount) {
        onCountChanged(totalCount)
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
                // 被回复的楼层可能还没展开全部回复，发完后自动展开，别让用户找不到自己刚发的内容
                if (parentId != null) {
                    comments.firstOrNull { floor ->
                        floor.id == parentId || floor.replies.any { it.id == parentId }
                    }?.let { expandedReplyFloors = expandedReplyFloors + it.id }
                }
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

    // 键盘弹起时，页面底部本来就有 bottomReserve 的留白；只补「键盘比它高出来的那部分」，
    // 否则输入框会被抬起 bottomReserve（输入法与输入框之间露出一大段空隙）。
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val keyboardLift = (imeBottom - bottomReserve).coerceAtLeast(0.dp)

    Column(modifier = modifier.fillMaxSize().padding(bottom = keyboardLift)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(id = R.string.comment_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                color = palette.primary,
            )
            if (totalCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = totalCount.toString(),
                    fontSize = 13.sp,
                    color = palette.tertiary,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(1.dp)
                .background(palette.divider)
        )

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    loadComments(reset = true, force = true)
                    isRefreshing = false
                }
            },
            state = rememberPullToRefreshState(),
            modifier = Modifier.weight(1f),
        ) {
            when {
                isLoading && comments.isEmpty() -> CommentLoadingState(palette = palette)

                // 用 LazyColumn 撑满，空态时也能下拉刷新
                comments.isEmpty() -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            CommentEmptyState(
                                modifier = Modifier
                                    .fillParentMaxSize()
                                    .padding(horizontal = 20.dp),
                                message = when {
                                    musicId < 0 -> stringResource(id = R.string.local_music_cloud_action_unavailable)
                                    loadFailed -> stringResource(id = R.string.comment_load_failed)
                                    else -> stringResource(id = R.string.comment_empty)
                                },
                                showRetry = loadFailed && musicId >= 0,
                                onRetry = { scope.launch { loadComments(reset = true) } },
                                palette = palette,
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 12.dp,
                            bottom = 20.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(items = comments, key = { it.id }) { floor ->
                            CommentFloorCard(
                                floor = floor,
                                palette = palette,
                                showAllReplies = expandedReplyFloors.contains(floor.id),
                                onShowAllReplies = {
                                    expandedReplyFloors = expandedReplyFloors + floor.id
                                },
                                onReply = { target ->
                                    if (isLoggedIn) replyTarget = target else onRequestLogin()
                                },
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
                                        fontSize = 12.5.sp,
                                        color = RoseRed.copy(alpha = 0.9f),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable(enabled = !isLoadingMore) {
                                                scope.launch { loadComments(reset = false) }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                    )

                                    else -> Text(
                                        text = stringResource(id = R.string.comment_no_more),
                                        fontSize = 11.5.sp,
                                        color = palette.tertiary,
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
                palette = palette,
                onRequestLogin = onRequestLogin,
                onSubmit = { submit() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!isDeleting) deleteTarget = null },
            containerColor = if (isDarkTheme) Color(0xFF1C1C20) else Color.White,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(26.dp),
            title = {
                Text(
                    text = stringResource(id = R.string.comment_delete_title),
                    color = palette.primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.15).sp,
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(id = R.string.comment_delete_message),
                        color = palette.secondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    if (target.isFloor && target.replyCount > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(id = R.string.comment_delete_floor_message),
                            color = palette.secondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { deleteComment(target) },
                    enabled = !isDeleting,
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = RoseRed,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = stringResource(id = R.string.delete),
                            color = RoseRed,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteTarget = null },
                    enabled = !isDeleting,
                ) {
                    Text(text = stringResource(id = R.string.cancel), color = palette.secondary)
                }
            },
        )
    }
}

@Composable
private fun CommentLoadingState(palette: CommentPalette) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = RoseRed,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(id = R.string.loading),
                fontSize = 12.sp,
                color = palette.tertiary,
            )
        }
    }
}

@Composable
private fun CommentEmptyState(
    message: String,
    showRetry: Boolean,
    onRetry: () -> Unit,
    palette: CommentPalette,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_comments_glass),
                contentDescription = null,
                tint = palette.tertiary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = palette.secondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (showRetry) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.retry),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RoseRed.copy(alpha = 0.9f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentFloorCard(
    floor: CommentItem,
    palette: CommentPalette,
    showAllReplies: Boolean,
    onShowAllReplies: () -> Unit,
    onReply: (ReplyTarget) -> Unit,
    onDelete: (DeleteTarget) -> Unit,
) {
    val context = LocalContext.current
    val initialReplies = floor.replies.take(COMMENT_INITIAL_REPLY_COUNT)
    val moreReplies = floor.replies.drop(COMMENT_INITIAL_REPLY_COUNT)
    Column(modifier = Modifier.fillMaxWidth()) {
        CommentRow(
            nickname = floor.user?.nickname.orEmpty(),
            userId = floor.user?.id ?: 0,
            content = floor.content,
            createdAt = floor.createdAt,
            ipRegion = floor.ipRegion,
            palette = palette,
            showActions = true,
            canDelete = floor.canDelete,
            avatarSize = 38.dp,
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

        if (initialReplies.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 46.dp, top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                initialReplies.forEach { reply ->
                    CommentReplyRow(
                        reply = reply,
                        palette = palette,
                        onReply = onReply,
                        onDelete = onDelete,
                    )
                }
                if (showAllReplies) {
                    moreReplies.forEach { reply ->
                        CommentReplyRow(
                            reply = reply,
                            palette = palette,
                            onReply = onReply,
                            onDelete = onDelete,
                        )
                    }
                }
                if (!showAllReplies && moreReplies.isNotEmpty()) {
                    Text(
                        text = stringResource(id = R.string.comment_view_more),
                        fontSize = 12.sp,
                        color = RoseRed.copy(alpha = 0.88f),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onShowAllReplies)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentReplyRow(
    reply: CommentReply,
    palette: CommentPalette,
    onReply: (ReplyTarget) -> Unit,
    onDelete: (DeleteTarget) -> Unit,
) {
    val context = LocalContext.current
    CommentRow(
        nickname = reply.user?.nickname.orEmpty(),
        userId = reply.user?.id ?: 0,
        content = reply.content,
        createdAt = reply.createdAt,
        ipRegion = reply.ipRegion,
        palette = palette,
        replyToUser = reply.replyToUser,
        showActions = true,
        canDelete = reply.canDelete,
        avatarSize = 28.dp,
        onReply = {
            onReply(
                ReplyTarget(
                    id = reply.id,
                    nickname = reply.user?.nickname.orEmpty().ifBlank {
                        context.getString(R.string.comment_title)
                    },
                )
            )
        },
        onDelete = { onDelete(DeleteTarget(reply.id, isFloor = false, replyCount = 0)) },
    )
}

@Composable
private fun CommentRow(
    nickname: String,
    userId: Int,
    content: String,
    createdAt: String,
    ipRegion: String,
    palette: CommentPalette,
    showActions: Boolean,
    canDelete: Boolean,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    replyToUser: CommentUser? = null,
    avatarSize: Dp = 34.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val compact = avatarSize < 34.dp
    val meta = buildString {
        append(formatCommentTime(context, createdAt))
        if (ipRegion.isNotBlank()) {
            if (isNotEmpty()) append(" · ")
            append(ipRegion)
        }
    }
    Row(modifier = modifier.fillMaxWidth()) {
        AsyncImage(
            model = UrlConfig.getUserAvatarUrl(userId),
            contentDescription = null,
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(palette.field),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(if (compact) 10.dp else 12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = nickname,
                    fontSize = if (compact) 12.sp else 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.primary.copy(alpha = 0.94f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (meta.isNotBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = meta,
                        fontSize = 11.sp,
                        color = palette.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (replyToUser != null && replyToUser.nickname.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = context.getString(R.string.comment_reply_hint_format, replyToUser.nickname),
                    fontSize = 11.sp,
                    color = RoseRed.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (content.isNotBlank()) {
                Spacer(modifier = Modifier.height(if (compact) 3.dp else 4.dp))
                Text(
                    text = content,
                    fontSize = if (compact) 13.sp else 13.5.sp,
                    color = palette.primary.copy(alpha = 0.92f),
                    lineHeight = if (compact) 18.5.sp else 19.5.sp,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showActions) {
                    Text(
                        text = stringResource(id = R.string.comment_reply),
                        fontSize = 11.5.sp,
                        color = palette.secondary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onReply() }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
                if (canDelete) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDelete() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(id = R.string.delete),
                            tint = palette.tertiary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
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
    palette: CommentPalette,
    onRequestLogin: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            if (!isLoggedIn) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onRequestLogin() }
                        .padding(horizontal = 2.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(id = R.string.comment_login_hint),
                        fontSize = 13.sp,
                        color = palette.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(id = R.string.login_now),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RoseRed.copy(alpha = 0.9f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
                return@Column
            }

            if (replyTarget != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(id = R.string.comment_reply_hint_format, replyTarget.nickname),
                        fontSize = 11.5.sp,
                        color = palette.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(id = R.string.comment_cancel_reply),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RoseRed.copy(alpha = 0.9f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onClearReply() }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp, max = 112.dp)
                        .padding(horizontal = 2.dp, vertical = 11.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    if (draft.isEmpty()) {
                        Text(
                            text = stringResource(id = R.string.comment_input_hint),
                            fontSize = 13.5.sp,
                            color = palette.tertiary,
                        )
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { value ->
                            if (value.length <= COMMENT_MAX_LENGTH) onDraftChange(value)
                        },
                        textStyle = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp, color = palette.primary),
                        cursorBrush = SolidColor(RoseRed),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                val canSend = draft.isNotBlank() && !isSending
                Box(
                    modifier = Modifier
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = canSend) { onSubmit() }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = RoseRed,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = stringResource(
                                id = if (replyTarget != null) R.string.comment_reply else R.string.comment_send
                            ),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (canSend) RoseRed.copy(alpha = 0.92f) else palette.tertiary,
                        )
                    }
                }
            }

            if (draft.isNotEmpty()) {
                val remaining = COMMENT_MAX_LENGTH - draft.length
                Text(
                    text = "${draft.length}/$COMMENT_MAX_LENGTH",
                    fontSize = 10.sp,
                    color = if (remaining < 30) RoseRed else palette.tertiary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, end = 2.dp),
                )
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
