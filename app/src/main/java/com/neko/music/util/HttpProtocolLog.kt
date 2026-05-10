package com.neko.music.util

import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.HttpResponse

/** 追加到已有 log 文案，例如 ` [protocol=HTTP/2.0]` */
fun HttpResponse.protocolLogSuffix(): String = " [protocol=$version]"

/** 从 Ktor [ResponseException]（含 cause 链）解析出协议；没有则返回空串。 */
fun Throwable.protocolLogSuffixOrEmpty(): String {
    var t: Throwable? = this
    while (t != null) {
        if (t is ResponseException) {
            return t.response.protocolLogSuffix()
        }
        t = t.cause
    }
    return ""
}
