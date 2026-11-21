package com.neko.music.service

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtProvider
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.neko.music.debug.NekoLog as Log
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer

internal interface StemInference : Closeable {
    fun instrumental(context: FloatArray, outputFrames: Int): FloatArray
}

/** 使用轻量 UVR_MDXNET_1_9703，所有推理与关闭操作在后台串行执行。 */
internal class MdxStemEngine(file: File) : StemInference {
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    val acceleration: String
    private val dsp = StemDsp(bins = SPECTRUM_BINS, frames = SPECTRUM_FRAMES)

    init {
        val created = createSession(MdxModelWindow.shorten(file.readBytes(), SPECTRUM_FRAMES))
        session = created.first
        acceleration = created.second
        Log.d("MdxStemEngine", "AI 分离执行后端: $acceleration")
        try {
            val shape = (session.inputInfo.getValue("input").info as TensorInfo).shape
            require(shape[0] in longArrayOf(-1, 1) &&
                shape.contentEquals(longArrayOf(shape[0], 4, SPECTRUM_BINS.toLong(), SPECTRUM_FRAMES.toLong()))) {
                "AI 模型输入尺寸不匹配: ${shape.contentToString()}"
            }
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
            spectrum.fill(0f, channel * SPECTRUM_BINS * SPECTRUM_FRAMES, (channel * SPECTRUM_BINS + 3) * SPECTRUM_FRAMES)
        }
        val prediction = OnnxTensor.createTensor(
            environment, FloatBuffer.wrap(spectrum), longArrayOf(1, 4, SPECTRUM_BINS.toLong(), SPECTRUM_FRAMES.toLong())
        ).use { input ->
            session.run(mapOf("input" to input)).use { result ->
                val tensor = result[0] as OnnxTensor
                val buffer = tensor.floatBuffer
                FloatArray(buffer.remaining()).also(buffer::get)
            }
        }
        val decoded = dsp.decode(prediction)
        val vocals = FloatArray(OUTPUT_FRAMES * 2) {
            decoded[TRIM_FRAMES * 2 + it] * 1.03f
        }
        val instrumental = FloatArray(OUTPUT_FRAMES * 2) {
            wave[TRIM_FRAMES * 2 + it] - vocals[it]
        }
        check(vocals.all { it.isFinite() } && instrumental.all { it.isFinite() })
        return resampleStereo(instrumental, outputFrames)
    }

    @Synchronized
    override fun close() = session.close()

    private fun createSession(model: ByteArray): Pair<OrtSession, String> {
        val available = OrtEnvironment.getAvailableProviders()
        val candidates = buildList {
            if (OrtProvider.XNNPACK in available) add(listOf(OrtProvider.XNNPACK))
            if (OrtProvider.NNAPI in available && OrtProvider.XNNPACK in available) {
                add(listOf(OrtProvider.NNAPI, OrtProvider.XNNPACK))
            }
            if (OrtProvider.NNAPI in available) add(listOf(OrtProvider.NNAPI))
            add(emptyList())
        }.distinct()
        var failure: Exception? = null
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
        for (providers in candidates) {
            val options = OrtSession.SessionOptions()
            try {
                options.setInterOpNumThreads(1)
                // 模型固定按 batch=1 推理，移除动态 batch 有助于 NNAPI/XNNPACK 划分图。
                options.setSymbolicDimensionValue("batch_size", 1)
                if (providers.isEmpty()) {
                    options.setIntraOpNumThreads(threads)
                } else {
                    options.setIntraOpNumThreads(1)
                    options.addConfigEntry("session.intra_op.allow_spinning", "0")
                    for (provider in providers) {
                        when (provider) {
                            OrtProvider.NNAPI -> options.addNnapi()
                            OrtProvider.XNNPACK -> options.addXnnpack(
                                mapOf("intra_op_num_threads" to threads.toString())
                            )
                            else -> Unit
                        }
                    }
                }
                val result = environment.createSession(model, options)
                return result to providers.joinToString("+") { it.name }.ifEmpty { "CPU" }
            } catch (error: Exception) {
                failure = error
                Log.w("MdxStemEngine", "AI 执行后端 ${providers.joinToString("+") { it.name }} 不可用，尝试回退", error)
            } finally {
                options.close()
            }
        }
        throw failure ?: IllegalStateException("无法创建 AI 推理会话")
    }

    companion object {
        const val SAMPLE_RATE = 44_100
        const val SPECTRUM_FRAMES = 128
        const val SPECTRUM_BINS = 2048
        const val MODEL_FRAMES = 1024 * (SPECTRUM_FRAMES - 1)
        const val TRIM_FRAMES = 3072
        const val OUTPUT_FRAMES = MODEL_FRAMES - TRIM_FRAMES * 2
    }
}
