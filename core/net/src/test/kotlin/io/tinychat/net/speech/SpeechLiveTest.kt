package io.tinychat.net.speech

import io.tinychat.model.TtsVoice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 语音端点真实冒烟（M5）：**要花钱，默认跳过**。
 *
 * 跑法：
 * ```
 * $env:TINYCHAT_LIVE_TEST="1"; .\gradlew.bat :core:net:test --tests "*SpeechLiveTest*"
 * ```
 *
 * 一次跑两条：TTS 合成（量首字节延迟）+ 把合成音频降采样成 16k WAV 再喂 ASR，
 * 相当于自己验证「录音 → WAV → 识别」这条链路的格式要求。
 */
class SpeechLiveTest {

    @Test
    fun `TTS 边下边给且 ASR 能读懂自家合成的音频`() = runBlocking {
        assumeTrue("未开启真实接口测试（TINYCHAT_LIVE_TEST=1）", System.getenv("TINYCHAT_LIVE_TEST") == "1")
        val key = readKey()
        assumeTrue("未配置 .local/dev-key.txt，跳过真实接口测试", key != null)

        val client = SpeechClient()
        val text = "你好，我是语音助手，今天天气不错。"

        // 1) TTS：确认拿到音频、且不是等整段才返回
        val pcm = ByteArrayOutputStream()
        var firstChunkAt = -1L
        var chunks = 0
        val startedAt = System.currentTimeMillis()
        val total = client.synthesize(key!!, text, TtsVoice.STELLA) { chunk ->
            if (firstChunkAt < 0) firstChunkAt = System.currentTimeMillis() - startedAt
            chunks++
            pcm.write(chunk)
        }
        val audioMs = total / 2 * 1000 / 24_000
        println("[TTS] bytes=$total chunks=$chunks ttfb=${firstChunkAt}ms audio=${audioMs}ms")

        assertTrue("应当收到音频，实际 $total 字节", total > 8_000)
        assertEquals("PCM 应当是 16bit 对齐的偶数长度", 0L, total % 2)
        assertTrue("首字节太慢：${firstChunkAt}ms", firstChunkAt in 0..8_000)
        assertTrue("音频时长不合理：${audioMs}ms", audioMs > 500)

        // 2) 24k → 16k 线性重采样，写成 16-bit PCM WAV
        val wav = File.createTempFile("tc-live-asr", ".wav").apply { deleteOnExit() }
        wav.writeBytes(toWav16k(pcm.toByteArray()))

        // 3) ASR：能读回自己刚合成的那句话
        val response = client.transcribe(key, wav)
        println("[ASR] text=${response.text} duration=${response.durationMs}ms")
        assertTrue("识别结果为空", response.text.isNotBlank())
        assertTrue("时长应当接近合成音频长度，实际 ${response.durationMs}ms", response.durationMs in 1_000..8_000)
        assertTrue("识别结果与原文差太远：${response.text}", response.text.contains("语音"))
    }

    /** 24kHz/16bit/mono → 16kHz/16bit/mono，纯线性插值（只为喂 ASR，不做抗混叠） */
    private fun toWav16k(pcm: ByteArray): ByteArray {
        val source = pcm.size / 2
        val target = (source.toLong() * 16_000 / 24_000).toInt()
        val data = ByteArray(target * 2)
        for (index in 0 until target) {
            val position = index * 24_000.0 / 16_000
            val left = position.toInt().coerceIn(0, source - 1)
            val right = (left + 1).coerceAtMost(source - 1)
            val fraction = position - left
            val a = (pcm[left * 2].toInt() and 0xFF) or (pcm[left * 2 + 1].toInt() shl 8)
            val b = (pcm[right * 2].toInt() and 0xFF) or (pcm[right * 2 + 1].toInt() shl 8)
            val sample = (a * (1 - fraction) + b * fraction).toInt()
            data[index * 2] = sample.toByte()
            data[index * 2 + 1] = (sample shr 8).toByte()
        }
        val header = ByteArray(44)
        "RIFF".toByteArray().copyInto(header, 0)
        putInt(header, 4, 36 + data.size)
        "WAVE".toByteArray().copyInto(header, 8)
        "fmt ".toByteArray().copyInto(header, 12)
        putInt(header, 16, 16)
        putShort(header, 20, 1)
        putShort(header, 22, 1)
        putInt(header, 24, 16_000)
        putInt(header, 28, 32_000)
        putShort(header, 32, 2)
        putShort(header, 34, 16)
        "data".toByteArray().copyInto(header, 36)
        putInt(header, 40, data.size)
        return header + data
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

    /** 从模块目录向上找仓库根的 .local/dev-key.txt */
    private fun readKey(): String? {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        repeat(5) {
            val candidate = File(dir, ".local/dev-key.txt")
            if (candidate.isFile) {
                return candidate.readText().trim().takeIf { it.isNotEmpty() }
            }
            dir = dir?.parentFile
        }
        return null
    }
}
