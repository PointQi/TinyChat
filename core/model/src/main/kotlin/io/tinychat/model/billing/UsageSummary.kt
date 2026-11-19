package io.tinychat.model.billing

import io.tinychat.model.Usage

/**
 * 一个会话的用量合计（M7-2 右抽屉用）。
 *
 * [costMicros] 是**逐条消息**用当时的价格与时段算出来的累计值，含已被丢弃/重生成的分支——
 * 那些轮次同样计费，所以不能只算当前分支。
 */
data class UsageSummary(
    val usage: Usage = Usage.ZERO,
    val costMicros: Long = 0,
) {
    /** 缓存命中率（0..1）：命中 / (命中 + 未命中)；没有输入 token 时为 `null`。 */
    val cacheHitRate: Double?
        get() {
            val input = usage.cacheHitTokens + usage.promptTokens
            return if (input == 0) null else usage.cacheHitTokens.toDouble() / input
        }

    val isEmpty: Boolean get() = usage.isEmpty && costMicros == 0L
}
