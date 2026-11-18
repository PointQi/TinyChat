package io.tinychat.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `\(…\)` / `\[…\]` 兼容层（真机发现：模型主要用 LaTeX 定界符，不用 `$`）。
 *
 * 两个必须守住的性质：
 * 1. 行内 `\(…\)` 归一成 `$…$`，长度会变 —— **偏移映射必须同时给对**，否则引用角标错位；
 * 2. 代码块 / 行内代码里的反斜杠是字面量，不能动。
 */
class MathDelimitersTest {

    @Test
    fun `行内定界符归一成美元符`() {
        val result = normalizeInlineMathDelimiters("面积是 \\(\\pi r^2\\) 这么大")
        assertEquals("面积是 \$\\pi r^2\$ 这么大", result.text)
    }

    @Test
    fun `块级定界符不动（走原始文本识别）`() {
        val src = "\\[ a^2 + b^2 = c^2 \\]"
        assertEquals(src, normalizeInlineMathDelimiters(src).text)
    }

    @Test
    fun `没有配对的开口保持原样`() {
        val src = "只有一个 \\( 开口"
        assertEquals(src, normalizeInlineMathDelimiters(src).text)
    }

    @Test
    fun `跨行不算行内公式`() {
        val src = "a \\( b\nc \\) d"
        assertEquals(src, normalizeInlineMathDelimiters(src).text)
    }

    @Test
    fun `行内代码里的不处理`() {
        val src = "写成 `\\(x\\)` 这样"
        assertEquals(src, normalizeInlineMathDelimiters(src).text)
    }

    @Test
    fun `围栏代码块里的不处理`() {
        val src = "```\n\\(x\\)\n```\n"
        assertEquals(src, normalizeInlineMathDelimiters(src).text)
    }

    @Test
    fun `双反斜杠后面的括号不当定界符`() {
        // LaTeX 里的换行 `\\` 后面紧跟 `(` 不该被当成行内公式开口
        val src = "a \\\\(b"
        assertEquals(src, normalizeInlineMathDelimiters(src).text)
    }

    @Test
    fun `偏移映射把锚点搬到新位置`() {
        val src = "ab\\(x\\)cd"
        val result = normalizeInlineMathDelimiters(src)
        assertEquals("ab\$x\$cd", result.text)
        assertEquals(0, result.mapOffset(0))
        assertEquals(2, result.mapOffset(2))
        // 落在被替换区间里的偏移折到替换起点
        assertEquals(2, result.mapOffset(3))
        // 末尾：9 → 7
        assertEquals(7, result.mapOffset(9))
    }

    @Test
    fun `多条公式的偏移映射累加`() {
        val src = "\\(a\\) 与 \\(b\\)"
        val result = normalizeInlineMathDelimiters(src)
        assertEquals("\$a\$ 与 \$b\$", result.text)
        assertEquals(src.length, 13)
        // 两条公式各短 2 个字符：13 - 4 = 9
        assertEquals(9, result.mapOffset(13))
    }

    // ---------- 块级定界符 ----------

    @Test
    fun `方括号块级公式能被识别`() {
        val blocks = findBlockMath("见 \\[ a^2+b^2 \\] 结束")
        assertEquals(1, blocks.size)
        assertEquals("a^2+b^2", blocks[0].latex)
        assertEquals("见 \\[ a^2+b^2 \\] 结束", "见 \\[ a^2+b^2 \\] 结束")
    }

    @Test
    fun `美元与方括号两种块级公式都找得到`() {
        val text = "$$\nx=1\n$$\n\n\\[\ny=2\n\\]"
        val blocks = findBlockMath(text)
        assertEquals(2, blocks.size)
        assertEquals("x=1", blocks[0].latex)
        assertEquals("y=2", blocks[1].latex)
        assertTrue(blocks[0].start < blocks[1].start)
    }

    @Test
    fun `未闭合的方括号不产出块级公式`() {
        assertTrue(findBlockMath("\\[ x=1").isEmpty())
    }
}
