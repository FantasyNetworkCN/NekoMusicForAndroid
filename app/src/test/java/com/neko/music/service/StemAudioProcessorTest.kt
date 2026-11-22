@file:Suppress("DEPRECATION")

package com.neko.music.service

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessingPipeline
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.common.collect.ImmutableList
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StemAudioProcessorTest {
    private val format = AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)

    private fun pcm(frames: Int, sample: Short = 6000): ByteBuffer =
        ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder()).apply {
            repeat(frames * 2) { putShort(sample) }
            flip()
        }

    private fun pipeline(control: StemProcessingControl): AudioProcessingPipeline =
        AudioProcessingPipeline(ImmutableList.of<AudioProcessor>(StemAudioProcessor(control))).also {
            it.configure(format)
            it.flush()
        }

    private fun drain(output: AudioProcessingPipeline): ByteArray {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val buffer = output.output
            if (!buffer.hasRemaining()) break
            bytes.write(ByteArray(buffer.remaining()).also(buffer::get))
        }
        return bytes.toByteArray()
    }

    @Test
    fun serverChunkAppliesBothGainsWithoutRestartingPipeline() {
        val track = StemTrackCache("music:1:hq")
        track.ready.complete(Unit)
        track.put(44_100, StemCachedChunk(
            0,
            vocals = StemCachedSamples.fromFloats(FloatArray(882 * 2) { 4000 / 32768f }),
            accompaniment = StemCachedSamples.fromFloats(FloatArray(882 * 2) { 2000 / 32768f }),
        ))
        val control = Control(StemTrackInference(track))
        val output = pipeline(control)
        try {
            control.state.value = control.state.value.copy(vocals = 0f, instrumental = 1f)
            output.queueInput(pcm(441))

            val instrumentsOnly = drain(output)
            val buffer = ByteBuffer.wrap(instrumentsOnly).order(ByteOrder.nativeOrder())
            while (buffer.hasRemaining()) assertEquals(2000, buffer.short.toInt())

            control.state.value = control.state.value.copy(vocals = 1f, instrumental = 1f)
            output.queueInput(pcm(441))
            val fullMix = drain(output)
            val mixed = ByteBuffer.wrap(fullMix).order(ByteOrder.nativeOrder())
            while (mixed.hasRemaining()) assertEquals(6000, mixed.short.toInt())
        } finally {
            output.reset()
        }
    }

    @Test
    fun missingChunkFallsBackToOriginalAndLaterChunkTakesEffect() {
        val track = StemTrackCache("music:2:hq")
        track.ready.complete(Unit)
        val control = Control(StemTrackInference(track))
        val output = pipeline(control)
        try {
            output.queueInput(pcm(100))
            val fallback = ByteBuffer.wrap(drain(output)).order(ByteOrder.nativeOrder())
            while (fallback.hasRemaining()) assertEquals(6000, fallback.short.toInt())

            track.put(44_100, StemCachedChunk(
                100,
                vocals = StemCachedSamples.fromFloats(FloatArray(100 * 2)),
                accompaniment = StemCachedSamples.fromFloats(FloatArray(100 * 2) { 2000 / 32768f }),
            ))
            control.state.value = control.state.value.copy(vocals = 0f, instrumental = 1f)
            output.queueInput(pcm(100))
            val separated = ByteBuffer.wrap(drain(output)).order(ByteOrder.nativeOrder())
            while (separated.hasRemaining()) assertEquals(2000, separated.short.toInt())
        } finally {
            output.reset()
        }
    }

    @Test
    fun sourceCanAppearAfterPlaybackStarted() {
        val control = Control(null)
        val processor = StemAudioProcessor(control)
        val output = AudioProcessingPipeline(ImmutableList.of<AudioProcessor>(processor)).also {
            it.configure(format)
            it.flush()
        }
        try {
            val passthrough = pcm(50)
            output.queueInput(passthrough)
            assertFalse(passthrough.hasRemaining())
            assertTrue(drain(output).isNotEmpty())

            val track = StemTrackCache("music:3:hq")
            track.ready.complete(Unit)
            track.put(44_100, StemCachedChunk(
                50,
                vocals = StemCachedSamples.fromFloats(FloatArray(50 * 2)),
                accompaniment = StemCachedSamples.fromFloats(FloatArray(50 * 2) { 1000 / 32768f }),
            ))
            control.source = StemTrackInference(track)
            processor.setInputTimestampUs((50L * 1_000_000 + 22_050) / 44_100)
            control.state.value = control.state.value.copy(vocals = 0f, instrumental = 1f)
            output.queueInput(pcm(50))
            val separated = ByteBuffer.wrap(drain(output)).order(ByteOrder.nativeOrder())
            while (separated.hasRemaining()) assertEquals(1000, separated.short.toInt())
        } finally {
            output.reset()
        }
    }

    @Test
    fun pairedStemsReplaceOriginalTogetherWithoutResidual() {
        val track = StemTrackCache("music:4:hq")
        track.ready.complete(Unit)
        track.put(44_100, StemCachedChunk(
            0,
            vocals = StemCachedSamples.fromFloats(FloatArray(100 * 2)),
            accompaniment = StemCachedSamples.fromFloats(FloatArray(100 * 2) { 2000 / 32768f }),
        ))
        val control = Control(StemTrackInference(track)).apply {
            state.value = state.value.copy(vocals = 1f, instrumental = 1f)
        }
        val output = pipeline(control)
        try {
            output.queueInput(pcm(100))
            val mixed = ByteBuffer.wrap(drain(output)).order(ByteOrder.nativeOrder())
            while (mixed.hasRemaining()) assertEquals(2000, mixed.short.toInt())
        } finally {
            output.reset()
        }
    }

    private class Control(var source: StemChunkSource?) : StemProcessingControl {
        override val state = MutableStateFlow(StemSeparationState(enabled = true))
        override val stemSource: StemChunkSource? get() = source
        val phases = mutableListOf<StemStreamPhase>()

        override fun reportStemPhase(source: StemChunkSource?, phase: StemStreamPhase) {
            phases += phase
        }

        override fun setPlaybackSampleRate(sampleRate: Int) = Unit
    }
}
