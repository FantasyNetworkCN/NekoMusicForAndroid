package com.neko.music.data.api

import java.io.IOException

/** 批量匹配时服务端无响应、空 body 或响应截断等，应对用户展示「服务繁忙」。 */
class MusicSearchBusyException(cause: Throwable? = null) : IOException(cause)
