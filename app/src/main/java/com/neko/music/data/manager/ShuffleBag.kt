package com.neko.music.data.manager

import java.util.Random

/**
 * 洗牌袋（Shuffle Bag）。
 *
 * 随机播放不再"每次都从全库抽一首"，而是把整个曲库洗成一轮顺序后逐个消费：
 * - 一轮之内每首歌恰好播放一次，不会出现"40 首只有四五首在循环"；
 * - 一轮播完才洗下一轮，且新一轮首曲不会紧接着上一轮末曲；
 * - 曲库增删只影响未播部分，不会重置整轮进度；
 * - 游标与历史可持久化（[serialize] / [restore]），重启后继续，而不是又从头几首开始。
 *
 * 本类不依赖 Android SDK，便于单元测试。
 */
class ShuffleBag(private val random: Random = Random()) {

    /** 本轮播放顺序，元素为 Music.id。 */
    private val bag = ArrayList<Int>()

    /** 下一个待弹出的位置。 */
    private var cursor = 0

    /** 播放历史，队尾为当前曲，供"上一首"使用。 */
    private val recent = ArrayDeque<Int>()

    /** 池签名：池内容未变时不重洗，避免 UI 重排导致进度重置。 */
    private var poolSig: String? = null

    private var poolSize = 0

    /** 本轮剩余待播曲目数。 */
    val pendingCount: Int get() = (bag.size - cursor).coerceAtLeast(0)

    /** 本轮总曲目数。 */
    val totalSize: Int get() = bag.size

    /** 当前游标位置（用于日志观察）。 */
    val cursorPosition: Int get() = cursor

    /** 最后播放的曲目 id。 */
    val currentKey: Int? get() = recent.lastOrNull()

    /** 待播队列快照（仅用于测试与日志）。 */
    fun pendingKeys(): List<Int> =
        if (cursor >= bag.size) emptyList() else bag.subList(cursor, bag.size).toList()

    /** 播放历史快照（仅用于测试与日志）。 */
    fun recentKeys(): List<Int> = recent.toList()

    /**
     * 与当前曲库对齐：保留未播部分，把曲库里新出现的曲目洗牌后接到队尾。
     * 幂等，可在每次取歌前调用（曲库未变时只做一次签名比对）。
     */
    fun syncPool(pool: List<Int>) {
        val sig = signatureOf(pool)
        if (sig == poolSig) return
        poolSig = sig
        poolSize = pool.size

        val poolSet = pool.toHashSet()
        val seen = HashSet<Int>()
        val remaining = ArrayList<Int>(bag.size - cursor + 8)
        for (i in cursor until bag.size) {
            val key = bag[i]
            if (key in poolSet && seen.add(key)) remaining.add(key)
        }
        bag.clear()
        bag.addAll(remaining)
        cursor = 0

        // 新增曲目补到队尾；本轮已播过的（recent）不再重复排入
        val played = recent.toHashSet()
        val fresh = ArrayList<Int>()
        for (key in pool) {
            if (seen.add(key) && key !in played) fresh.add(key)
        }
        if (fresh.isNotEmpty()) {
            fresh.shuffle(random)
            bag.addAll(fresh)
        }

        trimRecent()
    }

    /** 查看下一首但不消费（供预加载使用，保证预加载与实际播放是同一首）。 */
    fun peekNext(pool: List<Int>): Int? {
        ensureCycle(pool)
        val index = nextValidIndex(pool.toHashSet())
        return if (index < bag.size) bag[index] else null
    }

    /** 消费下一首：推进游标并记入历史。 */
    fun commitNext(pool: List<Int>): Int? {
        ensureCycle(pool)
        val index = nextValidIndex(pool.toHashSet())
        if (index >= bag.size) return null
        val key = bag[index]
        cursor = index + 1
        pushRecent(key)
        return key
    }

    /**
     * 上一首：沿播放历史回退，并把当前曲插回待播队首，
     * 这样"上一首"之后再按"下一首"能回到刚才那首。
     */
    fun previous(pool: List<Int>): Int? {
        if (recent.size < 2) return null
        syncPool(pool)
        val current = recent.removeLast()
        val target = recent.last()
        removePending(target)
        insertPendingAtCursor(current)
        return target
    }

