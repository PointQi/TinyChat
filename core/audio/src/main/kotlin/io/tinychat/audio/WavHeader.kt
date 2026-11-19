package io.tinychat.audio

/**
 * 44 字节 PCM WAV 头（M5-1）。
 *
 * 服务端 ASR 只认 16-bit PCM WAV（采样率限 8k/16k/22.05k/24k/32k/44.1k/48k），
 * 而且**长度对不上就会被拒**（探针里 0 字节文件直接 400），所以录音结束后必须回填
 * RIFF/data 两处长度。TTS 返回的 wav 则是反例：`data` 长度填的是流式占位
 * `0x7FFFFFFF`，解析时按"未知长度"处理，别拿它算时长。
 */
object WavHeader {

    const val SIZE = 44

    /** 流式输出的占位长度（服务端 TTS 的 wav 就是这么填的）。 */
    const val UNKNOWN_DATA_SIZE = 0x7FFFFFFF

    /**
     * 把 44 字节头写进 [target] 的 [offset] 起。
     *
     * @param dataSize PCM 数据的字节数；边录边写时先传 0，结束时再整体重写一遍。
     */
    fun write(
        target: ByteArray,
        sampleRate: Int,
        dataSize: Int,
        offset: Int = 0,
        channels: Int = 1,
        bitsPerSample: Int = 16,
    ) {
        require(target.size - offset >= SIZE) { "buffer too small" }
        require(channels >= 1 && bitsPerSample % 8 == 0) { "bad pcm format" }
        val blockAlign = channels * bitsPerSample / 8
        val byteRate = sampleRate * blockAlign

        putAscii(target, offset, "RIFF")
        putInt(target, offset + 4, 36 + dataSize)
        putAscii(target, offset + 8, "WAVE")
        putAscii(target, offset + 12, "fmt ")
        putInt(target, offset + 16, 16) // fmt 块长度
        putShort(target, offset + 20, 1) // PCM
        putShort(target, offset + 22, channels)
        putInt(target, offset + 24, sampleRate)
        putInt(target, offset + 28, byteRate)
        putShort(target, offset + 32, blockAlign)
        putShort(target, offset + 34, bitsPerSample)
        putAscii(target, offset + 36, "data")
        putInt(target, offset + 40, dataSize)
    }

    /**
     * 读 `data` 块长度；流式占位或结构不对返回 -1。
     *
     * 只做最小校验（RIFF/WAVE + 遍历块头），够用来验自己的录音与判 TTS 的流式头。
     */
    fun dataSize(source: ByteArray, offset: Int = 0): Int {
        val data = chunkOffset(source, offset, "data") ?: return -1
        // chunkOffset 给的是**负载**起点，长度字段在它前面 4 字节
        val size = readInt(source, data - 4)
        return if (size == UNKNOWN_DATA_SIZE || size < 0) -1 else size
    }

    fun sampleRate(source: ByteArray, offset: Int = 0): Int {
        val fmt = chunkOffset(source, offset, "fmt ") ?: return -1
        return readInt(source, fmt + 4)
    }

    fun channels(source: ByteArray, offset: Int = 0): Int {
        val fmt = chunkOffset(source, offset, "fmt ") ?: return -1
        return readShort(source, fmt + 2)
    }

    fun bitsPerSample(source: ByteArray, offset: Int = 0): Int {
        val fmt = chunkOffset(source, offset, "fmt ") ?: return -1
        return readShort(source, fmt + 14)
    }

    /** 找到某个块的**负载**起点（块头 4 字节 id + 4 字节长度之后）。 */
    private fun chunkOffset(source: ByteArray, offset: Int, id: String): Int? {
        if (source.size - offset < 12) return null
        if (readAscii(source, offset) != "RIFF" || readAscii(source, offset + 8) != "WAVE") return null
        var cursor = offset + 12
        while (cursor + 8 <= source.size) {
            val chunkId = readAscii(source, cursor)
            val size = readInt(source, cursor + 4)
            if (chunkId == id) return cursor + 8
            if (size < 0) return null
            // 块长度按偶数字节补齐
            cursor += 8 + size + (size and 1)
        }
        return null
    }

    private fun putAscii(target: ByteArray, offset: Int, text: String) {
        for (index in text.indices) target[offset + index] = text[index].code.toByte()
    }

    private fun putInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
        target[offset + 2] = (value ushr 16).toByte()
        target[offset + 3] = (value ushr 24).toByte()
    }

    private fun putShort(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }

    private fun readAscii(source: ByteArray, offset: Int): String {
        if (offset + 4 > source.size) return ""
        return buildString(4) {
            for (index in 0 until 4) append((source[offset + index].toInt() and 0xFF).toChar())
        }
    }

    private fun readInt(source: ByteArray, offset: Int): Int {
        if (offset + 4 > source.size) return -1
        return (source[offset].toInt() and 0xFF) or
            ((source[offset + 1].toInt() and 0xFF) shl 8) or
            ((source[offset + 2].toInt() and 0xFF) shl 16) or
            ((source[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readShort(source: ByteArray, offset: Int): Int {
        if (offset + 2 > source.size) return -1
        return (source[offset].toInt() and 0xFF) or ((source[offset + 1].toInt() and 0xFF) shl 8)
    }
}
