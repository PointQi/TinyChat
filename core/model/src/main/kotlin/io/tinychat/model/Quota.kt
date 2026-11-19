package io.tinychat.model

/**
 * 订阅制服务商的额度窗口（M8-6）。
 *
 * OpenCode Go 的 `/v1/usage` 只给「已用百分比 + 重置时间」，没有金额明细；
 * 因此这里统一换算成**剩余百分比**（与 ￥ 余额的语义一致），钱的计算留给服务商自己。
 */
data class QuotaWindow(
    /** `rolling` / `weekly` / `monthly` */
    val key: String,
    /** 展示名：5 小时 / 本周 / 本月 */
    val label: String,
    /** 上游给的**已用**百分比 0–100 */
    val usedPercent: Int,
    /** ISO 时间；上游没给就是 null */
    val resetAt: String?,
) {
    val remainingPercent: Int get() = (100 - usedPercent).coerceIn(0, 100)

    val exhausted: Boolean get() = usedPercent >= 100
}

data class QuotaSnapshot(
    val windows: List<QuotaWindow>,
    val fetchedAt: Long,
)
