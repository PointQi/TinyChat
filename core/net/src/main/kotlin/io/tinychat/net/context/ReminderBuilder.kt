package io.tinychat.net.context

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * `latest_reminder` 行（02 §5.4）：
 * `{date},{weekday},{location},{platform=App},{language}`
 *
 * 例：`2026-11-17,星期一,,App,中文`
 * - location 为空时**保留逗号**（服务端要求 5 段）；
 * - language 必须取官方 70+ 取值表里的值，不要自造。
 */
object ReminderBuilder {

    fun build(
        date: LocalDate,
        location: String?,
        language: String = "中文",
        platform: String = "App",
    ): String = listOf(
        date.toString(),
        weekdayOf(date.dayOfWeek),
        location.orEmpty(),
        platform,
        language,
    ).joinToString(",")

    private fun weekdayOf(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "星期一"
        DayOfWeek.TUESDAY -> "星期二"
        DayOfWeek.WEDNESDAY -> "星期三"
        DayOfWeek.THURSDAY -> "星期四"
        DayOfWeek.FRIDAY -> "星期五"
        DayOfWeek.SATURDAY -> "星期六"
        DayOfWeek.SUNDAY -> "星期日"
    }
}
