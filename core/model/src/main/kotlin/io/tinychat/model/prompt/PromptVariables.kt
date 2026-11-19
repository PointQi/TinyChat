package io.tinychat.model.prompt

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 系统提示词变量（02 §5.6，M7-5-3）。
 *
 * 规则只有两条：
 * 1. **只替换白名单**——未知变量原样保留（请求里也不该出现被吃掉的内容）；
 * 2. **发送时求值**——日期、星期、时间必须实时，所以快照只存原文。
 *
 * `{{model}}` 取模型显示名（`DeepSeek V4.1 Flash` 这种），不是内部 id：
 * 提示词是给人看的自然语言，写 id 反而破坏语义。
 */
object PromptVariables {

    const val DATE = "date"
    const val WEEKDAY = "weekday"
    const val TIME = "time"
    const val LOCATION = "location"
    const val LANGUAGE = "language"
    const val MODEL = "model"

    /** 白名单；顺序即编辑器里按钮的顺序 */
    val names: List<String> = listOf(DATE, WEEKDAY, TIME, LOCATION, LANGUAGE, MODEL)

    private val PATTERN = Regex("""\{\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*\}\}""")
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

    /** 文本里出现的、不在白名单内的变量名（去重、按出现顺序）。 */
    fun unknown(text: String): List<String> = PATTERN.findAll(text)
        .map { it.groupValues[1] }
        .filter { it !in names }
        .distinct()
        .toList()

    /**
     * 替换全部白名单变量。`now` 由调用方给，方便单测钉死时间。
     * 未知变量与普通文本原样保留。
     */
    fun evaluate(
        text: String,
        now: ZonedDateTime,
        location: String = "",
        language: String = "中文",
        model: String = "",
    ): String {
        if (text.isEmpty()) return text
        return PATTERN.replace(text) { match ->
            when (match.groupValues[1]) {
                DATE -> now.format(DATE_FORMAT)
                WEEKDAY -> weekdayOf(now)
                TIME -> now.format(TIME_FORMAT)
                LOCATION -> location
                LANGUAGE -> language
                MODEL -> model
                else -> match.value
            }
        }
    }

    /** 星期与 `latest_reminder` 保持一致（同一个「星期几」不该有两套写法） */
    fun weekdayOf(now: ZonedDateTime): String = when (now.dayOfWeek.value) {
        1 -> "星期一"
        2 -> "星期二"
        3 -> "星期三"
        4 -> "星期四"
        5 -> "星期五"
        6 -> "星期六"
        else -> "星期日"
    }
}
