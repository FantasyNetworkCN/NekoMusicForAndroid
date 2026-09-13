package com.neko.music.util

/**
 * 解析酷狗歌单 / 单曲分享链接或分享文案，得到后端可识别的 `listid`。
 *
 * 支持示例：
 * - 特殊歌单数字 ID：`1234567`
 * - 网页版歌单链接：`https://www.kugou.com/yy/special/single/1234567.html`
 * - 含 `specialid=` 的分享链接
 * - 移动端分享链接：`https://m.kugou.com/songlist/gcid_3zmi8f5nz5z0c4/`
 * - 网关全局歌单 ID：`collection_3_12345678_1_0`
 * - 移动端单曲分享链接：`https://m.kugou.com/share/?action=single&hash=...`
 *
 * 酷狗分享形式较多，后端同时接受链接与 ID，因此这里能归一化时提取更精简的
 * `listid`，识别不了就原样交给后端处理。
 */
object KugouMusicPlaylistImport {

    private val SpecialSingleId = Regex("""special/single/(\d+)""", RegexOption.IGNORE_CASE)
    private val SpecialIdParam = Regex("""[?&]specialid=(\d+)""", RegexOption.IGNORE_CASE)
    private val Gcid = Regex("""gcid_[A-Za-z0-9]+""")
    private val GlobalCollectionId = Regex("""collection_[A-Za-z0-9_]+""")
    private val KugouUrl = Regex("""kugou\.com""", RegexOption.IGNORE_CASE)

    /**
     * @return 归一化后的 `listid`；无法识别则返回 null
     */
    fun parseListId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.all { it.isDigit() }) return trimmed

        SpecialSingleId.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        SpecialIdParam.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        GlobalCollectionId.find(trimmed)?.value?.let { return it }
        Gcid.find(trimmed)?.value?.let { return it }
        // 单曲分享链接等其它酷狗链接：原样交给后端解析
        if (KugouUrl.containsMatchIn(trimmed)) return trimmed
        return null
    }

    /** 粘贴分享全文时提取 listid；无法解析则保留原输入便于继续编辑。 */
    fun normalizeListIdInput(input: String): String = parseListId(input) ?: input
}
