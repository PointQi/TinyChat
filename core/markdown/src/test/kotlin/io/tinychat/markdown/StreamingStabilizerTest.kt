package io.tinychat.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * setext 标记的稳定化（M2-3 抖动的第一坑）。
 *
 * 核心约束有两条，错一条就会引入更难查的问题：
 * 1. **长度必须不变** —— 引用角标的偏移是对着原文算的；
 * 2. 围栏代码块、空行之后、缩进 4 格以上的行都**不能改**（那不是 setext）。
 */
class StreamingStabilizerTest {

    @Test
    fun `正文后紧跟三个减号会被换成分隔线`() {
        assertEquals("一段正文\n***\n", stabilizeSetextMarkers("一段正文\n---\n"))
    }

    @Test
    fun `正文后紧跟等号线也会被换掉`() {
        assertEquals("标题候选\n***\n", stabilizeSetextMarkers("标题候选\n===\n"))
    }

    @Test
    fun `空行之后的分隔线保持原样`() {
        val src = "一段正文\n\n---\n"
        assertEquals(src, stabilizeSetextMarkers(src))
    }

    @Test
    fun `围栏代码块里的减号不动`() {
        val src = "```\n一段正文\n---\n```\n"
        assertEquals(src, stabilizeSetextMarkers(src))
    }

    @Test
    fun `四个空格缩进的是代码块不动`() {
        val src = "一段正文\n    ---\n"
        assertEquals(src, stabilizeSetextMarkers(src))
    }

    @Test
    fun `本来就是星号的分隔线不动`() {
        val src = "一段正文\n***\n"
        assertEquals(src, stabilizeSetextMarkers(src))
    }

    @Test
    fun `长度恒等`() {
        val cases = listOf(
            "a\n---\n",
            "a\n===\n",
            "a\n\n---\n",
            "```\na\n---\n```\n",
            "长文本一段。\n---\n后面还有\n=== \n",
            "",
            "没有标记的正文",
        )
        cases.forEach { src ->
            assertEquals("length of <$src>", src.length, stabilizeSetextMarkers(src).length)
        }
    }

    @Test
    fun `连续两条分隔线都处理`() {
        assertEquals("a\n***\nb\n***\n", stabilizeSetextMarkers("a\n---\nb\n---\n"))
    }

    @Test
    fun `末尾未闭合的标记行也处理（流式最后一帧）`() {
        assertEquals("一段正文\n***", stabilizeSetextMarkers("一段正文\n---"))
    }
}
