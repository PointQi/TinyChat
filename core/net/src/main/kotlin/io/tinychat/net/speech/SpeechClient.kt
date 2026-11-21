package io.tinychat.net.speech

import io.tinychat.model.TtsVoice
import io.tinychat.net.transport.OkHttpChatTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/** 语音相关失败。UI 按这个映射文案（见 [userMessage]）。 */
enum class SpeechFailure {
    /** TTS：文本为空 / 纯符号 / 内容审核不通过，都是"这条读不出来" */
    NO_AUDIO,

    /** ASR：音频格式不被接受（WAV 头不对、采样率不在白名单） */
    INVALID_AUDIO,

    /** ASR：文件超过 8MiB */
    TOO_LARGE,
    UNAUTHORIZED,
    INSUFFICIENT_BALANCE,
    RATE_LIMITED,
    BUSY,
    NETWORK,

    /** 读到一半断了（官方文档：提前结束一律视为失败） */
    INTERRUPTED,

    /** 拿不到音频通道：有别的应用在放东西 */
    NO_FOCUS,
    UNKNOWN,
    ;

    val userMessage: String
        get() = when (this) {
            NO_AUDIO -> "这条回答暂不支持朗读"
            INVALID_AUDIO -> "录音文件无法识别，请重新录制"
            TOO_LARGE -> "录音时长超出限制，请缩短后重试"
            UNAUTHORIZED -> "API Key 无效，请在设置中更新"
            INSUFFICIENT_BALANCE -> "账户余额不足，充值后即可继续"
            RATE_LIMITED -> "操作太频繁，稍后再试"
            BUSY -> "服务繁忙，稍后再试"
            NETWORK -> "网络连接失败，请检查网络后重试"
            INTERRUPTED -> "朗读已中断，请重新点朗读"
            NO_FOCUS -> "无法占用音频通道，请关闭其他正在播放的音视频后重试"
            UNKNOWN -> "朗读失败，请稍后再试"
        }
}

class SpeechException(
    val failure: SpeechFailure,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * ASR / TTS 客户端（M5）。
 *
 * 和聊天共用同一个 OkHttp 实例配置（15s 连接 / 120s 读），但**不挂日志拦截器**——
 * 请求头里有 Key。
 *
 * TTS 走 `format=pcm` 的流式响应：一边读一边把 chunk 交给 [synthesize] 的回调，
 * 回调里直接写 `AudioTrack` 就形成自然的背压（播放慢 → 网络读得慢 → 少占内存）。
 */
class SpeechClient(
    private val client: OkHttpClient = OkHttpChatTransport.defaultClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    /** 语音识别。[wav] 必须是 16-bit PCM WAV。 */
    suspend fun transcribe(apiKey: String, wav: File): AsrResponse = withContext(Dispatchers.IO) {
        val request = SpeechRequests.asrRequest(baseUrl, apiKey, wav)
        val call = client.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val body = runCatching { response.body?.string() }.getOrNull().orEmpty()
                    throw asrFailure(response.code, body)
                }
                val raw = response.body?.string().orEmpty()
                runCatching { speechJson.decodeFromString(AsrResponse.serializer(), raw) }
                    .getOrElse { throw SpeechException(SpeechFailure.UNKNOWN, "ASR 返回体无法解析", it) }
            }
        } catch (error: SpeechException) {
            throw error
        } catch (error: IOException) {
            throw SpeechException(SpeechFailure.NETWORK, error.message ?: "网络连接失败", error)
        }
    }

    /**
     * 语音合成，边下边交给 [onChunk]。
     *
     * ⚠️ [onChunk] 拿到的数组**可能被下一轮复用**（整块 8KB 时直接复用），
     * 需要留存就自己 copy。回调在 IO 线程，允许同步阻塞（这就是我们想要的背压）。
     *
     * @return 总共读到的字节数；0 说明服务端一个字都没给
     * @throws SpeechException 失败（含中途断流）
     */
    suspend fun synthesize(
        apiKey: String,
        text: String,
        voice: TtsVoice,
        onChunk: (ByteArray) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        val json = SpeechRequests.ttsJson(text, voice)
        val call = client.newCall(SpeechRequests.ttsRequest(baseUrl, apiKey, json))
        // 停止朗读 = 取消协程，这里要能立刻掐断连接（不然会白听 100 秒的合成）
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val body = runCatching { response.body?.string() }.getOrNull().orEmpty()
                    throw ttsFailure(response.code, body)
                }
                val stream = response.body?.byteStream()
                    ?: throw SpeechException(SpeechFailure.UNKNOWN, "响应体为空")
                val buffer = ByteArray(CHUNK_BYTES)
                var total = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    total += read
                    onChunk(if (read == buffer.size) buffer else buffer.copyOf(read))
                }
                total
            }
        } catch (error: SpeechException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            if (!coroutineContext[Job].isActiveOrNull()) throw CancellationException()
            throw SpeechException(SpeechFailure.INTERRUPTED, error.message ?: "朗读已中断", error)
        } finally {
            cancelHandle?.dispose()
        }
    }

    private fun ttsFailure(code: Int, body: String): SpeechException {
        val failure = when {
            code == 400 && (
                body.contains("Content Exists Risk") ||
                    body.contains("No readable text") ||
                    body.contains("must not be empty")
                ) -> SpeechFailure.NO_AUDIO

            code == 401 -> SpeechFailure.UNAUTHORIZED
            code == 402 -> SpeechFailure.INSUFFICIENT_BALANCE
            code == 429 -> SpeechFailure.RATE_LIMITED
            code in 500..599 -> SpeechFailure.BUSY
            else -> SpeechFailure.UNKNOWN
        }
        return SpeechException(failure, "TTS HTTP $code $body")
    }

    private fun asrFailure(code: Int, body: String): SpeechException {
        val failure = when {
            code == 400 -> SpeechFailure.INVALID_AUDIO
            code == 413 -> SpeechFailure.TOO_LARGE
            code == 401 -> SpeechFailure.UNAUTHORIZED
            code == 402 -> SpeechFailure.INSUFFICIENT_BALANCE
            code == 429 -> SpeechFailure.RATE_LIMITED
            code in 500..599 -> SpeechFailure.BUSY
            else -> SpeechFailure.UNKNOWN
        }
        return SpeechException(failure, "ASR HTTP $code $body")
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"

        /** 8KB ≈ 0.17s 音频；再小就费系统调用，再大就首帧变慢。 */
        private const val CHUNK_BYTES = 8 * 1024
    }
}

/** 协程还没被取消：`Job` 可能为 null（没有 Job 的裸调用）。 */
private fun Job?.isActiveOrNull(): Boolean = this == null || isActive
