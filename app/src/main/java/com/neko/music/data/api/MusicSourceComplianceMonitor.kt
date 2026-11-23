package com.neko.music.data.api

import android.content.Context
import com.neko.music.data.manager.TokenManager
import com.neko.music.debug.NekoLog as Log
import com.neko.music.util.UrlConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 非官方连接源的持续合规检查。
 *
 * 启动后稍等片刻先校验一次，之后每 6 小时重新读取清单并调用 GitHub API 核验仓库公开状态
 * 与 AGPL 许可证。任一次失败都会退回官方源并清除该非官方源的登录态。
 */
object MusicSourceComplianceMonitor {
    private const val INITIAL_DELAY_MS = 30_000L
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @Synchronized
    fun restart(context: Context) {
        job?.cancel()
        job = null
        if (UrlConfig.isUsingDefaultBaseUrl()) return
        val appContext = context.applicationContext
        job = scope.launch {
            try {
                delay(INITIAL_DELAY_MS)
                while (isActive) {
                    val checkedBase = UrlConfig.getBaseUrl()
                    if (UrlConfig.isUsingDefaultBaseUrl()) return@launch
                    val result = MusicSourceVerifier.verify(checkedBase)
                    if (result.isFailure) {
                        Log.w(
                            "MusicSourceCompliance",
                            "非官方音乐连接源持续校验失败，退回官方源",
                            result.exceptionOrNull(),
                        )
                        withContext(Dispatchers.Main.immediate) {
                            if (UrlConfig.getBaseUrl() == checkedBase) {
                                UrlConfig.resetToDefault(appContext)
                                TokenManager(appContext).clearToken()
                            }
                        }
                        return@launch
                    }
                    delay(CHECK_INTERVAL_MS)
                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }
}
