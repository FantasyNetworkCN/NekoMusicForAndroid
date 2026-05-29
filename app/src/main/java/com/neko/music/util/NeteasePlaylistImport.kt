package com.neko.music.util

/**
 * 从网易云歌单分享文案或链接中解析歌单 ID。
 * 示例：`https://music.163.com/m/playlist?id=7244101195&creatorId=...`
 */
object NeteasePlaylistImport {

    private val PlaylistQueryId = Regex("""playlist\?id=(\d+)""", RegexOption.IGNORE_CASE)
    private val UrlIdParam = Regex("""[?&]id=(\d+)""", RegexOption.IGNORE_CASE)
    private val PlaylistPathId = Regex("""playlist/(\d+)""", RegexOption.IGNORE_CASE)

    fun parsePlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.all { it.isDigit() }) return trimmed

        PlaylistQueryId.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        UrlIdParam.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        PlaylistPathId.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        return null
    }

    /** 粘贴分享全文时提取 ID；无法解析则保留原输入便于继续编辑。 */
    fun normalizePlaylistIdInput(input: String): String = parsePlaylistId(input) ?: input
}
