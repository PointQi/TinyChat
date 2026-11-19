package io.tinychat.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * 基 2 快速傅里叶变换（M5-2）。
 *
 * 只做录音频谱需要的实数输入场景：512 点、约 30fps 跑一次，纯 Kotlin 足够，
 * 不引第三方 DSP 库（也更方便单测）。
 *
 * 幅度口径与 Web Audio 的 `AnalyserNode` 对齐（chat-demo 用的就是它）：
 * 先加窗，再按窗增益归一，满幅单频约等于 1.0（0 dBFS）。
 */
object Fft {

    const val DEFAULT_SIZE = 512

    fun isPowerOfTwo(value: Int): Boolean = value > 0 && (value and (value - 1)) == 0

    /** Blackman 窗：与浏览器的默认窗一致，旁瓣比 Hann 低。 */
    fun blackman(size: Int): FloatArray {
        require(size > 1) { "window must be longer than 1" }
        val window = FloatArray(size)
        val step = 2.0 * PI / (size - 1)
        for (index in 0 until size) {
            window[index] = (0.42 - 0.5 * cos(step * index) + 0.08 * cos(2 * step * index)).toFloat()
        }
        return window
    }

    /** 原地基 2 复数 FFT；`re` / `im` 必须同长且长度为 2 的幂。 */
    fun transform(re: FloatArray, im: FloatArray) {
        val size = re.size
        require(im.size == size) { "re/im must be the same length" }
        require(isPowerOfTwo(size)) { "size must be a power of two" }
        if (size == 1) return

        // 位反转置换
        var j = 0
        for (index in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (index < j) {
                val tmpRe = re[index]; re[index] = re[j]; re[j] = tmpRe
                val tmpIm = im[index]; im[index] = im[j]; im[j] = tmpIm
            }
        }

        // 蝶形运算
        var length = 2
        val twoPi = -2.0 * PI
        while (length <= size) {
            val angle = twoPi / length
            val wReStep = cos(angle).toFloat()
            val wImStep = kotlin.math.sin(angle).toFloat()
            var start = 0
            while (start < size) {
                var wRe = 1f
                var wIm = 0f
                for (offset in 0 until length / 2) {
                    val evenIndex = start + offset
                    val oddIndex = evenIndex + length / 2
                    val oddRe = re[oddIndex] * wRe - im[oddIndex] * wIm
                    val oddIm = re[oddIndex] * wIm + im[oddIndex] * wRe
                    re[oddIndex] = re[evenIndex] - oddRe
                    im[oddIndex] = im[evenIndex] - oddIm
                    re[evenIndex] += oddRe
                    im[evenIndex] += oddIm
                    val nextWRe = wRe * wReStep - wIm * wImStep
                    wIm = wRe * wImStep + wIm * wReStep
                    wRe = nextWRe
                }
                start += length
            }
            length = length shl 1
        }
    }

    /**
     * 实数 PCM → `fftSize / 2` 个 bin 的幅度（不含 DC）。
     *
     * 样本多于 [fftSize] 时取**末尾**一段（频谱要对准"刚刚说的这句"）。
     */
    fun magnitudes(
        samples: ShortArray,
        fftSize: Int = DEFAULT_SIZE,
        window: FloatArray = blackman(fftSize),
    ): FloatArray {
        require(isPowerOfTwo(fftSize)) { "fftSize must be a power of two" }
        require(window.size == fftSize) { "window size mismatch" }
        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)
        val start = maxOf(0, samples.size - fftSize)
        val count = minOf(fftSize, samples.size - start)
        var gain = 0f
        for (index in 0 until fftSize) {
            val coefficient = window[index]
            gain += coefficient
            if (index < count) {
                // 归一化到 ±1：16-bit PCM 的满幅是 32767
                re[index] = samples[start + index] / 32768f * coefficient
            }
        }
        transform(re, im)

        val bins = fftSize / 2
        val magnitudes = FloatArray(bins)
        val scale = if (gain > 0f) 2f / gain else 0f
        for (index in 1 until bins) {
            magnitudes[index - 1] = sqrt(re[index] * re[index] + im[index] * im[index]) * scale
        }
        return magnitudes
    }
}
