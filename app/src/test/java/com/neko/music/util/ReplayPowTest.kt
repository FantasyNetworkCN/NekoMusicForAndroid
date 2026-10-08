package com.neko.music.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * 防重放挑战解题器测试：客户端解出的 proof 必须能被服务端的判定规则接受
 * （`SHA-256(seed + ":" + proof)` 的前导零比特数达到难度）。
 *
 * <p>这里按服务端契约独立重算一次哈希，不依赖被测实现自身的判断。</p>
 */
class ReplayPowTest {

    private fun leadingZeroBits(hash: ByteArray): Int {
        var bits = 0
        for (byte in hash) {
            val value = byte.toInt() and 0xFF
            if (value == 0) {
                bits += 8
                continue
            }
            bits += Integer.numberOfLeadingZeros(value) - 24
            break
        }
        return bits
    }

    private fun digest(seed: String, counter: String): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest("$seed:$counter".toByteArray(Charsets.UTF_8))

    @Test
    fun proofMeetsRequestedDifficulty() {
        for (difficulty in intArrayOf(0, 4, 8, 12)) {
            val seed = "c0ffee$difficulty"
            val proof = solveProof(seed, difficulty)
            assertTrue("proof 必须是十进制计数：$proof", proof.all { it in '0'..'9' })
            assertTrue(
                "难度 $difficulty 未被满足：$proof",
                leadingZeroBits(digest(seed, proof)) >= difficulty,
            )
        }
    }

    @Test
    fun sameSeedAlwaysYieldsUsableProof() {
        val first = solveProof("deadbeef", 10)
        val second = solveProof("deadbeef", 10)
        assertTrue(leadingZeroBits(digest("deadbeef", first)) >= 10)
        assertTrue(leadingZeroBits(digest("deadbeef", second)) >= 10)
    }

    @Test
    fun illegalDifficultyIsRejected() {
        for (difficulty in intArrayOf(-1, 65, 1_000)) {
            val thrown = runCatching { solveProof("seed", difficulty) }.exceptionOrNull()
            assertTrue("难度 $difficulty 应被拒绝", thrown is IllegalArgumentException)
        }
    }

    @Test
    fun meetsDifficultyMatchesIndependentCount() {
        // 全零摘要只有前 64 位有意义地比较，超出的位数一律视为不满足
        val zeros = ByteArray(32)
        assertTrue(meetsDifficulty(zeros, 0))
        assertTrue(meetsDifficulty(zeros, 16))
        assertTrue(meetsDifficulty(zeros, 32))
        val hash = digest("合同", "0")
        assertEquals(
            leadingZeroBits(hash) >= 9,
            meetsDifficulty(hash, 9),
        )
    }
}
