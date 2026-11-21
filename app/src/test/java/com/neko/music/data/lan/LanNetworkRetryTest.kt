package com.neko.music.data.lan

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanNetworkRetryTest {
    @Test
    fun deniedDatagramSendDoesNotEscapeAndCanRecover() = runBlocking {
        var attempts = 0
        val failures = mutableListOf<Exception>()
        retryLanNetworkOperation(0, failures::add) {
            if (++attempts <= 2) throw IOException("sendto failed: EPERM (Operation not permitted)")
        }
        assertEquals(3, attempts)
        assertEquals(2, failures.size)
        assertTrue(failures.all { it is IOException })
    }

    @Test
    fun revokedPermissionAlsoAllowsLaterRecovery() = runBlocking {
        var attempts = 0
        val failures = mutableListOf<Exception>()
        retryLanNetworkOperation(0, failures::add) {
            if (++attempts == 1) throw SecurityException("局域网权限已撤销")
        }
        assertEquals(2, attempts)
        assertTrue(failures.single() is SecurityException)
    }

    @Test
    fun cancellationIsNeverReportedAsNetworkFailure() = runBlocking {
        val cancelled = CancellationException("设备发现已停止")
        var reported = false
        try {
            retryLanNetworkOperation(0, { reported = true }) { throw cancelled }
            error("取消异常应当向上传递")
        } catch (actual: CancellationException) {
            assertTrue(actual === cancelled)
        }
        assertTrue(!reported)
    }

    @Test
    fun stopDuringBackoffDoesNotOpenAnotherSocket() = runBlocking {
        val failed = CompletableDeferred<Unit>()
        val attempts = AtomicInteger()
        val job = launch {
            retryLanNetworkOperation(60_000, { failed.complete(Unit) }) {
                attempts.incrementAndGet()
                throw IOException("网络不可达")
            }
        }
        withTimeout(1_000) {
            failed.await()
            job.cancelAndJoin()
        }
        assertEquals(1, attempts.get())
    }

    @Test
    fun programmingErrorsAreNotRetriedAsNetworkFailures() = runBlocking {
        var attempts = 0
        try {
            retryLanNetworkOperation(0, { error("不应归类为网络异常") }) {
                attempts++
                throw IllegalStateException("无效状态")
            }
            error("程序错误应当向上传递")
        } catch (actual: IllegalStateException) {
            assertEquals("无效状态", actual.message)
        }
        assertEquals(1, attempts)
    }
}
