package com.neko.music.util

import com.neko.music.data.manager.TokenManager
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders

/**
 * 已登录用户令牌自动注入（Android 客户端）。
 *
 * 服务端用 `Authorization` 把请求归到具体账号（访问日志 / 安全分析、以及需要身份的接口）。
 * 各 API 类历史上有的自己带头、有的忘了带，这里在 Ktor 插件层统一补齐：
 *
 *   - 登录后所有 Ktor 请求自动带 `Authorization: Bearer <token>`（含未显式带头的接口）；
 *   - 只发给**本站后端**（[NEKO_BACKEND_HOST]），第三方主机（更新检查 / CDN / 天气等）不带，
 *     避免把用户身份发到站外；
 *   - 请求里已显式设置过 `Authorization` 的不覆盖（扫码登录等特殊令牌接口）；
 *   - 未登录 / 已登出不带（Token 由 [TokenManager] 的进程内缓存提供，登出即清空）。
 *
 * 由 [installNekoClientHeader] 一并安装，因此所有 Ktor 客户端自动获得该能力，
 * 与防重放 nonce（[installNekoReplayProtection]）同层、互不干扰。
 * 媒体流与安装包走 ExoPlayer / DownloadManager，属于公开静态资源，不注入令牌。
 */
val NekoUserTokenPlugin: ClientPlugin<Unit> = createClientPlugin("NekoUserToken") {
    on(Send) { request ->
        val token = TokenManager.processToken()
        if (token.isNullOrEmpty()) return@on proceed(request)
        if (request.url.build().host != NEKO_BACKEND_HOST) return@on proceed(request)
        if (request.headers[HttpHeaders.Authorization] != null) return@on proceed(request)

        val copy = HttpRequestBuilder().takeFrom(request)
        copy.headers.append(HttpHeaders.Authorization, "Bearer $token")
        proceed(copy)
    }
}

/** 给该 [HttpClientConfig] 安装用户令牌注入（由 [installNekoClientHeader] 调用）。 */
fun HttpClientConfig<*>.installNekoUserToken() {
    install(NekoUserTokenPlugin)
}
