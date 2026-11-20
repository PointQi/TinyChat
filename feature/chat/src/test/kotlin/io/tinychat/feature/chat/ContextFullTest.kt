package io.tinychat.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 上下文到上限的判定（`send()` 拦截追加提问靠它）。
 *
 * 阈值 95%：分子来自上一轮服务端报的真实 usage，已经含那一轮的发和收，
 * 剩下的 5%（1M 窗口下 50k）是留给"刚好卡在边界上的那条新消息"的余量。
 */
class ContextFullTest {

    @Test
    fun `空会话不算满`() {
        assertFalse(isContextFull(used = 0, window = 1_000_000))
    }

    @Test
    fun `不到 95% 不拦`() {
        assertFalse(isContextFull(used = 500_000, window = 1_000_000))
        assertFalse(isContextFull(used = 949_999, window = 1_000_000))
    }

    @Test
    fun `从 95% 起拦住新提问`() {
        assertTrue(isContextFull(used = 950_000, window = 1_000_000))
        assertTrue(isContextFull(used = 999_000, window = 1_000_000))
    }

    @Test
    fun `分母跟着模型窗口走，不是写死的 1M`() {
        assertFalse(isContextFull(used = 121_000, window = 128_000))
        assertTrue(isContextFull(used = 122_000, window = 128_000))
    }

    @Test
    fun `窗口拿不到时按 0 占比算，不会一律判满`() {
        assertEquals(0f, contextRatio(used = 10_000, window = 0))
        assertFalse(isContextFull(used = 10_000, window = 0))
    }

    @Test
    fun `占比封顶在 1`() {
        assertEquals(1f, contextRatio(used = 2_000_000, window = 1_000_000))
    }
}
