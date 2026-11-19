package io.tinychat.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SpectrumAnalyserTest {

    private val bands = 20

    @Test
    fun `静音时所有柱子都落底`() {
        val analyser = SpectrumAnalyser(bands)
        repeat(5) { analyser.update(ShortArray(512)) }
        assertTrue(analyser.levels.all { it == 0f })
    }

    @Test
    fun `1kHz 正弦的峰值落在中段`() {
        val analyser = SpectrumAnalyser(bands)
        val levels = analyser.update(sine(1_000.0, 0.005))
        val peak = levels.indices.maxByOrNull { levels[it] } ?: -1
        // log(1000/100) / log(4000/100) * 20 ≈ 12.5
        assertTrue("peak band = $peak", peak in 10..15)
        assertTrue("peak level = ${levels[peak]}", levels[peak] > 0.4f)
    }

    @Test
    fun `低频在左、高频在右`() {
        val low = SpectrumAnalyser(bands).run {
            repeat(3) { update(sine(200.0, 0.02)) }
            levels.copyOf()
        }
        val high = SpectrumAnalyser(bands).run {
            repeat(3) { update(sine(3_000.0, 0.02)) }
            levels.copyOf()
        }
        val lowPeak = low.indices.maxByOrNull { low[it] } ?: -1
        val highPeak = high.indices.maxByOrNull { high[it] } ?: -1
        assertTrue("low=$lowPeak", lowPeak < bands / 2)
        assertTrue("high=$highPeak", highPeak > bands / 2)
    }

    @Test
    fun `更响的输入柱子更高`() {
        fun peakOf(amplitude: Double): Float {
            val analyser = SpectrumAnalyser(bands)
            repeat(3) { analyser.update(sine(1_000.0, amplitude)) }
            return analyser.levels.max()
        }
        val quiet = peakOf(0.002)
        val loud = peakOf(0.2)
        assertTrue("quiet=$quiet loud=$loud", loud > quiet)
    }

    @Test
    fun `柱高始终在 0 到 1 之间且数组复用`() {
        val analyser = SpectrumAnalyser(bands)
        val first = analyser.update(sine(500.0, 1.0))
        val second = analyser.update(sine(500.0, 1.0))
        assertTrue(first === second) // 同一数组，不产生垃圾
        assertEquals(bands, second.size)
        assertTrue(second.all { it in 0f..1f })
    }

    @Test
    fun `reset 之后回到全零`() {
        val analyser = SpectrumAnalyser(bands)
        analyser.update(sine(1_000.0, 0.5))
        analyser.reset()
        assertTrue(analyser.levels.all { it == 0f })
    }

    @Test
    fun `样本不足一帧时不崩`() {
        val analyser = SpectrumAnalyser(bands)
        analyser.update(ShortArray(37))
        analyser.update(ShortArray(0))
        assertEquals(bands, analyser.levels.size)
    }

    private fun sine(freq: Double, amplitude: Double, size: Int = 512): ShortArray =
        ShortArray(size) { index ->
            (sin(2 * PI * freq * index / 16_000) * amplitude * 32767).toInt().toShort()
        }
}
