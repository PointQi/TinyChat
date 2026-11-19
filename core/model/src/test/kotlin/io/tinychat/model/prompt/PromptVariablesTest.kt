package io.tinychat.model.prompt

import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptVariablesTest {

    private val now = ZonedDateTime.parse("2026-11-17T09:05:00+08:00[Asia/Shanghai]")

    @Test
    fun `替换全部白名单变量`() {
        val text = "今天是 {{date}} {{weekday}}，现在 {{time}}，" +
            "我在 {{location}}，说 {{language}}，模型是 {{model}}。"
        val out = PromptVariables.evaluate(
            text = text,
            now = now,
            location = "浙江",
            language = "中文",
            model = "DeepSeek V4.1 Flash",
        )
        assertEquals(
            "今天是 2026-11-17 星期二，现在 09:05，我在 浙江，说 中文，模型是 DeepSeek V4.1 Flash。",
            out,
        )
    }

    @Test
    fun `未知变量原样保留且能被查出来`() {
        val text = "{{date}} {{role}} {{Date}}"
        assertEquals(listOf("role", "Date"), PromptVariables.unknown(text))
        // 大小写敏感：Date 不在白名单，保留原文
        assertEquals("2026-11-17 {{role}} {{Date}}", PromptVariables.evaluate(text, now))
    }

    @Test
    fun `花括号里带空格也算同一个变量`() {
        assertEquals("2026-11-17", PromptVariables.evaluate("{{ date }}", now))
        assertTrue(PromptVariables.unknown("{{  date  }}").isEmpty())
    }

    @Test
    fun `没有变量时原样返回`() {
        val text = "你是一位耐心的老师。"
        assertEquals(text, PromptVariables.evaluate(text, now))
        assertTrue(PromptVariables.unknown(text).isEmpty())
    }

    @Test
    fun `空文本不炸`() {
        assertEquals("", PromptVariables.evaluate("", now))
    }

    @Test
    fun `地点为空时替换成空串`() {
        assertEquals("我在 。", PromptVariables.evaluate("我在 {{location}}。", now))
    }

    @Test
    fun `星期用中文且与 reminder 一致`() {
        val sunday = ZonedDateTime.parse("2026-11-22T12:00:00+08:00[Asia/Shanghai]")
        assertEquals("星期日", PromptVariables.evaluate("{{weekday}}", sunday))
        assertEquals("星期二", PromptVariables.evaluate("{{weekday}}", now))
    }
}
