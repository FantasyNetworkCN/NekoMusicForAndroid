package com.neko.music.data.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * 洗牌袋不变式测试：
 * 一轮内零重复且全覆盖、接缝不重复、池变更不重置、持久化可续播、小曲库不退化。
 */
class ShuffleBagTest {

    private val pool = (1..40).toList()

    private fun bag(seed: Long = 20260915L) = ShuffleBag(Random(seed))

    @Test
    fun `一轮内每首歌恰好播放一次`() {
        val bag = bag()
        val played = (0 until pool.size).map { bag.commitNext(pool) }
        assertTrue("不应有空值", played.all { it != null })
        assertEquals("一轮内应恰好覆盖整个曲库", pool.toSet(), played.toSet())
        assertEquals("待播队列应清空", 0, bag.pendingCount)
    }

    @Test
    fun `连续多轮均零重复且接缝不重复`() {
        val bag = bag()
        val sequence = (0 until pool.size * 3).map { bag.commitNext(pool)!! }
        sequence.windowed(pool.size, pool.size).forEachIndexed { index, cycle ->
            assertEquals("第 ${index + 1} 轮应覆盖整个曲库", pool.toSet(), cycle.toSet())
        }
        sequence.zipWithNext().forEach { (first, second) ->
            assertNotEquals("相邻两首不应重复", first, second)
        }
    }

    @Test
    fun `随机顺序不会退化成只有少数几首打头`() {
        val bag = bag()
        val heads = HashSet<Int>()
        repeat(200) {
            heads.add(bag.commitNext(pool)!!)
            repeat(pool.size - 1) { bag.commitNext(pool) }
        }
        assertTrue("200 轮应出现足够多的不同首曲，实际 ${heads.size}", heads.size >= 30)
    }

    @Test
    fun `曲库增加时保留未播部分并纳入新曲`() {
        val bag = bag()
        val played = (0 until 10).map { bag.commitNext(pool)!! }
        val remaining = bag.pendingKeys()

        val grownPool = pool + (41..50).toList()
        bag.syncPool(grownPool)

        assertEquals("未播部分应原样保留", remaining, bag.pendingKeys().take(remaining.size))
        assertEquals("新增曲目应全部进入待播队列", grownPool.toSet(), bag.pendingKeys().toSet() + played.toSet())

        val next = (0 until 30).map { bag.commitNext(grownPool)!! }
        assertTrue("新曲库里的曲目都应可播放", next.all { it in grownPool })
        assertEquals("一轮内不应重复", next.size, next.toSet().size)
    }

    @Test
    fun `曲库删除后跳过失效曲目且不报错`() {
        val bag = bag()
        repeat(5) { bag.commitNext(pool)!! }
        val shrunk = (1..20).toList()
        bag.syncPool(shrunk)

        val pending = bag.pendingKeys()
        assertEquals("待播队列不应重复", pending.size, pending.toSet().size)
        assertTrue("待播队列应全部落在新曲库内", pending.all { it in shrunk })

        val next = (0 until 20).map { bag.commitNext(shrunk) }
        assertTrue("结果应全部落在新曲库内", next.all { it != null && it in shrunk })
        next.zipWithNext().forEach { (a, b) -> assertNotEquals("相邻两首不应重复", a, b) }
    }

    @Test
    fun `序列化后恢复可无缝续播`() {
        val original = bag()
        repeat(7) { original.commitNext(pool)!! }
        val state = original.serialize()

        val restored = ShuffleBag(Random(1L))
        restored.restore(state)
        assertEquals("恢复后待播顺序应一致", original.pendingKeys(), restored.pendingKeys())
        assertEquals("恢复后当前曲应一致", original.currentKey, restored.currentKey)

        val expected = (0 until 12).map { original.commitNext(pool) }
        val actual = (0 until 12).map { restored.commitNext(pool) }
        assertEquals("恢复后的后续播放顺序应与未中断时一致", expected, actual)
    }

    @Test
    fun `损坏的持久化数据不会导致崩溃`() {
        val bag = bag()
        bag.restore(null)
        bag.restore("")
        bag.restore("garbage")
        bag.restore("1|2")
        assertEquals(pool.toSet(), (0 until pool.size).map { bag.commitNext(pool)!! }.toSet())
    }

    @Test
    fun `小曲库行为正确`() {
        val single = bag()
        repeat(5) { assertEquals(7, single.commitNext(listOf(7))) }

        val pair = bag()
        val pairKeys = listOf(1, 2)
        val pairSequence = (0 until 20).map { pair.commitNext(pairKeys)!! }
        pairSequence.zipWithNext().forEach { (a, b) -> assertNotEquals("两首歌时应交替播放", a, b) }

        val triple = bag()
        val tripleKeys = listOf(1, 2, 3)
        val tripleSequence = (0 until 30).map { triple.commitNext(tripleKeys)!! }
        tripleSequence.zipWithNext().forEach { (a, b) -> assertNotEquals("相邻两首不应重复", a, b) }
        tripleSequence.windowed(3, 3).forEach { cycle ->
            assertEquals(tripleKeys.toSet(), cycle.toSet())
        }
    }

    @Test
    fun `预加载只看不消费`() {
        val bag = bag()
        repeat(3) { bag.commitNext(pool)!! }
        val peeked = bag.peekNext(pool)
        assertNotNull(peeked)
        repeat(10) { assertEquals("重复 peek 不应改变结果", peeked, bag.peekNext(pool)) }
        assertEquals("peek 不应消费游标", 3, bag.cursorPosition)
        assertEquals("commit 应给出同一首", peeked, bag.commitNext(pool))
    }

    @Test
    fun `上一首后再下一首可回到原曲`() {
        val bag = bag()
        val first = bag.commitNext(pool)!!
        val second = bag.commitNext(pool)!!
        val third = bag.commitNext(pool)!!

        assertEquals("上一首应回到历史里的前一首", second, bag.previous(pool))
        assertEquals("上一首之后当前曲应为第二首", second, bag.currentKey)
        assertEquals("再按下一首应回到刚才那首", third, bag.commitNext(pool))
        assertEquals("历史里仍有第一首", first, bag.recentKeys().first())
    }

    @Test
    fun `历史不足时上一首返回空`() {
        val bag = bag()
        assertEquals(null, bag.previous(pool))
        bag.commitNext(pool)
        assertEquals(null, bag.previous(pool))
    }

    @Test
    fun `手动点歌不会在本轮被再次随到`() {
        val bag = bag()
        repeat(4) { bag.commitNext(pool)!! }
        val pending = bag.pendingKeys()
        val picked = pending.last()
        bag.onUserPicked(picked, pool)
        assertFalse("手动点过的歌不应留在待播队列", bag.pendingKeys().contains(picked))
        assertEquals("手动点歌应记为当前曲", picked, bag.currentKey)
        assertEquals("重复点同一首不应产生重复历史", 1, bag.recentKeys().count { it == picked })
    }

    @Test
    fun `清空列表时重置状态`() {
        val bag = bag()
        repeat(6) { bag.commitNext(pool)!! }
        bag.reset()
        assertEquals(0, bag.pendingCount)
        assertEquals(null, bag.currentKey)
        assertEquals(pool.toSet(), (0 until pool.size).map { bag.commitNext(pool)!! }.toSet())
    }
}
