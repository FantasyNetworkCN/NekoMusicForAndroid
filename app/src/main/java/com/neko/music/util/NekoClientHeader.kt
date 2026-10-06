package com.neko.music.util

import android.app.DownloadManager
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.neko.music.BuildConfig
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header

/**
 * 客户端标识：本应用所有出站请求（Ktor / ExoPlayer / DownloadManager 等）统一带上
 *   - `X-Neko-Client: android+<版本>`
 *   - `User-Agent: NekoMusic-android/<版本>`
 * 便于后端按来源端与版本做统计、灰度或最低版本拦截。
 * 标头名与取值格式须与 web / pc 端保持一致。
 */
const val NEKO_CLIENT_HEADER = "X-Neko-Client"
const val NEKO_USER_AGENT_HEADER = "User-Agent"

/** X-Neko-Client 取值：`android+<版本>`；版本取自 build.gradle.kts 的 versionName */
val NEKO_CLIENT_VALUE: String = "android+" + BuildConfig.VERSION_NAME

/** User-Agent 取值：`NekoMusic-android/<版本>` */
val NEKO_USER_AGENT_VALUE: String = "NekoMusic-android/" + BuildConfig.VERSION_NAME

/** 所有出站请求统一携带的标头；各网络栈都复用这一份映射，避免取值漂移。 */
val NEKO_REQUEST_HEADERS: Map<String, String> = mapOf(
    NEKO_CLIENT_HEADER to NEKO_CLIENT_VALUE,
    NEKO_USER_AGENT_HEADER to NEKO_USER_AGENT_VALUE,
)

/** 给该 HttpClient 的所有请求加上 X-Neko-Client 与 User-Agent 标头。 */
fun HttpClientConfig<*>.installNekoClientHeader() {
    defaultRequest {
        // 请求内已显式设置同名标头时以请求为准（DefaultRequest 不会覆盖已有标头）
        NEKO_REQUEST_HEADERS.forEach { (name, value) -> header(name, value) }
    }
}

/** ExoPlayer 数据源工厂：流媒体（音频）请求同样携带客户端标识标头。 */
fun nekoHttpDataSourceFactory(): DefaultHttpDataSource.Factory =
    DefaultHttpDataSource.Factory().setDefaultRequestProperties(NEKO_REQUEST_HEADERS)

/** 系统 DownloadManager 请求同样携带客户端标识标头。 */
fun DownloadManager.Request.withNekoClientHeaders(): DownloadManager.Request =
    apply {
        NEKO_REQUEST_HEADERS.forEach { (name, value) -> addRequestHeader(name, value) }
    }
