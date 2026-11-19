package io.tinychat.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class FftTest {

    @Test
    fun `长度必须是 2 的幂`() {
        assertTrue(Fft.isPowerOfTwo(1))
        assertTrue(Fft.isPowerOfTwo(512))
        assertTrue(!Fft.isPowerOfTwo(500))
        assertTrue(!Fft.isPowerOfTwo(0))
    }

    @Test
    fun `窗是对称的且增益约 0_42`() {
        val window = Fft.blackman(512)
        assertEquals(512, window.size)
        assertEquals(window.first(), window.last(), 1e-6f)
        assertEquals(window[1], window[510], 1e-6f)
        val mean = window.sum() / window.size
        assertEquals(0.42f, mean, 0.01f)
    }

    @Test
    fun `满幅 1kHz 正弦的峰值落在对应 bin 上`() {
        // 16kHz 采样、512 点 → bin 宽 31.25Hz；1kHz 在 bin 32
        val pcm = sine(freq = 1_000.0, sampleRate = 16_000, size = 512, amplitude = 1.0)
        val magnitudes = Fft.magnitudes(pcm, 512)
        val peakIndex = magnitudes.indices.maxByOrNull { magnitudes[it] } ?: -1
        assertEquals(31, peakIndex) // 数组不含 DC，所以 bin 32 → 下标 31
        // 满幅单频归一化后约等于 1
        assertEquals(1.0f, magnitudes[peakIndex], 0.15f)
    }

    @Test
    fun `幅度随输入线性变化`() {
        val loud = Fft.magnitudes(sine(1_000.0, 16_000, 512, 0.8), 512)
        val quiet = Fft.magnitudes(sine(1_000.0, 16_000, 512, 0.08), 512)
        val loudPeak = loud.max()
        val quietPeak = quiet.max()
        assertEquals(10.0f, loudPeak / quietPeak, 0.5f)
    }

    @Test
    fun `静音输入全是零`() {
        val magnitudes = Fft.magnitudes(ShortArray(512), 512)
        assertTrue(magnitudes.all { it < 1e-6f })
    }

    @Test
    fun `样本比 fft 长时取末尾一段`() {
        // 前半静音、后半 1kHz：取末尾 → 仍然有强峰
        val pcm = ShortArray(2048)
        val tail = sine(1_000.0, 16_000, 1024, 1.0)
        tail.copyInto(pcm, destinationOffset = 1024)
        val magnitudes = Fft.magnitudes(pcm, 512)
        assertTrue(magnitudes.max() > 0.7f)
    }

    private fun sine(freq: Double, sampleRate: Int, size: Int, amplitude: Double): ShortArray =
        ShortArray(size) { index ->
            (sin(2 * PI * freq * index / sampleRate) * amplitude * 32767).toInt().toShort()
        }
}
