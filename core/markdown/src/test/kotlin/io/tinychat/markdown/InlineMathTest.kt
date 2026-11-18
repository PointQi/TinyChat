package io.tinychat.markdown

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 行内/块级公式的范围识别（M2-5）。这层必须是纯逻辑 + 单测：
 * WebView 渲染没法用 Robolectric 验，但「哪些 `$` 是公式、哪些不是」可以钉死。
 */
class InlineMathTest {

    @Test
    fun `识别基本行内公式`() {
        val found = findInlineMath("质能方程 \$E=mc^2\$ 很好看")
        assertEquals(1, found.size)
        assertEquals("E=mc^2", found[0].latex)
    }

    @Test
    fun `不把美元金额当公式`() {
        assertTrue(findInlineMath("价格 \$5 和 \$10 应该原样").isEmpty())
        assertTrue(findInlineMath("US\$100 到 US\$200").isEmpty())
    }

    @Test
    fun `多个公式次序正确`() {
        val found = findInlineMath("从 \$a+b\$ 到 \$c\$")
        assertEquals(listOf("a+b", "c"), found.map { it.latex })
    }

    @Test
    fun `转义的美元不算公式`() {
        assertTrue(findInlineMath("这是 \$ 5 元").isEmpty())
        assertTrue(findInlineMath("转义 \\\$x=1\$ 不应该开").isEmpty())
    }

    @Test
    fun `行内代码里的美元不算公式`() {
        assertTrue(findInlineMath("`awk '{print \$1}'`").isEmpty())
    }

    @Test
    fun `跨空行不闭合时不吞内容`() {
        assertTrue(findInlineMath("\$a+b\n\n\$c").isEmpty())
    }

    @Test
    fun `流式阶段只吞定界符且保留 span`() {
        val source = AnnotatedString("前 \$x^2\$ 后")
        val formulas = findInlineMath(source.text)
        val result = source.applyInlineMath(formulas, replace = false)
        assertEquals("前 x^2 后", result.text)
    }

    @Test
    fun `替换阶段插入占位符且文本变短`() {
        val source = AnnotatedString("前 \$x^2\$ 后")
        val formulas = findInlineMath(source.text)
        val result = source.applyInlineMath(formulas, replace = true)
        // 占位符是单个内联内容字符：原来的 "$x^2$"（5 个字）收缩成 1 个
        assertEquals(source.text.length - 4, result.text.length)
        assertTrue(result.text.none { it == '$' })
    }

    @Test
    fun `识别块级公式并给出原文区间`() {
        val text = "文字\n\$\$\na^2+b^2=c^2\n\$\$"
        val blocks = findBlockMath(text)
        assertEquals(1, blocks.size)
        assertEquals("a^2+b^2=c^2", blocks[0].latex)
        assertEquals("$$", text.substring(blocks[0].start, blocks[0].start + 2))
        assertEquals("$$", text.substring(blocks[0].end - 2, blocks[0].end))
    }

    @Test
    fun `多个块级公式与空公式`() {
        val blocks = findBlockMath("$$\na\n$$\n中间\n$$\nb\n$$\n\$\$\$\$")
        assertEquals(listOf("a", "b"), blocks.map { it.latex })
    }

    @Test
    fun `块级与行内同时存在时各归各的`() {
        val text = "先 \$x\$ 再\n\$\$y\$\$"
        assertEquals(1, findInlineMath("\$x\$").size)
        assertEquals(1, findBlockMath(text).size)
        assertEquals("y", findBlockMath(text).first().latex)
    }
}
