package io.tinychat.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageFormatTest {

    @Test
    fun `token 紧凑格式`() {
        assertEquals("0", compactTokens(0))
        assertEquals("999", compactTokens(999))
        assertEquals("1K", compactTokens(1_000))
        assertEquals("120K", compactTokens(120_000))
        assertEquals("623.4K", compactTokens(623_400))
        assertEquals("1M", compactTokens(1_000_000))
        assertEquals("1.2M", compactTokens(1_234_567))
    }
}
