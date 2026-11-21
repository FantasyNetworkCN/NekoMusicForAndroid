package com.neko.music.service

import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.PI
import kotlin.math.cos

/** 低延迟频谱近似分离：提取人声频段中左右声道相干的部分，不能等同于模型分离。 */
internal class RealtimeStemMixer(private val rate: Int, private val mono: Boolean) {
    private val size = 1024
    private val hop = 256
    private val fft = FloatFFT_1D(size.toLong())
    private val window = FloatArray(size) { (0.5 - 0.5 * cos(2 * PI * it / size)).toFloat() }
    private val left = FloatArray(size)
    private val right = FloatArray(size)
    private val vocal = FloatArray(size)
    private val weights = FloatArray(size)
    private val spectrumLeft = FloatArray(size * 2)
    private val spectrumRight = FloatArray(size * 2)
    private val spectrumVocal = FloatArray(size * 2)
    private var filled = size - hop
    private var discard = size - hop
    private var received = 0L
    private var emitted = 0L

    fun append(l: Float, r: Float, output: (Float, Float, Float) -> Unit) {
        received++
        appendFrame(l, r, output)
    }

    fun finish(output: (Float, Float, Float) -> Unit) {
        while (emitted < received) appendFrame(0f, 0f, output)
    }

    private fun appendFrame(l: Float, r: Float, output: (Float, Float, Float) -> Unit) {
        left[filled] = l
        right[filled++] = r
        if (filled < size) return
        spectrumLeft.fill(0f)
        spectrumRight.fill(0f)
        spectrumVocal.fill(0f)
        for (i in 0 until size) {
            spectrumLeft[i] = left[i] * window[i]
            spectrumRight[i] = right[i] * window[i]
        }
        fft.realForwardFull(spectrumLeft)
        fft.realForwardFull(spectrumRight)
        for (bin in 0..size / 2) {
            val index = bin * 2
            val lr = spectrumLeft[index]
            val li = spectrumLeft[index + 1]
            val rr = spectrumRight[index]
            val ri = spectrumRight[index + 1]
            val coherence = if (mono) 1f else
                (2f * (lr * rr + li * ri) / (lr * lr + li * li + rr * rr + ri * ri + 1e-12f)).coerceIn(0f, 1f)
            val frequency = bin.toFloat() * rate / size
            val band = minOf(((frequency - 100f) / 200f).coerceIn(0f, 1f), ((10_000f - frequency) / 3000f).coerceIn(0f, 1f))
            val mask = coherence * coherence * band
            spectrumVocal[index] = (lr + rr) * 0.5f * mask
            spectrumVocal[index + 1] = (li + ri) * 0.5f * mask
            if (bin in 1 until size / 2) {
                spectrumVocal[(size - bin) * 2] = spectrumVocal[index]
                spectrumVocal[(size - bin) * 2 + 1] = -spectrumVocal[index + 1]
            }
        }
        fft.complexInverse(spectrumVocal, true)
        for (i in 0 until size) {
            vocal[i] += spectrumVocal[i * 2] * window[i]
            weights[i] += window[i] * window[i]
        }
        for (i in 0 until hop) {
            if (discard > 0) discard-- else if (emitted < received) {
                output(left[i], right[i], vocal[i] / weights[i].coerceAtLeast(1e-8f))
                emitted++
            }
        }
        for (array in arrayOf(left, right, vocal, weights)) {
            array.copyInto(array, 0, hop, size)
            array.fill(0f, size - hop, size)
        }
        filled = size - hop
    }
}
