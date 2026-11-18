package io.tinychat.net.files

import io.tinychat.model.ErrorKind
import io.tinychat.net.transport.ChatException
import io.tinychat.net.transport.OkHttpChatTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.File
import java.io.IOException

/**
 * 上传用途（03 §6.5）。`purpose` 必须排在 `file` 之前，否则服务端解析不到。
 */
enum class FilePurpose(val wire: String) {
    /** 仅文字识别 */
    TEXT_RECOGNITION("text_recognition"),

    /** 含图版式识别 */
    RICH_LAYOUT("rich_layout_recognition"),

    /** txt/md/csv/json/代码/XLSX/EPUB，不计费 */
    USER_DATA("user_data"),
}

/** 上传成功后的引用信息：对话里用 `file_id` 直接引用，不必再带内容。 */
data class UploadedFile(
    val fileId: String,
    val fileName: String,
    val purpose: FilePurpose,
)

/**
 * 已经拼好的上传请求（可离线断言，不联网）。
 */
data class PreparedUpload(
    val url: String,
    val headers: Map<String, String>,
    val body: MultipartBody,
)

/**
 * Files API（M1-12）：只负责「上传拿 `file_id`」，供 M4 的附件功能用。
 *
 * - `POST /anthropic/v1/files`，multipart，`purpose` 在 `file` 之前；
 * - 必须带 `anthropic-beta: files-api-2025-04-14`；
 * - 单文件 100MiB / 1000 页，上传与解析各 120s（超时按 120s 设）。
 */
object FilesRequests {

    const val BETA = "files-api-2025-04-14"

    fun build(
        baseUrl: String,
        apiKey: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        purpose: FilePurpose,
    ): PreparedUpload = build(
        baseUrl = baseUrl,
        apiKey = apiKey,
        fileName = fileName,
        mimeType = mimeType,
        content = bytes.toRequestBody(mimeType.toMediaType()),
        purpose = purpose,
    )

    /**
     * 允许调用方传入**流式** body：大文件不能先读进内存（100MiB 会直接把进程打死）。
     */
    fun build(
        baseUrl: String,
        apiKey: String,
        fileName: String,
        mimeType: String,
        content: RequestBody,
        purpose: FilePurpose,
    ): PreparedUpload {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            // 顺序有讲究：purpose 必须先写
            .addFormDataPart("purpose", purpose.wire)
            .addFormDataPart("file", fileName, content)
            .build()

        return PreparedUpload(
            url = baseUrl.trimEnd('/') + "/anthropic/v1/files",
            headers = mapOf(
                "x-api-key" to apiKey,
                "anthropic-beta" to BETA,
            ),
            body = body,
        )
    }

    /**
     * 从响应里取 `file_id`；形状不对就按上传失败处理（不静默吞掉）。
     *
     * 服务端返回 `{"id":"file-api-..."}`；`file_id` 是对旧文档写法的兼容。
     */
    fun parseFileId(json: String): String {
        val root = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull()
            ?: throw ChatException(ErrorKind.UPLOAD, message = "上传响应不是 JSON")

        val id = listOf("id", "file_id")
            .firstNotNullOfOrNull { key -> (root[key] as? JsonPrimitive)?.contentOrNull }

        if (id.isNullOrBlank()) {
            throw ChatException(ErrorKind.UPLOAD, message = "上传响应里没有 file_id")
        }
        return id
    }
}

/**
 * OkHttp 实现。错误分类沿用 [ErrorKind]，`upload` 类错误由 UI 提示「附件上传失败」。
 */
class FilesClient(
    private val client: OkHttpClient = OkHttpChatTransport.defaultClient(),
    private val baseUrl: String = "https://api.deepseek.com",
) {

    suspend fun upload(
        apiKey: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        purpose: FilePurpose = FilePurpose.USER_DATA,
    ): UploadedFile = upload(
        apiKey = apiKey,
        fileName = fileName,
        mimeType = mimeType,
        content = bytes.toRequestBody(mimeType.toMediaType()),
        purpose = purpose,
    )

    /**
     * 文件版：**边读边发**，不把整个文件读进内存（M4-3 的进度也靠它）。
     */
    suspend fun upload(
        apiKey: String,
        fileName: String,
        mimeType: String,
        file: File,
        purpose: FilePurpose,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> },
    ): UploadedFile = upload(
        apiKey = apiKey,
        fileName = fileName,
        mimeType = mimeType,
        content = ProgressFileBody(file, mimeType.toMediaType(), onProgress),
        purpose = purpose,
    )

    private suspend fun upload(
        apiKey: String,
        fileName: String,
        mimeType: String,
        content: RequestBody,
        purpose: FilePurpose,
    ): UploadedFile = withContext(Dispatchers.IO) {
        val prepared = FilesRequests.build(baseUrl, apiKey, fileName, mimeType, content, purpose)
        val request = Request.Builder()
            .url(prepared.url)
            .post(prepared.body)
            .apply { prepared.headers.forEach { (name, value) -> header(name, value) } }
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw ChatException(ErrorKind.NETWORK, message = e.message ?: "上传失败", cause = e)
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                val body = runCatching { resp.body?.string() }.getOrNull().orEmpty()
                throw ChatException(
                    kind = kindFor(resp.code),
                    httpStatus = resp.code,
                    message = body.ifBlank { "HTTP ${resp.code}" },
                )
            }
            val text = runCatching { resp.body?.string() }.getOrNull()
                ?: throw ChatException(ErrorKind.UPLOAD, resp.code, "上传响应为空")
            UploadedFile(
                fileId = FilesRequests.parseFileId(text),
                fileName = fileName,
                purpose = purpose,
            )
        }
    }

    private fun kindFor(code: Int): ErrorKind = when (code) {
        401 -> ErrorKind.HTTP_401
        402 -> ErrorKind.HTTP_402
        422, 413 -> ErrorKind.HTTP_422
        429 -> ErrorKind.HTTP_429
        in 500..599 -> ErrorKind.HTTP_5XX
        else -> ErrorKind.UPLOAD
    }
}

/** 流式上传 + 进度回调（每 64KiB 一次，够画进度条了） */
private class ProgressFileBody(
    private val file: File,
    private val mediaType: MediaType?,
    private val onProgress: (Long, Long) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = file.length()
        var sent = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                sink.write(buffer, 0, read)
                sent += read
                onProgress(sent, total)
            }
        }
    }
}
