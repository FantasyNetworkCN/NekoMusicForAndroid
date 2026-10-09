package com.neko.music.data.manager

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.neko.music.MainActivity
import com.neko.music.R
import com.neko.music.data.api.NotificationApi
import com.neko.music.data.api.NotificationItem
import com.neko.music.data.api.NotificationStreamEvent
import com.neko.music.data.api.NotificationStreamHttpException
import com.neko.music.util.UrlConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * 站内消息推送（不接任何第三方推送 SDK）。
 *
 * 复用后端的 SSE 长连接：登录后常驻一条 `/api/user/notifications/stream`，收到新消息就发系统通知。
 * 代价是**进程被杀后收不到**——这时不引入 push SDK 就没有任何唤醒通道；消息本身在服务端有落库，
 * 下次启动时会按游标补拉并提醒一次，不会丢。
 *
 * 断线窗口与 PC 端同一套约定：服务端到点主动断开（最长 300 秒）且不重放，重连后拿 `ready` 里的
 * `latestId` 与本地游标比对，落后就补拉；连接数超限（429）按 `Retry-After` 退避。
 */
object NotificationCenter {

    private const val TAG = "NekoNotifications"

    /** 消息通知渠道（独立于播放通知，用户可单独关闭） */
    private const val CHANNEL_ID = "neko_messages"
    private const val GROUP_KEY = "neko_messages_group"

    /** 汇总通知的固定 id（单条用消息自身 id，避免重复提醒） */
    private const val SUMMARY_NOTIFICATION_ID = 0x4E4B

    private const val PREF_NAME = "notification_center"
    private const val KEY_LAST_SEEN = "last_seen_id"
    /** 没有游标（首次 / 刚登出）时的取值 */
    private const val NO_CURSOR = -1

    private const val RECONNECT_DELAY_MS = 3_000L
    /** 未登录或令牌失效时的空转间隔：期间反复读 Token，等重新登录后自动接上 */
    private const val IDLE_RECHECK_MS = 30_000L
    private const val BACKFILL_LIMIT = 50

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var streamJob: Job? = null
    private var appContext: Context? = null

    private val _unread = MutableStateFlow(0)

    /** 未读数：来自 `ready` 帧、`message` 帧自增与标记已读响应，不做轮询 */
    val unread: StateFlow<Int> = _unread.asStateFlow()

    /** 由 Application 在进程启动时调用一次 */
    fun install(context: Context) {
        val app = context.applicationContext
        appContext = app
        ensureChannel(app)
        ensureStarted()
    }

    /** 登录成功后立即接上，不必等空转间隔 */
    fun onLogin() = ensureStarted()

    /** 登出：断开长连接并清掉游标，避免换账号后串消息 */
    fun onLogout() {
        stop()
        appContext?.let { prefs(it).edit().remove(KEY_LAST_SEEN).apply() }
    }

    fun ensureStarted() {
        if (streamJob?.isActive == true) return
        val app = appContext ?: return
        streamJob = scope.launch { runLoop(app) }
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
        _unread.value = 0
    }

    /** 收件箱标记已读后回写未读数（用服务端返回值，不做本地估算） */
    fun updateUnread(value: Int) {
        _unread.value = value.coerceAtLeast(0)
    }

    // ──────────────────────────── 连接主循环 ────────────────────────────

    private suspend fun runLoop(context: Context) {
        while (currentCoroutineContext().isActive) {
            val token = TokenManager(context).getToken()
            if (token.isNullOrBlank()) {
                delay(IDLE_RECHECK_MS)
                continue
            }

            val api = NotificationApi(token)
            try {
                api.stream { event -> handleEvent(api, event) }
                delay(RECONNECT_DELAY_MS) // 对端正常关闭（或到点断开）：稍等再连
            } catch (e: CancellationException) {
                throw e
            } catch (e: NotificationStreamHttpException) {
                when (e.code) {
                    401, 403 -> {
                        Log.i(TAG, "令牌失效，暂停消息推送，等待重新登录")
                        delay(IDLE_RECHECK_MS)
                    }
                    429 -> delay((e.retryAfterSeconds ?: 30).coerceAtLeast(1) * 1000L)
                    else -> delay(RECONNECT_DELAY_MS)
                }
            } catch (e: Exception) {
                // 网络中断 / 读超时探活到死链：等一会儿重来
                if (currentCoroutineContext().isActive) delay(RECONNECT_DELAY_MS)
            }
        }
    }

