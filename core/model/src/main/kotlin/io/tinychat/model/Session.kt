package io.tinychat.model

/**
 * 系统提示词的三种作用方式，与 v3 导出、请求体 system 字段一一对应。
 */
enum class PromptMode(val wire: String) {
    REPLACE("replace"),
    APPEND("append"),
    CLEAR("clear"),
    ;

    companion object {
        fun fromWire(value: String?): PromptMode =
            entries.firstOrNull { it.wire == value } ?: REPLACE
    }
}

/**
 * 会话。字段与 02 §3.1 的 `sessions` 表一一对应。
 *
 * 注意：`providerId` / `model` / `effort` / `systemPromptSnapshot` 都是**创建时快照**，
 * 之后全局设置怎么改都不回写（决策 12）。
 */
data class Session(
    val id: String,
    val title: String? = null,
    val pinned: Boolean = false,
    /** 当前分支的叶子消息；渲染只走这条链 */
    val currentMessageId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val providerId: String,
    val model: String,
    val thinkingEnabled: Boolean = false,
    val searchEnabled: Boolean = false,
    val effort: ThinkingEffort? = null,
    /** null 表示不发送 system */
    val systemPromptSnapshot: String? = null,
    val systemPromptMode: PromptMode = PromptMode.REPLACE,
    /** 软删；附件由异步任务清理 */
    val deleted: Boolean = false,
)
