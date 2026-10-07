package com.neko.music.util

import android.app.DownloadManager
import android.net.Uri
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.ResolvingDataSource
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
 *
 * 与此同时，[installNekoClientHeader] 会一并安装通用请求防重放（见 [NekoReplayNoncePlugin]）：
 * 所有 Ktor 请求自动携带一次性 `X-Neko-Nonce`，被判定为重放时自动换新 nonce 重试一次。
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

/**
 * 给该 HttpClient 的所有请求加上 X-Neko-Client 与 User-Agent 标头，并安装通用防重放能力。
 *
 * 所有 Ktor 客户端都必须调用本函数（`build.gradle.kts` 的 `verifyNekoClientHeader` 会校验），
 * 因此防重放无需在各处重复接入。
 */
fun HttpClientConfig<*>.installNekoClientHeader() {
    defaultRequest {
        // 请求内已显式设置同名标头时以请求为准（DefaultRequest 不会覆盖已有标头）
        NEKO_REQUEST_HEADERS.forEach { (name, value) -> header(name, value) }
    }
    installNekoReplayProtection()
}

/**
 * ExoPlayer 数据源工厂：流媒体（音频）请求同样携带客户端标识标头。
 *
 * 音质接口 `/api/music/file/{id}` 现在返回 `200` + JSON，不能直接交给播放器；这里用
 * [ResolvingDataSource] 在加载线程上先把它解析成固定媒体地址（`/media/music/...`）再取字节。
 * 解析请求本身同样走 Ktor 客户端，自动携带 nonce。
 */
fun nekoHttpDataSourceFactory(): ResolvingDataSource.Factory {
    val upstream = DefaultHttpDataSource.Factory()
        .setDefaultRequestProperties(NEKO_REQUEST_HEADERS)
    return ResolvingDataSource.Factory(upstream) { dataSpec ->
        val uri = dataSpec.uri.toString()
        if (MusicUrlResolver.isMusicFileApiUrl(uri)) {
            val resolved = MusicUrlResolver.resolveBlocking(uri)
            if (resolved != uri) {
                dataSpec.withUri(Uri.parse(resolved))
            } else {
                dataSpec
            }
        } else {
            dataSpec
        }
    }
}

/** 系统 DownloadManager 请求同样携带客户端标识标头。 */
fun DownloadManager.Request.withNekoClientHeaders(): DownloadManager.Request =
    apply {
        NEKO_REQUEST_HEADERS.forEach { (name, value) -> addRequestHeader(name, value) }
    }