    private suspend fun handleEvent(api: NotificationApi, event: NotificationStreamEvent) {
        when (event) {
            is NotificationStreamEvent.Ready -> onReady(api, event)
            is NotificationStreamEvent.Message -> {
                val context = appContext ?: return
                advanceCursor(context, event.item.id)
                if (!event.item.read) _unread.value += 1
                postMessage(context, event.item)
            }
        }
    }

    /**
     * 每次连上都会来一帧 `ready`。
     *
     * 没有游标说明是首次（或刚换账号）：只记基线，避免把历史消息一次性弹出来。
     */
    private suspend fun onReady(api: NotificationApi, ready: NotificationStreamEvent.Ready) {
        val context = appContext ?: return
        _unread.value = ready.unread.coerceAtLeast(0)
        Log.i(TAG, "消息流已连接：未读=${ready.unread}，服务端最新 id=${ready.latestId}")

        val cursor = lastSeenId(context)
        if (cursor == NO_CURSOR) {
            saveCursor(context, ready.latestId)
            return
        }
        if (ready.latestId > cursor)
            backfill(context, api, cursor, ready.latestId)
        saveCursor(context, max(lastSeenId(context), ready.latestId))
    }

    /**
     * 补拉断线（含进程被杀）期间落下的消息：一条照常弹，多条只弹一条汇总。
     *
     * 只认 `(cursor, upTo]`：`upTo` 之上的消息由实时帧负责，否则建连瞬间新落库的那条会被提醒两次。
     */
    private suspend fun backfill(context: Context, api: NotificationApi, cursor: Int, upTo: Int) {
        val page = api.list(since = cursor, limit = BACKFILL_LIMIT) ?: return
        val missed = page.items.filter { it.id > cursor && it.id <= upTo }.sortedBy { it.id }
        if (missed.isEmpty()) return

        saveCursor(context, max(lastSeenId(context), missed.last().id))
        if (missed.size == 1) {
            postMessage(context, missed.first())
            return
        }
        post(
            context = context,
            notificationId = SUMMARY_NOTIFICATION_ID,
            title = context.getString(R.string.notifications_title),
            body = context.getString(R.string.notifications_missed_fmt, missed.size),
            link = missed.last().link,
        )
    }

    // ──────────────────────────── 通知 ────────────────────────────

    private fun postMessage(context: Context, item: NotificationItem) {
        val title = item.title.trim().ifBlank { context.getString(R.string.notifications_title) }
        post(context, item.id, title, item.body.trim(), item.link)
    }

    private fun post(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        link: String,
    ) {
        if (title.isBlank() && body.isBlank()) return
        if (!canNotify(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (link.startsWith("/")) data = Uri.parse(UrlConfig.buildFullUrl(link))
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_message)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_KEY)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // 通知权限被撤销时系统会抛，忽略即可：消息本身在应用内的收件箱里
            Log.w(TAG, "发送消息通知失败", e)
        }
    }

    private fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // 同 ID 重复创建即可，不要 deleteNotificationChannel（Android 14+ 会抛 SecurityException）
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notifications_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notifications_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    // ──────────────────────────── 游标 ────────────────────────────

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun lastSeenId(context: Context): Int =
        prefs(context).getInt(KEY_LAST_SEEN, NO_CURSOR)

    private fun saveCursor(context: Context, id: Int) {
        if (id <= 0) return
        prefs(context).edit().putInt(KEY_LAST_SEEN, id).apply()
    }

    private fun advanceCursor(context: Context, id: Int) {
        if (id > lastSeenId(context).coerceAtLeast(0)) saveCursor(context, id)
    }
}
