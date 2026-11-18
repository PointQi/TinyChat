package io.tinychat.model

/**
 * 一次请求的用量。字段名对齐服务端 `usage` 对象（02 §4.7）。
 *
 * 铁律：以**最后一个** `message_delta.usage` 为准，不与 `message_start` 累加。
 */
data class Usage(
    /** input_tokens（未命中缓存的部分） */
    val promptTokens: Int = 0,
    /** cache_read_input_tokens */
    val cacheHitTokens: Int = 0,
    /** output_tokens */
    val completionTokens: Int = 0,
    /** output_tokens_details.thinking_tokens */
    val thinkingTokens: Int = 0,
    val safetyTokens: Int = 0,
    /** server_tool_use(action=search) 次数 */
    val searchCalls: Int = 0,
    /** server_tool_use(action=open_page) 次数 */
    val fetchCalls: Int = 0,
    val imageSearchCalls: Int = 0,
) {
    val totalInput: Int get() = promptTokens + cacheHitTokens

    /** 上下文圆环的分子 */
    val contextUsed: Int get() = totalInput + completionTokens

    val isEmpty: Boolean
        get() = promptTokens == 0 && cacheHitTokens == 0 && completionTokens == 0 &&
            safetyTokens == 0 && searchCalls == 0 && fetchCalls == 0 && imageSearchCalls == 0

    companion object {
        val ZERO = Usage()
    }
}

/**
 * 错误分类（02 §4.5）。`wire` 落库，避免枚举名与导出/日志耦合。
 */
enum class ErrorKind(val wire: String) {
    /** 连接失败 / DNS / 超时 */
    NETWORK("network"),
    /** Key 无效 */
    HTTP_401("http.401"),
    /** 余额不足 */
    HTTP_402("http.402"),
    /** 请求体不合法 */
    HTTP_422("http.422"),
    /** 并发 / 限流 */
    HTTP_429("http.429"),
    HTTP_5XX("http.5xx"),
    /** 流中 error 帧 */
    STREAM("stream"),
    /** 流提前断开且无 stop_reason */
    INTERRUPTED("interrupted"),
    /** stop_reason=refusal */
    REFUSAL("refusal"),
    UPLOAD("upload"),
    /** 服务端判 file_id 失效 */
    FILE_INVALID("fileInvalid"),
    ;

    /** 是否允许自动重试（refusal / 401 / 402 / 422 一律不允许） */
    val retryable: Boolean
        get() = when (this) {
            NETWORK, HTTP_429, HTTP_5XX -> true
            else -> false
        }

    /**
     * 给用户看的一句话（02 §4.5 表格逐条对齐）。
     *
     * 文案只描述**发生了什么、下一步怎么办**，不暴露 HTTP 码与技术细节。
     */
    val userMessage: String
        get() = when (this) {
            NETWORK -> "网络异常，请检查网络后重试"
            HTTP_401 -> "API Key 无效或已删除"
            HTTP_402 -> "账户余额不足，请充值"
            HTTP_422 -> "请求参数有误"
            HTTP_429 -> "请求过于频繁，请稍后再试"
            HTTP_5XX -> "服务器繁忙，请稍后重试"
            STREAM -> "生成出错"
            INTERRUPTED -> "生成被中断"
            REFUSAL -> "这条回答无法提供"
            UPLOAD -> "附件上传失败"
            FILE_INVALID -> "附件已失效，请重新上传"
        }

    /** 行内重试提示（贴在消息下方右侧的灰字）；官方文案是「请检查网络后重试」 */
    val retryHint: String
        get() = when (this) {
            NETWORK -> "请检查网络后重试"
            HTTP_429 -> "请求过于频繁，请稍后重试"
            HTTP_5XX -> "服务器繁忙，请稍后重试"
            else -> userMessage
        }

    /** 自动重试上限：限流只补一次，服务端故障/网络抖动最多两次 */
    val maxAutoRetries: Int
        get() = when (this) {
            HTTP_429 -> 1
            NETWORK, HTTP_5XX -> 2
            else -> 0
        }

    companion object {
        fun fromWire(value: String?): ErrorKind? = entries.firstOrNull { it.wire == value }
    }
}
