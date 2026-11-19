package io.tinychat.net.speech

import io.tinychat.model.TtsVoice
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * 语音两个端点的请求拼装（M5 前置），**不联网**就能断言，测试见 `SpeechApiTest`。
 *
 * 官方约束（2026-11-19 实测 + 文档核对）：
 *
 * - ASR 只收 `multipart/form-data` 的 `file` 字段，16-bit PCM WAV / Ogg Opus，≤8MiB、≤120s；
 * - TTS 收 JSON：`text` 最多 10000 **计费字符**（汉字算 2 个）、`filter_markdown` 默认 true；
 * - `format` 选 `pcm` 而不是 `wav`：wav 的头里 `data` 长度是流式占位，没法落到文件播放。
 */
object SpeechRequests {

    const val TTS_PATH = "/v1/tools/tts"
    const val ASR_PATH = "/v1/tools/asr"

    private val JSON = "application/json".toMediaType()
    private val WAV = "audio/wav".toMediaType()

    /** TTS 的请求体：看着小，但字段顺序与默认值都在这里定死，方便断言。 */
    fun ttsJson(
        text: String,
        voice: TtsVoice,
        format: String = "pcm",
        filterMarkdown: Boolean = true,
    ): String = buildJsonObject {
        put("text", text)
        put("voice_id", voice.wire)
        put("format", format)
        put("filter_markdown", filterMarkdown)
    }.toString()

    fun ttsRequest(baseUrl: String, apiKey: String, json: String): Request =
        Request.Builder()
            .url(endpoint(baseUrl, TTS_PATH))
            .header("Authorization", "Bearer $apiKey")
            .post(json.toRequestBody(JSON))
            .build()

    fun asrRequest(
        baseUrl: String,
        apiKey: String,
        wav: File,
        fileName: String = "recording.wav",
    ): Request {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, wav.asRequestBody(WAV))
            .build()
        return Request.Builder()
            .url(endpoint(baseUrl, ASR_PATH))
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()
    }

    private fun endpoint(baseUrl: String, path: String): String = baseUrl.trimEnd('/') + path
}

/** ASR 的返回体。`request_id` 只用于排障，不进 UI。 */
@kotlinx.serialization.Serializable
data class AsrResponse(
    @kotlinx.serialization.SerialName("request_id")
    val requestId: String? = null,
    val text: String = "",
    @kotlinx.serialization.SerialName("duration_ms")
    val durationMs: Int = 0,
)

internal val speechJson = Json { ignoreUnknownKeys = true; isLenient = true }
