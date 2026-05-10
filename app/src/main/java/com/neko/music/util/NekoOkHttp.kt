package com.neko.music.util

import okhttp3.OkHttpClient
import okhttp3.Protocol

/**
 * ALPN 优先协商 **HTTP/2**；**HTTP/1.1** 仅作不支持 h2 时的兜底（OkHttp 要求 https 场景下
 * `protocols` 须包含 [Protocol.HTTP_1_1]，不能仅声明 h2）。
 */
fun OkHttpClient.Builder.preferHttp2AlpnOverHttp1() {
    protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
}
