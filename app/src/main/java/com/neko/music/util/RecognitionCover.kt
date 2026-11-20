package com.neko.music.util

import com.neko.music.data.model.Music

/** 识曲返回的是站内歌曲，服务端文件路径不能当作本机文件读取。 */
internal fun recognitionCoverUrl(music: Music): String {
    val path = music.coverFilePath?.trim().orEmpty()
    return if (path.startsWith("https://") || path.startsWith("http://")) path
    else UrlConfig.getMusicCoverUrl(music.id)
}
