package io.tinychat.model

enum class Role(val wire: String) {
    USER("USER"),
    ASSISTANT("ASSISTANT"),
    ;

    companion object {
        fun fromWire(value: String?): Role? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * 消息状态。前四个是 v3 导出里真实出现过的，后三个是本地状态。
 */
enum class MessageStatus(val wire: String) {
    /** 正在流式生成（仅本地，进程死亡后启动时归为 INCOMPLETE） */
    WIP("WIP"),
    FINISHED("FINISHED"),

    /** 用户停止 / 提前 EOF / 达到 max_tokens；内容可用 */
    INCOMPLETE("INCOMPLETE"),
    FAILED("FAILED"),
    TIMEOUT("TIMEOUT"),
    CONTENT_FILTER("CONTENT_FILTER"),
    CONTEXT_LENGTH_EXCEEDED("CONTEXT_LENGTH_EXCEEDED"),
    ;

    /**
     * 能否进入上下文（02 §5.2 规则 3）。
     * 只放状态位，`stop_reason=refusal`、模板块、空正文等条件由上下文重建器再判。
     */
    val isUsableInContext: Boolean
        get() = this == FINISHED || this == INCOMPLETE

    val isTerminal: Boolean
        get() = this != WIP

    companion object {
        fun fromWire(value: String?): MessageStatus? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * 消息树节点。`parentId` 表达分支：同一 USER 下的多个 ASSISTANT 是重新生成的兄弟。
 *
 * `id` 由数据库全局自增（决策 22），导出时写回该值。
 */
data class Message(
    val id: Long = 0,
    val sessionId: String,
    val parentId: Long? = null,
    val role: Role,
    val status: MessageStatus,
    val thinkingEnabled: Boolean = false,
    val searchEnabled: Boolean = false,
    val accumulatedTokenUsage: Int = 0,
    val insertedAt: Long,
    // —— 以下仅本地，不导出 ——
    val model: String? = null,
    val effort: ThinkingEffort? = null,
    val usage: Usage? = null,
    val errorKind: ErrorKind? = null,
    val errorMessage: String? = null,
    /** 该轮 latest_reminder 快照；null = 本轮没发 reminder */
    val reminderSnapshot: String? = null,
    /** 本轮思考的 signature，继续生成时要回传 */
    val thinkingSignature: String? = null,
)
