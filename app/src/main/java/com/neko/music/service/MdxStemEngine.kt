package com.neko.music.service

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtProvider
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer

internal interface StemInference : Closeable {
    fun instrumental(context: FloatArray, outputFrames: Int): FloatArray
}

/** 固定使用 UVR-MDX-NET-Inst_HQ_3，所有推理与关闭操作在后台串行执行。 */
internal class MdxStemEngine(file: File) : StemInference {
    private val environment = OrtEnvironment.getEnvironment()
    private val session = OrtSession.SessionOptions().use { options ->
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        if (OrtProvider.XNNPACK in OrtEnvironment.getAvailableProviders()) {
            options.setIntraOpNumThreads(1)
            options.addConfigEntry("session.intra_op.allow_spinning", "0")
            options.addXnnpack(mapOf("intra_op_num_threads" to threads.toString()))
        } else {
            options.setIntraOpNumThreads(threads)
        }
        options.setInterOpNumThreads(1)
        environment.createSession(file.absolutePath, options)
    }
    private val dsp = StemDsp(frames = SPECTRUM_FRAMES)

    init {
        try {
            val shape = (session.inputInfo.getValue("input").info as TensorInfo).shape
            require(shape.contentEquals(longArrayOf(-1, 4, 3072, SPECTRUM_FRAMES.toLong())))
        } catch (error: Exception) {
            session.close()
            throw error
        }
    }

    @Synchronized
    override fun instrumental(context: FloatArray, outputFrames: Int): FloatArray {
        val wave = resampleStereo(context, MODEL_FRAMES)
        val spectrum = dsp.encode(wave)
        // 与模型上游推理流程一致，清除最底部三个频率桶。
        for (channel in 0 until 4) {
            spectrum.fill(0f, channel * 3072 * SPECTRUM_FRAMES, (channel * 3072 + 3) * SPECTRUM_FRAMES)
        }
        val prediction = OnnxTensor.createTensor(
            environment, FloatBuffer.wrap(spectrum), longArrayOf(1, 4, 3072, SPECTRUM_FRAMES.toLong())
        ).use { input ->
            session.run(mapOf("input" to input)).use { result ->
                val tensor = result[0] as OnnxTensor
                val buffer = tensor.floatBuffer
                FloatArray(buffer.remaining()).also(buffer::get)
            }
        }
        val decoded = dsp.decode(prediction)
        val trimmed = FloatArray(OUTPUT_FRAMES * 2) {
            decoded[TRIM_FRAMES * 2 + it] * 1.022f
        }
        check(trimmed.all { it.isFinite() })
        return resampleStereo(trimmed, outputFrames)
    }

    @Synchronized
    override fun close() = session.close()

    companion object {
        const val SAMPLE_RATE = 44_100
        const val SPECTRUM_FRAMES = 256
        const val MODEL_FRAMES = 1024 * (SPECTRUM_FRAMES - 1)
        const val TRIM_FRAMES = 3072
        const val OUTPUT_FRAMES = MODEL_FRAMES - TRIM_FRAMES * 2
    }
}
