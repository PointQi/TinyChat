package io.tinychat.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提示词预设（决策 9 / 12 / 13）。
 *
 * 这里同时把「UI 文案不使用中点分隔符」这件事钉成断言：
 * 预设名会直接显示在设置页，混进 `·` 就会出现在界面上。
 */
class PromptPresetTest {

    @Test
    fun `五个内置预设加一个自定义`() {
        assertEquals(5, PromptPreset.entries.count { it.isBuiltIn })
        assertEquals(PromptPreset.CUSTOM, PromptPreset.entries.last())
        assertFalse(PromptPreset.CUSTOM.isBuiltIn)
    }

    @Test
    fun `默认预设排第一且只读`() {
        assertEquals(PromptPreset.DEFAULT, PromptPreset.entries.first())
        assertEquals("默认", PromptPreset.DEFAULT.displayName)
    }

    @Test
    fun `显示名非空且不含中点`() {
        PromptPreset.entries.forEach { preset ->
            assertTrue("${preset.name} 的显示名为空", preset.displayName.isNotBlank())
            assertFalse(
                "${preset.name} 的显示名含中点分隔符：${preset.displayName}",
                preset.displayName.contains('·'),
            )
        }
    }

    @Test
    fun `ID 回查失败时回落到默认`() {
        assertEquals(PromptPreset.ROLEPLAY, PromptPreset.fromId("roleplay"))
        assertEquals(PromptPreset.DEFAULT, PromptPreset.fromId("不存在"))
    }
}
