package io.tinychat.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 抽屉里的一行会话。与 `SessionEntity` 解耦，方便单测分组与标题兜底。
 */
data class SessionRow(
    val id: String,
    val title: String?,
    val pinned: Boolean,
    val updatedAt: Long,
) {
    /** 新会话还没起标题时显示「新对话」（对齐 02 §12.3） */
    val displayTitle: String get() = title?.trim()?.takeIf { it.isNotEmpty() } ?: "新对话"
}

/** 分组顺序即枚举顺序：置顶 → 今天 → 昨天 → 7 天内 → 30 天内 → 更早。 */
enum class SessionGroup(val label: String) {
    PINNED("置顶"),
    TODAY("今天"),
    YESTERDAY("昨天"),
    LAST_7_DAYS("7 天内"),
    LAST_30_DAYS("30 天内"),
    EARLIER("更早"),
}

data class SessionSection(val group: SessionGroup, val rows: List<SessionRow>)

/**
 * 按 02 §12.3 分组。
 *
 * 置顶组不分时间，全部排在最上面；其余按 `updated_at` 落在哪个自然日归组。
 * 空组不出现（不显示只有标题没有内容的空分组）。
 */
fun groupSessions(
    rows: List<SessionRow>,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): List<SessionSection> {
    if (rows.isEmpty()) return emptyList()

    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val pinned = rows.filter { it.pinned }
    val rest = rows.filterNot { it.pinned }

    val buckets = linkedMapOf<SessionGroup, MutableList<SessionRow>>()
    rest.forEach { row ->
        buckets.getOrPut(groupOf(row.updatedAt, today, zone)) { mutableListOf() } += row
    }

    return buildList {
        if (pinned.isNotEmpty()) add(SessionSection(SessionGroup.PINNED, pinned))
        SessionGroup.entries
            .filter { it != SessionGroup.PINNED }
            .forEach { group ->
                buckets[group]?.let { add(SessionSection(group, it)) }
            }
    }
}

private fun groupOf(updatedAt: Long, today: LocalDate, zone: ZoneId): SessionGroup {
    val date = Instant.ofEpochMilli(updatedAt).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        // 时钟漂移导致的「未来时间」按今天算，不单列怪分组
        days <= 0L -> SessionGroup.TODAY
        days == 1L -> SessionGroup.YESTERDAY
        days < 7L -> SessionGroup.LAST_7_DAYS
        days < 30L -> SessionGroup.LAST_30_DAYS
        else -> SessionGroup.EARLIER
    }
}
