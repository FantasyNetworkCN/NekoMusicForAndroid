package com.neko.music.service

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StemModelRepositoryTest {
    private val model = byteArrayOf(1, 2, 3, 4, 5)
    private val hash = MessageDigest.getInstance("SHA-256").digest(model).joinToString("") { "%02x".format(it) }

    @Test fun selectingAiCanDownloadWithoutASongAndReuseTheVerifiedModel() = runBlocking {
        val directory = Files.createTempDirectory("neko-model-test").toFile()
        val file = File(directory, "model.onnx")
        var requests = 0
        val repository = StemModelRepository(file, { destination, progress ->
            requests++
            destination.writeBytes(model)
            progress(model.size.toLong())
        }, model.size.toLong(), hash)
        try {
            assertEquals(StemModelPhase.MISSING, repository.state.value.phase)
            assertEquals(file, repository.ensureAvailable())
            assertEquals(StemModelPhase.READY, repository.state.value.phase)
            assertEquals(1f, repository.state.value.progress, 0f)
            repository.ensureAvailable()
            assertEquals(1, requests)
        } finally { directory.deleteRecursively() }
    }

    @Test fun concurrentModelAndSongRequestsShareOneDownloadAndPublishRealProgress() = runBlocking {
        val directory = Files.createTempDirectory("neko-model-test").toFile()
        val file = File(directory, "model.onnx")
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var requests = 0
        val repository = StemModelRepository(file, { destination, progress ->
            requests++
            progress(2)
            started.complete(Unit)
            release.await()
            destination.writeBytes(model)
            progress(5)
        }, model.size.toLong(), hash)
        try {
            val first = async { repository.ensureAvailable() }
            started.await()
            val second = async { repository.ensureAvailable() }
            assertEquals(StemModelPhase.DOWNLOADING, repository.state.value.phase)
            assertEquals(0.4f, repository.state.value.progress, 0.0001f)
            release.complete(Unit)
            awaitAll(first, second)
            assertEquals(1, requests)
            assertEquals(StemModelPhase.READY, repository.state.value.phase)
        } finally { release.complete(Unit); directory.deleteRecursively() }
    }

    @Test fun checksumFailureShowsFailureAndAllowsRetry() = runBlocking {
        val directory = Files.createTempDirectory("neko-model-test").toFile()
        var invalid = true
        val file = File(directory, "model.onnx")
        val repository = StemModelRepository(file, { destination, _ ->
            destination.writeBytes(if (invalid) byteArrayOf(5, 4, 3, 2, 1) else model)
        }, model.size.toLong(), hash)
        try {
            try { repository.ensureAvailable(); fail("错误模型不能标记为已下载") } catch (_: IllegalStateException) {}
            assertEquals(StemModelPhase.FAILED, repository.state.value.phase)
            assertFalse(file.exists())
            assertTrue(directory.listFiles()!!.isEmpty())
            invalid = false
            repository.ensureAvailable()
            assertEquals(StemModelPhase.READY, repository.state.value.phase)
        } finally { directory.deleteRecursively() }
    }

    @Test fun reopeningUsesExistingModelWithoutNetwork() = runBlocking {
        val directory = Files.createTempDirectory("neko-model-test").toFile()
        val file = File(directory, "model.onnx").apply { writeBytes(model) }
        val repository = StemModelRepository(file, { _, _ -> error("已有完整模型不应再次下载") }, model.size.toLong(), hash)
        try {
            repository.ensureAvailable()
            assertEquals(StemModelPhase.READY, repository.state.value.phase)
        } finally { directory.deleteRecursively() }
    }

    @Test fun cancellationRemovesIncompleteDownloadAndKeepsRetryAvailable() = runBlocking {
        val directory = Files.createTempDirectory("neko-model-test").toFile()
        val started = CompletableDeferred<Unit>()
        val repository = StemModelRepository(File(directory, "model.onnx"), { destination, progress ->
            destination.writeBytes(byteArrayOf(1, 2))
            progress(2)
            started.complete(Unit)
            CompletableDeferred<Unit>().await()
        }, model.size.toLong(), hash)
        try {
            val job = launch { repository.ensureAvailable() }
            started.await()
            job.cancelAndJoin()
            assertEquals(StemModelPhase.MISSING, repository.state.value.phase)
            assertTrue(directory.listFiles()!!.isEmpty())
        } finally { directory.deleteRecursively() }
    }
}
