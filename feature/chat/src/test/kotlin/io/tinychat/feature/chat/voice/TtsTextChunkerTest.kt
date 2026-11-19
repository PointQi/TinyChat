package io.tinychat.feature.chat.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsTextChunkerTest {

    @Test
    fun `空文本切不出段`() {
        assertEquals(emptyList<String>(), TtsTextChunker.split(""))
        assertEquals(emptyList<String>(), TtsTextChunker.split("   \n "))
    }

    @Test
    fun `短文本不切`() {
        val text = "你好。今天天气不错。"
        assertEquals(listOf(text), TtsTextChunker.split(text))
    }

    @Test
    fun `按句末标点组段且不超过上限`() {
        val text = (1..40).joinToString("") { "这是第${it}句测试内容。" }
        val segments = TtsTextChunker.split(text, limit = 60)
        assertTrue("段数应当大于 1", segments.size > 1)
        segments.forEach { assertTrue("段超限：${it.length}", TtsTextChunker.billingChars(it) <= 60) }
        // 拼回去内容不少：只差首尾空白
        assertEquals(text, segments.joinToString(""))
    }

    @Test
    fun `汉字按两个计费字符算`() {
        assertEquals(4, TtsTextChunker.billingChars("你好"))
        assertEquals(2, TtsTextChunker.billingChars("ab"))
        assertEquals(6, TtsTextChunker.billingChars("你好ab"))
        // 标点、数字、空格都算 1（官方口径：只有汉字算 2）
        assertEquals(1, TtsTextChunker.billingChars("，"))
        assertEquals(1, TtsTextChunker.billingChars(" "))
    }

    @Test
    fun `单句超长会被硬切`() {
        val text = "啊".repeat(500) // 1000 计费字符
        val segments = TtsTextChunker.split(text, limit = 200)
        assertTrue(segments.size >= 5)
        segments.forEach { assertTrue(TtsTextChunker.billingChars(it) <= 200) }
        assertEquals(text, segments.joinToString(""))
    }

    @Test
    fun `超长句子前面还有正常句子时先收尾再硬切`() {
        val text = "第一句。" + "长".repeat(300) + "。"
        val segments = TtsTextChunker.split(text, limit = 100)
        assertTrue(segments.size > 3)
        segments.forEach { assertTrue(TtsTextChunker.billingChars(it) <= 100) }
        assertEquals(text, segments.joinToString(""))
    }

    @Test
    fun `不会把 emoji 的代理对劈开`() {
        val text = "😀".repeat(200) // 每个占 2 个 UTF-16 单元
        val segments = TtsTextChunker.split(text, limit = 100)
        segments.forEach { segment ->
            assertEquals("段内出现半个代理对", 0, segment.count { it.isHighSurrogate() } - segment.count { it.isLowSurrogate() })
        }
        assertEquals(text, segments.joinToString(""))
    }

    @Test
    fun `默认上限下五千汉字会被切成两段以上`() {
        val text = "人".repeat(5_000) // 10000 计费字符，正好顶格
        val segments = TtsTextChunker.split(text)
        assertTrue(segments.size >= 2)
        segments.forEach { assertTrue(TtsTextChunker.billingChars(it) <= TtsTextChunker.MAX_BILLING_CHARS) }
    }
}
