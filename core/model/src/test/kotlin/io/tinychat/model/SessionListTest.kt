package io.tinychat.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 抽屉分组（02 §12.3）：置顶不分时间排最前，其余按自然日落到 今天/昨天/7 天内/30 天内/更早。
 */
class SessionListTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 固定「现在」= 2026-11-18 10:00，避免用真实时钟导致跨日抖动 */
    private val now = at(2026, 11, 18, 10)
    private val today = LocalDate.of(2026, 11, 18)

    @Test
    fun `置顶全部排最前且不按时间再分`() {
        val rows = listOf(
            row("a", "置顶很久以前", pinned = true, updated = at(2025, 1, 1, 9)),
            row("b", "今天", updated = at(2026, 11, 18, 9)),
            row("c", "也置顶", pinned = true, updated = at(2026, 11, 17, 9)),
        )

        val sections = groupSessions(rows, now, zone)

        assertEquals(SessionGroup.PINNED, sections.first().group)
        assertEquals(listOf("a", "c"), sections.first().rows.map { it.id })
        assertEquals(
            listOf(SessionGroup.PINNED, SessionGroup.TODAY),
            sections.map { it.group },
        )
    }

    @Test
    fun `按自然日落到五个分组且空组不出现`() {
        val rows = listOf(
            row("today", "今天", updated = at(2026, 11, 18, 0)),
            row("yesterday", "昨天", updated = at(2026, 11, 17, 23)),
            row("week", "三天前", updated = at(2026, 11, 15, 12)),
            row("month", "二十天前", updated = at(2026, 10, 29, 12)),
            row("earlier", "两个月前", updated = at(2026, 9, 1, 12)),
        )

        val sections = groupSessions(rows, now, zone)

        assertEquals(
            listOf(
                SessionGroup.TODAY,
                SessionGroup.YESTERDAY,
                SessionGroup.LAST_7_DAYS,
                SessionGroup.LAST_30_DAYS,
                SessionGroup.EARLIER,
            ),
            sections.map { it.group },
        )
        assertEquals(listOf("today"), sections[0].rows.map { it.id })
        assertEquals(listOf("week"), sections[2].rows.map { it.id })
        assertEquals(listOf("earlier"), sections[4].rows.map { it.id })
    }

    @Test
    fun `第 7 天整落到 30 天组，不是 7 天内`() {
        val sevenDaysAgo = row("d7", "七天前", updated = today.minusDays(7).atTime(12, 0).toEpoch(zone))

        val sections = groupSessions(listOf(sevenDaysAgo), now, zone)

        assertEquals(SessionGroup.LAST_30_DAYS, sections.single().group)
    }

    @Test
    fun `第 30 天整落到更早`() {
        val thirtyDaysAgo = row("d30", "三十天前", updated = today.minusDays(30).atTime(12, 0).toEpoch(zone))

        val sections = groupSessions(listOf(thirtyDaysAgo), now, zone)

        assertEquals(SessionGroup.EARLIER, sections.single().group)
    }

    @Test
    fun `时钟漂移的未来时间按今天算`() {
        val future = row("f", "明天", updated = today.plusDays(1).atTime(9, 0).toEpoch(zone))

        val sections = groupSessions(listOf(future), now, zone)

        assertEquals(SessionGroup.TODAY, sections.single().group)
    }

    @Test
    fun `没有会话时不出任何分组`() {
        assertEquals(emptyList<SessionSection>(), groupSessions(emptyList(), now, zone))
    }

    @Test
    fun `标题为空的会话显示新对话`() {
        assertEquals("新对话", row("x", null, updated = now).displayTitle)
        assertEquals("新对话", row("x", "   ", updated = now).displayTitle)
        assertEquals("写点东西", row("x", " 写点东西 ", updated = now).displayTitle)
    }

    private fun row(
        id: String,
        title: String?,
        pinned: Boolean = false,
        updated: Long = now,
    ) = SessionRow(id = id, title = title, pinned = pinned, updatedAt = updated)

    private fun at(year: Int, month: Int, day: Int, hour: Int): Long =
        LocalDateTime.of(year, month, day, hour, 0).toEpoch(zone)

    private fun LocalDateTime.toEpoch(zone: ZoneId): Long = atZone(zone).toInstant().toEpochMilli()
}
