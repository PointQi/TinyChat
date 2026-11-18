package io.tinychat.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 行内公式的度量换算与行高撑开（M2-5 收尾）。
 *
 * 这些数字错一点，肉眼就是「公式整体高出一截」或「分母被下一行切掉」，
 * 所以按「像素 → sp → 行高」逐层钉死。
 */
class InlineMathLayoutTest {

    // ---------- 度量换算 ----------

    @Test
    fun `像素按 density 与 fontScale 折成 sp`() {
        // density 2.0 + fontScale 2.0 → 1 sp = 4 px
        val metrics = inlineMathMetrics(widthPx = 200, heightPx = 100, baselinePx = 80, pxPerSp = 4f)
        assertEquals(50f, metrics.widthSp, 0.001f)
        assertEquals(25f, metrics.heightSp, 0.001f)
        assertEquals(20f, metrics.aboveBaselineSp, 0.001f)
        assertEquals(5f, metrics.belowBaselineSp, 0.001f)
    }

    @Test
    fun `没量到基线时整张图都算在基线之上`() {
        // 探针失败会回 0：宁可整图贴基线，也不能算出一个负的上下半
        val metrics = inlineMathMetrics(widthPx = 40, heightPx = 40, baselinePx = 0, pxPerSp = 2f)
        assertEquals(20f, metrics.heightSp, 0.001f)
        assertEquals(20f, metrics.aboveBaselineSp, 0.001f)
        assertEquals(0f, metrics.belowBaselineSp, 0.001f)
    }

    @Test
    fun `基线等于图高视为没量到`() {
        val metrics = inlineMathMetrics(widthPx = 40, heightPx = 40, baselinePx = 40, pxPerSp = 2f)
        assertEquals(20f, metrics.aboveBaselineSp, 0.001f)
        assertEquals(0f, metrics.belowBaselineSp, 0.001f)
    }

    @Test
    fun `基线越界不会算出负数`() {
        val metrics = inlineMathMetrics(widthPx = 40, heightPx = 40, baselinePx = 400, pxPerSp = 2f)
        assertEquals(20f, metrics.aboveBaselineSp, 0.001f)
        assertEquals(0f, metrics.belowBaselineSp, 0.001f)
    }

    @Test
    fun `上下两段之和恒等于图高`() {
        listOf(0, 1, 17, 39, 40).forEach { baseline ->
            val metrics = inlineMathMetrics(64, 40, baseline, 2f)
            assertEquals(
                "baseline=$baseline",
                metrics.heightSp,
                metrics.aboveBaselineSp + metrics.belowBaselineSp,
                0.001f,
            )
        }
    }

    @Test
    fun `pxPerSp 异常时退化为 1 而不是除零`() {
        val metrics = inlineMathMetrics(widthPx = 30, heightPx = 20, baselinePx = 10, pxPerSp = 0f)
        assertEquals(30f, metrics.widthSp, 0.001f)
        assertEquals(20f, metrics.heightSp, 0.001f)
        assertEquals(10f, metrics.aboveBaselineSp, 0.001f)
    }

    // ---------- 行高撑开 ----------

    @Test
    fun `矮公式不改变原行高`() {
        // 正文 28sp 行高，一个 x 级别的公式只有 20sp 高，不该把行撑开
        val metrics = listOf(inlineMathMetrics(60, 40, 32, 2f))
        assertEquals(28f, inlineMathLineHeight(28f, metrics, gapSp = 3f), 0.001f)
    }

    @Test
    fun `高公式按上半加下伸撑开行高`() {
        // 上半 24 + 下伸 10 + 余量 3 = 37 > 28
        val metrics = listOf(inlineMathMetrics(widthPx = 120, heightPx = 68, baselinePx = 48, pxPerSp = 2f))
        assertEquals(37f, inlineMathLineHeight(28f, metrics, gapSp = 3f), 0.001f)
    }

    @Test
    fun `多张公式取最高的上半与最深的下伸而不是相加`() {
        val tall = inlineMathMetrics(widthPx = 60, heightPx = 60, baselinePx = 50, pxPerSp = 2f) // 上 25 下 5
        val deep = inlineMathMetrics(widthPx = 60, heightPx = 60, baselinePx = 20, pxPerSp = 2f) // 上 10 下 20
        // 单看 tall：上 25 + 下 5 + 3 = 33，比正文 28 高一点，确实要撑
        assertEquals(33f, inlineMathLineHeight(28f, listOf(tall), gapSp = 3f), 0.001f)
        // 上 25 + 下 20 + 3 = 48：两张图在同一段落时，行高必须同时容下最高与最深
        assertEquals(48f, inlineMathLineHeight(28f, listOf(tall, deep), gapSp = 3f), 0.001f)
    }

    @Test
    fun `没有公式时原行高原样返回`() {
        assertEquals(28f, inlineMathLineHeight(28f, emptyList(), gapSp = 3f), 0.001f)
    }
}
