package io.tinychat.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WavHeaderTest {

    private fun header(sampleRate: Int = 16_000, dataSize: Int = 32_000): ByteArray =
        ByteArray(WavHeader.SIZE).also { WavHeader.write(it, sampleRate, dataSize) }

    @Test
    fun `写出来的头是标准 16k 单声道 16bit`() {
        val bytes = header()
        val ascii = String(bytes, 0, 4, Charsets.US_ASCII)
        assertEquals("RIFF", ascii)
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(bytes, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(bytes, 36, 4, Charsets.US_ASCII))
        // RIFF 块长度 = 36 + 数据长度
        assertEquals(36 + 32_000, readInt(bytes, 4))
        assertEquals(1, readShort(bytes, 20)) // PCM
        assertEquals(1, readShort(bytes, 22)) // 单声道
        assertEquals(16_000, readInt(bytes, 24))
        assertEquals(32_000, readInt(bytes, 28)) // byteRate = sr * 2
        assertEquals(2, readShort(bytes, 32))
        assertEquals(16, readShort(bytes, 34))
        assertEquals(32_000, readInt(bytes, 40))
    }

    @Test
    fun `解析自己写的头能拿回参数`() {
        val bytes = header(sampleRate = 16_000, dataSize = 3_840_000)
        assertEquals(3_840_000, WavHeader.dataSize(bytes))
        assertEquals(16_000, WavHeader.sampleRate(bytes))
        assertEquals(1, WavHeader.channels(bytes))
        assertEquals(16, WavHeader.bitsPerSample(bytes))
    }

    @Test
    fun `服务端 TTS 那种流式占位长度按未知处理`() {
        val bytes = header(dataSize = WavHeader.UNKNOWN_DATA_SIZE)
        assertEquals(-1, WavHeader.dataSize(bytes))
        // 其余参数仍然可读
        assertEquals(16_000, WavHeader.sampleRate(bytes))
    }

    @Test
    fun `不是 wav 就返回未知而不是崩`() {
        assertEquals(-1, WavHeader.dataSize(ByteArray(8)))
        assertEquals(-1, WavHeader.sampleRate(ByteArray(0)))
        assertEquals(-1, WavHeader.dataSize(ByteArray(64) { 0x41 }))
    }

    @Test
    fun `空数据也是合法头`() {
        val bytes = header(dataSize = 0)
        assertEquals(0, WavHeader.dataSize(bytes))
        assertEquals(16_000, WavHeader.sampleRate(bytes))
    }

    private fun readInt(source: ByteArray, offset: Int): Int =
        (source[offset].toInt() and 0xFF) or
            ((source[offset + 1].toInt() and 0xFF) shl 8) or
            ((source[offset + 2].toInt() and 0xFF) shl 16) or
            ((source[offset + 3].toInt() and 0xFF) shl 24)

    private fun readShort(source: ByteArray, offset: Int): Int =
        (source[offset].toInt() and 0xFF) or ((source[offset + 1].toInt() and 0xFF) shl 8)
}
