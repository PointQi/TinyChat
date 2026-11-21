package io.tinychat.net.speech

import io.tinychat.model.TtsVoice
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MultipartBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpeechApiTest {

    @Test
    fun `TTS 请求体带音色与 pcm 格式`() {
        val json = SpeechRequests.ttsJson("你好", TtsVoice.TIDE)
        val objectJson = Json.parseToJsonElement(json).jsonObject
        assertEquals("你好", objectJson["text"]?.jsonPrimitive?.content)
        assertEquals("tide", objectJson["voice_id"]?.jsonPrimitive?.content)
        assertEquals("pcm", objectJson["format"]?.jsonPrimitive?.content)
        assertEquals("true", objectJson["filter_markdown"]?.jsonPrimitive?.content)
    }

    @Test
    fun `TTS 文本里的引号换行不会破 JSON`() {
        val text = "第一行\n第二行 \"带引号\" 和 \\ 反斜杠"
        val json = SpeechRequests.ttsJson(text, TtsVoice.STELLA)
        val objectJson = Json.parseToJsonElement(json).jsonObject
        assertEquals(text, objectJson["text"]?.jsonPrimitive?.content)
        assertEquals("stella", objectJson["voice_id"]?.jsonPrimitive?.content)
    }

    @Test
    fun `TTS 请求打到 tools 端点并带 Bearer`() {
        val request = SpeechRequests.ttsRequest(
            baseUrl = "https://api.deepseek.com/",
            apiKey = "k-test",
            json = SpeechRequests.ttsJson("hi", TtsVoice.STELLA),
        )
        assertEquals("https://api.deepseek.com/v1/tools/tts", request.url.toString())
        assertEquals("Bearer k-test", request.header("Authorization"))
        assertEquals("POST", request.method)
        assertEquals("application/json; charset=utf-8", request.body?.contentType().toString())
    }

    @Test
    fun `ASR 走 multipart 的 file 字段且只带一个 part`() {
        val wav = File.createTempFile("tc-asr", ".wav").apply {
            writeBytes(ByteArray(16) { 'A'.code.toByte() })
            deleteOnExit()
        }
        val request = SpeechRequests.asrRequest("https://api.deepseek.com", "k-test", wav)
        assertEquals("https://api.deepseek.com/v1/tools/asr", request.url.toString())
        assertEquals("Bearer k-test", request.header("Authorization"))

        val body = request.body
        assertTrue("期望 MultipartBody，实际 ${body?.javaClass}", body is MultipartBody)
        val multipart = body as MultipartBody
        assertEquals(1, multipart.parts.size)

        val buffer = okio.Buffer()
        multipart.writeTo(buffer)
        val raw = buffer.readUtf8()
        assertTrue(raw, raw.contains("name=\"file\"; filename=\"recording.wav\""))
        assertTrue(raw, raw.contains("Content-Type: audio/wav"))
        assertTrue(raw, raw.contains("AAAAAAAAAAAAAAAA"))
    }

    @Test
    fun `失败码映射到可读文案`() {
        // TTS 的 400 有三种含义，其中"没得读"要归到 NO_AUDIO
        assertEquals(SpeechFailure.NO_AUDIO, failureOf(SpeechFailure.NO_AUDIO))
        assertEquals("这条回答暂不支持朗读", SpeechFailure.NO_AUDIO.userMessage)
        assertEquals("逐字文案", "账户余额不足，充值后即可继续", SpeechFailure.INSUFFICIENT_BALANCE.userMessage)
        assertEquals("interrupted", "朗读已中断，请重新点朗读", SpeechFailure.INTERRUPTED.userMessage)
        assertEquals("没拿到音频通道", "无法占用音频通道，请关闭其他正在播放的音视频后重试", SpeechFailure.NO_FOCUS.userMessage)
    }

    private fun failureOf(value: SpeechFailure) = value
}
