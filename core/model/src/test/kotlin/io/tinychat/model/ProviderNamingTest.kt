package io.tinychat.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** 服务商实例命名（决策 48）：同一家预设可以添加多次，id 与显示名各自去重。 */
class ProviderNamingTest {

    @Test
    fun `空表直接用预设 id`() {
        assertEquals("opencode-go", ProviderNaming.uniqueId("opencode-go", emptySet()))
    }

    @Test
    fun `已占用时依次加序号`() {
        val taken = setOf("opencode-go", "opencode-go-2")
        assertEquals("opencode-go-3", ProviderNaming.uniqueId("opencode-go", taken))
    }

    @Test
    fun `序号从二开始且跳过被占用的`() {
        assertEquals(
            "deepseek-2",
            ProviderNaming.uniqueId("deepseek", setOf("deepseek")),
        )
    }

    @Test
    fun `空白基准名给一个兜底 id`() {
        assertEquals("provider", ProviderNaming.uniqueId("   ", emptySet()))
    }

    @Test
    fun `显示名用空格加序号`() {
        assertEquals("OpenCode Go", ProviderNaming.uniqueName("OpenCode Go", emptySet()))
        assertEquals(
            "OpenCode Go 2",
            ProviderNaming.uniqueName("OpenCode Go", setOf("OpenCode Go")),
        )
        assertEquals(
            "OpenCode Go 3",
            ProviderNaming.uniqueName("OpenCode Go", setOf("OpenCode Go", "OpenCode Go 2")),
        )
    }
}
