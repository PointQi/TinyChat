package io.tinychat.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CitationAnchorsTest {

    @Test
    fun `锚点落在段落内`() {
        val anchors = listOf(CitationAnchor(10, 1))
        assertEquals(listOf(1), anchorsIn(anchors, 0, 20).map { it.index })
    }

    @Test
    fun `锚点正好在段末算这一段`() {
        // 'offset > start && offset <= end'：段末（含）属于本段
        val anchors = listOf(CitationAnchor(20, 2))
        assertEquals(listOf(2), anchorsIn(anchors, 0, 20).map { it.index })
    }

    @Test
    fun `锚点比段末靠后不算`() {
        val anchors = listOf(CitationAnchor(21, 3))
        assertTrue(anchorsIn(anchors, 0, 20).isEmpty())
    }

    @Test
    fun `同一段多个锚点保持顺序`() {
        val anchors = listOf(CitationAnchor(5, 1), CitationAnchor(12, 2))
        assertEquals(listOf(1, 2), anchorsIn(anchors, 0, 20).map { it.index })
    }

    @Test
    fun `没有锚点直接短路`() {
        assertTrue(anchorsIn(emptyList(), 0, 100).isEmpty())
    }
}
