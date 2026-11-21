package com.neko.music.service

import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.PI
import kotlin.math.cos

/** MDX 的立体声频谱变换；音频按左右声道交错存放，频谱按实部、虚部分开存放。 */
internal class StemDsp(
    private val fftSize: Int = 6144,
    private val hop: Int = 1024,
    private val bins: Int = 3072,
    private val frames: Int = 256,
) {
    val sampleCount = hop * (frames - 1)
    private val fft = FloatFFT_1D(fftSize.toLong())
    private val window = FloatArray(fftSize) { (0.5 - 0.5 * cos(2 * PI * it / fftSize)).toFloat() }
    private val scratch = FloatArray(fftSize * 2)

    fun encode(wave: FloatArray): FloatArray {
        require(wave.size == sampleCount * 2)
        val spectrum = FloatArray(4 * bins * frames)
        for (channel in 0..1) {
            for (frame in 0 until frames) {
                scratch.fill(0f)
                for (i in 0 until fftSize) {
                    var source = frame * hop + i - fftSize / 2
                    if (source < 0) source = -source
                    if (source >= sampleCount) source = 2 * sampleCount - source - 2
                    scratch[i] = wave[source * 2 + channel] * window[i]
                }
                fft.realForwardFull(scratch)
                for (bin in 0 until bins) {
                    spectrum[(channel * 2 * bins + bin) * frames + frame] = scratch[bin * 2]
                    spectrum[((channel * 2 + 1) * bins + bin) * frames + frame] = scratch[bin * 2 + 1]
                }
            }
        }
        return spectrum
    }

    fun decode(spectrum: FloatArray): FloatArray {
        require(spectrum.size == 4 * bins * frames)
        val paddedSize = sampleCount + fftSize
        val weights = FloatArray(paddedSize)
        val output = FloatArray(paddedSize * 2)
        for (frame in 0 until frames) {
            for (i in window.indices) weights[frame * hop + i] += window[i] * window[i]
            for (channel in 0..1) {
                scratch.fill(0f)
                for (bin in 0 until bins) {
                    val real = spectrum[(channel * 2 * bins + bin) * frames + frame]
                    val imaginary = spectrum[((channel * 2 + 1) * bins + bin) * frames + frame]
                    scratch[bin * 2] = real
                    scratch[bin * 2 + 1] = imaginary
                    if (bin > 0) {
                        scratch[(fftSize - bin) * 2] = real
                        scratch[(fftSize - bin) * 2 + 1] = -imaginary
                    }
                }
                fft.complexInverse(scratch, true)
                for (i in window.indices) {
                    output[(frame * hop + i) * 2 + channel] += scratch[i * 2] * window[i]
                }
            }
        }
        return FloatArray(sampleCount * 2) {
            val frame = it / 2 + fftSize / 2
            output[frame * 2 + it % 2] / weights[frame].coerceAtLeast(1e-8f)
        }
    }
}

internal fun resampleStereo(input: FloatArray, outputFrames: Int): FloatArray {
    require(input.size >= 2 && input.size % 2 == 0 && outputFrames > 0)
    val inputFrames = input.size / 2
    if (inputFrames == outputFrames) return input.copyOf()
    return FloatArray(outputFrames * 2) { index ->
        val position = (index / 2).toDouble() * inputFrames / outputFrames
        val first = position.toInt().coerceAtMost(inputFrames - 1)
        val second = (first + 1).coerceAtMost(inputFrames - 1)
        val fraction = (position - first).toFloat()
        val channel = index % 2
        input[first * 2 + channel] * (1f - fraction) + input[second * 2 + channel] * fraction
    }
}

/** 用残差信号保留原曲信息，两路均为 100% 时逐样本恢复原声。 */
internal fun mixStemSample(original: Float, instrumental: Float, vocals: Float, backing: Float): Float =
    if (vocals == backing) original * vocals else
        (original - instrumental) * vocals + instrumental * backing
