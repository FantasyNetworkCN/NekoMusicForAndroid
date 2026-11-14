package com.neko.music.debug

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.neko.music.MainActivity
import com.neko.music.BuildConfig
import com.neko.music.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debug 构建专用的系统级日志悬浮窗。
 *
 * 通过 TYPE_APPLICATION_OVERLAY 显示在 NekoMusic 与其他应用之上；点击 LOG 气泡可展开完整
 * 日志面板，并支持筛选、暂停、复制、清空以及导出后通过系统分享面板发送。
 */
class DebugLogOverlayService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private lateinit var windowManager: WindowManager

    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var bubbleLabel: TextView? = null
    private var statusLabel: TextView? = null
    private var logText: TextView? = null
    private var logScroll: ScrollView? = null

    private var bubbleX = -1
    private var bubbleY = -1
    private var paused = false
    private var minPriority = ALL_LEVELS
    private var frozenEntries: List<DebugLogEntry>? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        createNotificationChannel()
        serviceScope.launch {
            DebugLogStore.changeRevision.collect {
                // StateFlow 在挂起期间会自动合并高频日志；轻微节流避免日志洪峰持续阻塞主线程。
                delay(100)
                if (paused) return@collect
                if (logText != null) {
                    updatePanelContent()
                } else {
                    updateBubbleLabel()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!canDrawOverlays()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        if (overlayView == null) {
            showBubble()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        removeOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun showBubble() {
        removeOverlay()
        if (bubbleX < 0 || bubbleY < 0) {
            bubbleX = resources.displayMetrics.widthPixels - dp(72f)
            bubbleY = dp(180f)
        }

        val bubble = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            background = roundedDrawable(0xF2E84368.toInt(), 28f)
            elevation = dp(8f).toFloat()
            minWidth = dp(56f)
            minHeight = dp(56f)
            setPadding(dp(8f), dp(6f), dp(8f), dp(6f))
            contentDescription = "打开 NekoMusic 调试日志"
            bubbleLabel = this
            updateBubbleLabel()
            setOnClickListener { showPanel() }
            installDragAndClick(this)
        }

        val params = overlayParams(
            width = WindowManager.LayoutParams.WRAP_CONTENT,
            height = WindowManager.LayoutParams.WRAP_CONTENT,
            gravity = Gravity.TOP or Gravity.START,
        ).apply {
            x = bubbleX
            y = bubbleY
        }
        addOverlayView(bubble, params)
    }

    private fun installDragAndClick(view: View) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var dragging = false

        view.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        val screenWidth = resources.displayMetrics.widthPixels
                        val screenHeight = resources.displayMetrics.heightPixels
                        params.x = (startX + dx.toInt()).coerceIn(0, screenWidth - dp(56f))
                        params.y = (startY + dy.toInt())
                            .coerceIn(dp(24f), screenHeight - dp(80f))
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        bubbleX = params.x
                        bubbleY = params.y
                    } else {
                        view.performClick()
                    }
                }
            }
            true
        }
    }

    private fun showPanel() {
        removeOverlay()

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val panelWidth = (screenWidth - dp(20f)).coerceAtLeast(dp(280f))
        val panelHeight = (screenHeight * 0.74f).toInt().coerceAtLeast(dp(360f))
        val params = overlayParams(
            width = panelWidth,
            height = panelHeight,
            gravity = Gravity.CENTER,
        )
        addOverlayView(createPanel(), params)
    }

    private fun createPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
            background = roundedDrawable(0xF51A1A24.toInt(), 20f)
            elevation = dp(14f).toFloat()
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val title = TextView(this).apply {
            text = "调试日志"
            setTextColor(Color.WHITE)
            textSize = 17f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }
        statusLabel = TextView(this).apply {
            setTextColor(0xFFB9B9C8.toInt())
            textSize = 11f
        }
        titleColumn.addView(title)
        titleColumn.addView(statusLabel)
        titleRow.addView(
            titleColumn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        titleRow.addView(actionView("收起", selected = false) { showBubble() })
        panel.addView(titleRow)

        val actionScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addFilterActions(actionRow)
        actionRow.addView(actionView(if (paused) "继续" else "暂停") {
            if (paused) {
                paused = false
                frozenEntries = null
            } else {
                paused = true
                frozenEntries = DebugLogStore.snapshot()
            }
            showPanel()
        })
        actionRow.addView(actionView("复制") {
            val entries = currentEntries()
            if (entries.isNotEmpty()) {
                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(
                    ClipData.newPlainText(
                        "NekoMusic debug log",
                        DebugLogStore.toPlainText(entries),
                    ),
                )
                Toast.makeText(this, "日志已复制", Toast.LENGTH_SHORT).show()
            }
        })
        actionRow.addView(actionView("清空") {
            DebugLogStore.clear()
            frozenEntries = if (paused) emptyList() else null
            updatePanelContent()
        })
        actionRow.addView(actionView("导出/发送") {
            val entries = currentEntries()
            if (entries.isNotEmpty()) {
                serviceScope.launch {
                    runCatching {
                        DebugLogExporter.share(this@DebugLogOverlayService, entries)
                    }.onFailure {
                        Toast.makeText(
                            this@DebugLogOverlayService,
                            "导出失败：${it.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        })
        actionScroll.addView(actionRow)
        panel.addView(
            actionScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(4f) },
        )

        logText = TextView(this).apply {
            setTextColor(0xFFE8E8F0.toInt())
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.12f)
            setPadding(dp(8f), dp(6f), dp(8f), dp(10f))
        }
        logScroll = ScrollView(this).apply {
            setBackgroundColor(0xCC0B0B10.toInt())
            addView(
                logText,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        panel.addView(
            logScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ).apply { topMargin = dp(4f) },
        )
        updatePanelContent()
        return panel
    }

    private fun addFilterActions(parent: LinearLayout) {
        data class Filter(val label: String, val priority: Int)
        listOf(
            Filter("全部", ALL_LEVELS),
            Filter("详细", android.util.Log.VERBOSE),
            Filter("调试", android.util.Log.DEBUG),
            Filter("信息", android.util.Log.INFO),
            Filter("警告", android.util.Log.WARN),
            Filter("错误", android.util.Log.ERROR),
        ).forEach { filter ->
            parent.addView(
                actionView(filter.label, selected = minPriority == filter.priority) {
                    minPriority = filter.priority
                    showPanel()
                },
            )
        }
    }

    private fun actionView(
        label: String,
        selected: Boolean = false,
        onClick: () -> Unit,
    ): TextView = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 13f
        setTextColor(if (selected) 0xFFFF7FA2.toInt() else 0xFFD9D9E3.toInt())
        setTypeface(Typeface.DEFAULT, if (selected) Typeface.BOLD else Typeface.NORMAL)
        setPadding(dp(9f), dp(7f), dp(9f), dp(7f))
        background = if (selected) {
            roundedDrawable(0x33FF7FA2, 12f)
        } else {
            null
        }
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun updateBubbleLabel() {
        val count = DebugLogStore.count()
        bubbleLabel?.text = if (count > 0) "日志\n$count" else "日志"
    }

    private fun updatePanelContent() {
        val textView = logText ?: return
        val entries = currentEntries()
        statusLabel?.text = buildString {
            append(if (paused) "已暂停" else "实时刷新")
            append(" · 缓存 ")
            append(entries.size)
            append(" 条")
        }

        val builder = SpannableStringBuilder()
        entries.forEachIndexed { index, entry ->
            val levelStart = builder.length
            builder.append(entry.level.label)
            builder.setSpan(
                ForegroundColorSpan(levelColor(entry.level)),
                levelStart,
                builder.length,
                SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            builder.append(' ')
                .append(timeFormat.format(Date(entry.timestampMillis)))
                .append(' ')
                .append(entry.threadName)
                .append(' ')
                .append(entry.tag)
                .append('\n')
                .append(entry.message)
            if (index != entries.lastIndex) {
                builder.append("\n\n")
            }
        }
        if (entries.isEmpty()) {
            builder.append("暂无日志")
        }
        textView.text = builder
        if (!paused) {
            logScroll?.post { logScroll?.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun currentEntries(): List<DebugLogEntry> {
        val frozen = frozenEntries
        if (paused && frozen != null) {
            return if (minPriority <= android.util.Log.VERBOSE) {
                frozen
            } else {
                frozen.filter { it.priority >= minPriority }
            }
        }
        return DebugLogStore.snapshot(minPriority)
    }

    private fun levelColor(level: DebugLogLevel): Int = when (level) {
        DebugLogLevel.VERBOSE -> 0xFF9AA4B2.toInt()
        DebugLogLevel.DEBUG -> 0xFF65B7F3.toInt()
        DebugLogLevel.INFO -> 0xFF5FD39B.toInt()
        DebugLogLevel.WARN -> 0xFFFFC857.toInt()
        DebugLogLevel.ERROR, DebugLogLevel.ASSERT -> 0xFFFF7B7B.toInt()
    }

    private fun overlayParams(
        width: Int,
        height: Int,
        gravity: Int,
    ): WindowManager.LayoutParams = WindowManager.LayoutParams(
        width,
        height,
        overlayWindowType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        this.gravity = gravity
    }

    private fun addOverlayView(view: View, params: WindowManager.LayoutParams) {
        overlayView = view
        layoutParams = params
        try {
            windowManager.addView(view, params)
        } catch (error: Exception) {
            overlayView = null
            layoutParams = null
            android.util.Log.e(TAG, "显示调试日志悬浮窗失败", error)
            stopSelf()
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        layoutParams = null
        bubbleLabel = null
        statusLabel = null
        logText = null
        logScroll = null
    }

    private fun roundedDrawable(color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "调试工具",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "NekoMusic Debug 日志悬浮窗"
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_message)
            .setContentTitle("NekoMusic 调试日志")
            .setContentText("全局日志悬浮窗运行中")
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val TAG = "DebugLogOverlay"
        private const val NOTIFICATION_ID = 0x4E4501
        private const val NOTIFICATION_CHANNEL_ID = "neko_debug_log_overlay"
        private const val ALL_LEVELS = Int.MIN_VALUE

        @Volatile
        private var permissionRequestedThisProcess = false

        /**
         * 已授权时启动系统悬浮窗；未授权时仅首次自动打开系统授权页。
         * 从授权页返回后由 Activity.onResume 再次调用（requestPermission=false）即可自动启动。
         */
        fun ensureRunning(activity: Activity, requestPermission: Boolean) {
            if (!BuildConfig.DEBUG) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !Settings.canDrawOverlays(activity)
            ) {
                if (requestPermission && !permissionRequestedThisProcess) {
                    permissionRequestedThisProcess = true
                    Toast.makeText(
                        activity,
                        "请允许 NekoMusic 显示在其他应用上层，以启用全局日志悬浮窗",
                        Toast.LENGTH_LONG,
                    ).show()
                    runCatching {
                        activity.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${activity.packageName}"),
                            ),
                        )
                    }
                }
                return
            }

            runCatching {
                activity.startService(
                    Intent(activity, DebugLogOverlayService::class.java),
                )
            }.onFailure {
                android.util.Log.e(TAG, "启动调试日志悬浮窗失败", it)
            }
        }
    }
}