    /** 用户手动选歌：从待播队列摘掉，并作为当前曲记入历史，避免短期内再次随到。 */
    fun onUserPicked(key: Int, pool: List<Int>) {
        syncPool(pool)
        removePending(key)
        if (recent.lastOrNull() != key) pushRecent(key)
    }

    /** 清空列表时重置。 */
    fun reset() {
        bag.clear()
        cursor = 0
        recent.clear()
        poolSig = null
        poolSize = 0
    }

    /** 序列化为单行字符串，存入 SharedPreferences。 */
    fun serialize(): String =
        cursor.toString() + "|" + bag.joinToString(",") + "|" + recent.joinToString(",")

    /** 从 [serialize] 的结果恢复。 */
    fun restore(state: String?) {
        if (state.isNullOrBlank()) return
        val parts = state.split('|')
        if (parts.size != 3) return
        cursor = (parts[0].toIntOrNull() ?: 0).coerceAtLeast(0)
        bag.clear()
        bag.addAll(parseKeys(parts[1]))
        recent.clear()
        recent.addAll(parseKeys(parts[2]))
        if (cursor > bag.size) cursor = bag.size
        poolSize = maxOf(bag.size, recent.size)
    }

    private fun ensureCycle(pool: List<Int>) {
        if (pool.isEmpty()) {
            bag.clear()
            cursor = 0
            return
        }
        syncPool(pool)
        if (cursor >= bag.size) newCycle(pool)
    }

    private fun newCycle(pool: List<Int>) {
        val keys = LinkedHashSet<Int>()
        for (key in pool) keys.add(key)
        if (keys.isEmpty()) {
            bag.clear()
            cursor = 0
            return
        }
        val shuffled = ArrayList(keys)
        shuffled.shuffle(random)
        // 接缝处理：新一轮第一首不要紧接着上一轮最后一首
        val last = recent.lastOrNull()
        if (shuffled.size > 1 && last != null && shuffled[0] == last) {
            val swapWith = 1 + random.nextInt(shuffled.size - 1)
            val head = shuffled[0]
            shuffled[0] = shuffled[swapWith]
            shuffled[swapWith] = head
        }
        bag.clear()
        bag.addAll(shuffled)
        cursor = 0
    }

    /** 跳过已不在池中的曲目；若整袋都已失效则按当前池重洗一轮兜底。 */
    private fun nextValidIndex(poolSet: Set<Int>): Int {
        var index = cursor
        while (index < bag.size && bag[index] !in poolSet) index++
        if (index >= bag.size && poolSet.isNotEmpty()) {
            newCycle(poolSet.toList())
            index = cursor
            while (index < bag.size && bag[index] !in poolSet) index++
        }
        return index
    }

    private fun pushRecent(key: Int) {
        recent.addLast(key)
        trimRecent()
    }

    /** 历史至少保留一整轮，保证"一轮内不重复"的判定不被裁剪破坏。 */
    private fun trimRecent() {
        val cap = maxOf(MIN_HISTORY, poolSize)
        while (recent.size > cap) recent.removeFirst()
    }

    private fun removePending(key: Int) {
        var index = cursor
        while (index < bag.size) {
            if (bag[index] == key) bag.removeAt(index) else index++
        }
    }

    private fun insertPendingAtCursor(key: Int) {
        val from = cursor.coerceIn(0, bag.size)
        if (bag.subList(from, bag.size).contains(key)) return
        bag.add(from, key)
    }

    /** 顺序无关的池签名：避免仅调整列表顺序就重洗一轮。 */
    private fun signatureOf(pool: List<Int>): String =
        pool.size.toString() + ":" + pool.toHashSet().hashCode()

    private fun parseKeys(raw: String): List<Int> =
        raw.split(',').mapNotNull { it.trim().toIntOrNull() }

    private companion object {
        /** 历史保留下限（小曲库时至少能回退这么多首）。 */
        const val MIN_HISTORY = 64
    }
}
