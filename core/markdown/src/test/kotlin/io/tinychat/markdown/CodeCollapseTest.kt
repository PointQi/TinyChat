package io.tinychat.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeCollapseTest {

    private fun code(lines: Int): String = (1..lines).joinToString("\n") { "line $it" }

    @Test
    fun `短代码不折叠`() {
        val (visible, collapsible) = collapsedCode(code(5), expanded = false, streaming = false)
        assertFalse(collapsible)
        assertEquals(code(5), visible)
    }

    @Test
    fun `刚好等于阈值也不折叠`() {
        val (_, collapsible) = collapsedCode(code(CollapseAfterLines), expanded = false, streaming = false)
        assertFalse(collapsible)
    }

    @Test
    fun `超长代码默认折到阈值的行数`() {
        val (visible, collapsible) = collapsedCode(code(50), expanded = false, streaming = false)
        assertTrue(collapsible)
        assertEquals(CollapseAfterLines, visible.lines().size)
        assertEquals("line 1", visible.lines().first())
    }

    @Test
    fun `展开后给全文`() {
        val (visible, collapsible) = collapsedCode(code(50), expanded = true, streaming = false)
        assertTrue(collapsible)
        assertEquals(50, visible.lines().size)
    }

    @Test
    fun `流式期间绝不折叠`() {
        // 否则输出到第 21 行会突然塌成 20 行，看着像内容丢了
        val (visible, collapsible) = collapsedCode(code(50), expanded = false, streaming = true)
        assertFalse(collapsible)
        assertEquals(50, visible.lines().size)
    }
}
