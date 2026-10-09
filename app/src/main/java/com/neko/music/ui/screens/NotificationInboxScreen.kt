package com.neko.music.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.neko.music.R
import com.neko.music.data.api.NotificationApi
import com.neko.music.data.api.NotificationItem
import com.neko.music.data.manager.NotificationCenter
import com.neko.music.ui.components.AppPageBackgroundImage
import com.neko.music.ui.components.GlassSurface
import com.neko.music.ui.components.LiquidGlassDefaults
import com.neko.music.ui.components.LocalLiquidLayerBackdrop
import com.neko.music.ui.components.PlaylistPageDarkTintOverlay
import com.neko.music.ui.components.rememberLiquidPageBackdrop
import com.neko.music.ui.theme.RoseRed
import com.neko.music.ui.theme.isAppDarkTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 每页条数，与后端契约（1~50）一致 */
private const val NOTIFICATION_PAGE_SIZE = 20

/** 距列表末尾还剩几条就预取下一页 */
private const val NOTIFICATION_PREFETCH_THRESHOLD = 3

/**
 * 站内消息收件箱。
 *
 * 未读数只有一个来源：[NotificationCenter] 的长连接帧与「标记已读」响应，**不做轮询**。
 * 打开页面即把已展示的未读消息标记已读，并把服务端算出的未读数回写到徽标。
 */
@Composable
fun NotificationInboxScreen(
    onBackClick: () -> Unit,
    onMessageClick: (NotificationItem) -> Unit,
    onLoginClick: () -> Unit,
    isLoggedIn: Boolean,
    token: String?,
) {
    val scope = rememberCoroutineScope()
    val isDarkTheme = isAppDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val pageBackdrop = rememberLiquidPageBackdrop(scheme.background)
    val glassTint = LiquidGlassDefaults.screenListCard
    val glassBg = glassTint.background(isDarkTheme)
    val glassBorder = glassTint.border(isDarkTheme)
    val glassHighlight = glassTint.highlight(isDarkTheme)

    val api = remember(token) { NotificationApi(token) }
    val state = remember(api) { NotificationInboxState(api) }
    val listState = rememberLazyListState()

    LaunchedEffect(api, isLoggedIn) {
        if (isLoggedIn) state.loadFirst() else state.isLoading = false
    }

    // 快滑到底部时翻页；loadMore 内部对重复调用与「没有下一页」做了短路
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= state.items.size - NOTIFICATION_PREFETCH_THRESHOLD) {
                    state.loadMore()
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(pageBackdrop)
        ) {
            AppPageBackgroundImage(modifier = Modifier.fillMaxSize())
            PlaylistPageDarkTintOverlay()
        }

        CompositionLocalProvider(LocalLiquidLayerBackdrop provides pageBackdrop) {
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
                    horizontalArrangement = Arrangement.SpaceBetween,
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

                    Text(
                        text = stringResource(id = R.string.notifications_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDarkTheme) Color(0xFFF0F0F5).copy(alpha = 0.95f) else scheme.onSurface
                    )

                    Spacer(modifier = Modifier.size(48.dp))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        !isLoggedIn -> InboxPlaceholder(
                            message = stringResource(id = R.string.please_login_first_short),
                            actionText = stringResource(id = R.string.login_now),
                            onAction = onLoginClick
                        )

                        state.isLoading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = RoseRed)
                        }

                        state.loadFailed -> InboxPlaceholder(
                            message = stringResource(id = R.string.load_failed),
                            actionText = stringResource(id = R.string.retry),
                            onAction = { scope.launch { state.loadFirst() } }
                        )

                        state.items.isEmpty() -> InboxPlaceholder(
                            message = stringResource(id = R.string.notifications_empty)
                        )

                        else -> LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            // 底部给迷你播放器 + 底栏留位，否则最后几条会被盖住
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 200.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.items, key = { it.id }) { item ->
                                NotificationCard(
                                    item = item,
                                    onClick = { onMessageClick(item) },
                                    glassBg = glassBg,
                                    glassBorder = glassBorder,
                                    glassHighlight = glassHighlight
                                )
                            }

                            if (state.loadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = RoseRed,
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
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
private fun InboxPlaceholder(
    message: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionText != null && onAction != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onAction) {
                    Text(text = actionText, color = RoseRed)
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    item: NotificationItem,
    onClick: () -> Unit,
    glassBg: Float,
    glassBorder: Float,
    glassHighlight: Float
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        backgroundAlpha = glassBg,
        borderAlpha = glassBorder,
        highlightAlpha = glassHighlight
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(RoseRed.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = RoseRed,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title.ifBlank { stringResource(id = R.string.notifications_title) },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!item.read) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(RoseRed)
                        )
                    }
                }

                if (item.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.body,
                        fontSize = 13.sp,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatInboxTime(context, item.createdAt.orEmpty()),
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/** 相对时间，复用评论区的文案；解析不了就原样截断展示 */
private fun formatInboxTime(context: Context, raw: String): String {
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

/**
 * 收件箱分页状态，只在页面存活期间持有。
 *
 * 标记已读只针对**已展示**的那一页，避免「打开页面就把没看到的全标已读」。
 */
private class NotificationInboxState(private val api: NotificationApi) {

    var items by mutableStateOf<List<NotificationItem>>(emptyList())
    var isLoading by mutableStateOf(true)
    var loadFailed by mutableStateOf(false)
    var hasMore by mutableStateOf(false)
    var loadingMore by mutableStateOf(false)

    suspend fun loadFirst() {
        isLoading = true
        loadFailed = false
        val page = api.list(limit = NOTIFICATION_PAGE_SIZE)
        if (page == null) {
            loadFailed = true
            isLoading = false
            return
        }
        items = page.items
        hasMore = page.hasMore
        isLoading = false
        markShownRead(page.items)
    }

    suspend fun loadMore() {
        if (loadingMore || !hasMore) return
        val last = items.lastOrNull() ?: return
        loadingMore = true
        val page = api.list(before = last.id, limit = NOTIFICATION_PAGE_SIZE)
        if (page != null) {
            items = items + page.items
            hasMore = page.hasMore
            markShownRead(page.items)
        }
        loadingMore = false
    }

    private suspend fun markShownRead(shown: List<NotificationItem>) {
        val unreadIds = shown.filter { !it.read }.map { it.id }
        if (unreadIds.isEmpty()) return
        val unread = api.markRead(unreadIds) ?: return
        items = items.map { if (unreadIds.contains(it.id)) it.copy(read = true) else it }
        NotificationCenter.updateUnread(unread)
    }
}
