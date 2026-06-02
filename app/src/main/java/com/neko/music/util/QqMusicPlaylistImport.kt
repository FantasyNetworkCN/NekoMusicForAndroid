package com.neko.music.util

/**
 * 从 QQ 音乐歌单分享链接中解析歌单 ID。
 *
 * 支持示例：
 * - https://i2.y.qq.com/n3/other/pages/details/playlist.html?id=歌单id&hosteuin=
 * - https://i2.y.qq.com/n3/other/pages/details/playlist.html?platform=11&appshare=android_qq&appversion=20010508&hosteuin=NK-xxx&id=歌单id&ADTAG=wxfshare
 */
object QqMusicPlaylistImport {
    private val IdQueryParam = Regex("""[?&]id=(\d+)""", RegexOption.IGNORE_CASE)

    /**
     * @return 解析到的纯数字歌单 ID；无法解析则返回 null
     */
    fun parsePlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.all { it.isDigit() }) return trimmed
        return IdQueryParam.find(trimmed)?.groupValues?.getOrNull(1)
    }

    /** 无法解析时原样返回，便于继续编辑。 */
    fun normalizePlaylistIdInput(input: String): String = parsePlaylistId(input) ?: input
}

