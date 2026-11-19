package io.tinychat.model.billing

import io.tinychat.model.Usage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class CostCalculatorTest {

    private val pricing = DefaultPricing.value

    /** 01 §2.4 的典型一轮：8K 命中 + 2K 未命中 + 1.5K 输出 + 1 次搜索，flash 空闲。 */
    private val typical = Usage(
        promptTokens = 2_000,
        cacheHitTokens = 8_000,
        completionTokens = 1_500,
        searchCalls = 1,
    )

    @Test
    fun `flash 空闲典型一轮约一分二`() {
        val micros = CostCalculator.micros(typical, "deepseek-flash", peak = false, pricing = pricing)
        // 8000/1e6*0.02 + 2000/1e6*1 + 1500/1e6*4 = 0.00016 + 0.002 + 0.006 = 0.00816 元
        // 加一次搜索 0.005 → 0.01316 元 = 13160 微元（文档写 ~0.012，量级一致）
        assertEquals(13_160L, micros)
        assertEquals("￥0.0132", Money.format(micros))
    }

    @Test
    fun `高峰单价翻倍 但工具费不变`() {
        val idle = CostCalculator.micros(typical, "deepseek-flash", peak = false, pricing = pricing)
        val peak = CostCalculator.micros(typical, "deepseek-flash", peak = true, pricing = pricing)
        val tokenIdle = idle - 5_000
        val tokenPeak = peak - 5_000
        assertEquals(tokenIdle * 2, tokenPeak)
    }

    @Test
    fun `v4-pro 是 flash 的数倍`() {
        val flash = CostCalculator.micros(typical, "deepseek-flash", peak = false, pricing = pricing)
        val pro = CostCalculator.micros(typical, "deepseek-v4-pro", peak = false, pricing = pricing)
        assertTrue("pro 应明显更贵", pro > flash * 2)
    }

    @Test
    fun `没有价格表的模型不计费也不崩`() {
        assertEquals(0L, CostCalculator.micros(typical, "some-other-model", peak = false, pricing = pricing))
    }

    @Test
    fun `百万 token 不溢出`() {
        val huge = Usage(promptTokens = 1_000_000, cacheHitTokens = 1_000_000, completionTokens = 1_000_000)
        val micros = CostCalculator.micros(huge, "deepseek-v4-pro", peak = true, pricing = pricing)
        assertEquals(1L * 9_000_000 + 1L * 300_000 + 1L * 27_000_000, micros)
    }

    @Test
    fun `峰谷判定 北京时间工作日两个时段`() {
        fun at(hour: Int, day: Int) = ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, PeakClock.ZONE)
        // 2026-11-16 是周一
        assertFalse(PeakClock.isPeak(at(8, 16)))
        assertTrue(PeakClock.isPeak(at(9, 16)))
        assertTrue(PeakClock.isPeak(at(11, 16)))
        assertFalse(PeakClock.isPeak(at(12, 16)))
        assertFalse(PeakClock.isPeak(at(13, 16)))
        assertTrue(PeakClock.isPeak(at(14, 16)))
        assertTrue(PeakClock.isPeak(at(17, 16)))
        assertFalse(PeakClock.isPeak(at(18, 16)))
        // 周六 10 点不是高峰
        assertFalse(PeakClock.isPeak(at(10, 21)))
    }

    @Test
    fun `金额格式化 至少两位 最多四位`() {
        assertEquals("￥0.34", Money.format(340_000))
        assertEquals("￥10.32", Money.format(10_320_000))
        assertEquals("￥0.0132", Money.format(13_160))
        assertEquals("￥0.0021", Money.format(2_100))
        assertEquals("￥0.00", Money.format(0))
        assertEquals("￥0.259", Money.format(259_000))
        assertEquals("1,919,810", Money.tokens(1_919_810))
    }
}
