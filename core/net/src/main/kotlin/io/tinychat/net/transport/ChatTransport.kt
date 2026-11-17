package io.tinychat.net.transport

import io.tinychat.model.ErrorKind
import io.tinychat.net.SseEvent
import io.tinychat.net.SseReader
import io.tinychat.net.protocol.PreparedRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 聊天请求的异常。`kind` 直接复用 [ErrorKind]，M1-11 的错误映射与 UI 文案都从这里取值。
 */
class ChatException(
    val kind: ErrorKind,
    val httpStatus: Int? = null,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** 传输层抽象：真实实现走 OkHttp，测试用假实现喂固件，上层不感知差别。 */
interface ChatTransport {
    fun stream(request: PreparedRequest): Flow<SseEvent>
}

/**
 * OkHttp 实现。
 *
 * - 超时按 02 §4.4：连接 15s、**读 120s**（深度思考搜索可能长时间无输出，靠 keep-alive 保活）；
 * - 取消：协程结束时 `call.cancel()`，这就是 M1-7「停止生成」的落点；
 * - 不挂任何日志拦截器：请求头里有 `x-api-key`，R9 要求「不写日志」。
 */
class OkHttpChatTransport(
    private val client: OkHttpClient = defaultClient(),
) : ChatTransport {

    override fun stream(request: PreparedRequest): Flow<SseEvent> = callbackFlow {
        val builder = Request.Builder()
            .url(request.url)
            .post(request.bodyJson.toRequestBody(JSON))

        request.headers.forEach { (name, value) -> builder.header(name, value) }

        val call = client.newCall(builder.build())

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(ChatException(ErrorKind.NETWORK, message = e.message ?: "网络请求失败", cause = e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val body = runCatching { resp.body?.string() }.getOrNull().orEmpty()
                        close(ChatException(kindFor(resp.code), resp.code, body.ifBlank { "HTTP ${resp.code}" }))
                        return
                    }

                    val source = resp.body?.source()
                    if (source == null) {
                        close(ChatException(ErrorKind.STREAM, resp.code, "响应体为空"))
                        return
                    }

                    try {
                        for (event in SseReader.read(source)) {
                            if (isClosedForSend) break
                            trySend(event)
                        }
                        close()
                    } catch (e: IOException) {
                        // 读流中断：已收到的内容由上层保留，状态落 INCOMPLETE
                        close(ChatException(ErrorKind.INTERRUPTED, resp.code, e.message ?: "流被中断", e))
                    } catch (e: Exception) {
                        close(ChatException(ErrorKind.STREAM, resp.code, e.message ?: "流解析失败", e))
                    }
                }
            }
        })

        awaitClose { call.cancel() }
    }

    private fun kindFor(code: Int): ErrorKind = when (code) {
        401 -> ErrorKind.HTTP_401
        402 -> ErrorKind.HTTP_402
        422 -> ErrorKind.HTTP_422
        429 -> ErrorKind.HTTP_429
        in 500..599 -> ErrorKind.HTTP_5XX
        else -> ErrorKind.STREAM
    }

    companion object {
        private val JSON = "application/json".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
