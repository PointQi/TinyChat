package io.tinychat.model.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 四种状态（决策 45）。 */
class PromptComposerTest {

    private val official = PromptSeeds.OFFICIAL_SYSTEM

    @Test
    fun `开关开、内容为空时只发官方那句`() {
        assertEquals(official, PromptComposer.compose(includeOfficial = true, custom = ""))
    }

    @Test
    fun `开关开、有内容时官方在前、空行分隔`() {
        assertEquals(
            "$official\n\n你是猫娘",
            PromptComposer.compose(includeOfficial = true, custom = "你是猫娘"),
        )
    }

    @Test
    fun `开关关、有内容时只发自己的`() {
        assertEquals("你是猫娘", PromptComposer.compose(includeOfficial = false, custom = "你是猫娘"))
    }

    @Test
    fun `开关关、内容为空时什么都不发`() {
        assertNull(PromptComposer.compose(includeOfficial = false, custom = ""))
    }

    @Test
    fun `只有空白的内容按空处理`() {
        assertEquals(official, PromptComposer.compose(includeOfficial = true, custom = "  \n\t "))
        assertNull(PromptComposer.compose(includeOfficial = false, custom = "  \n\t "))
    }

    @Test
    fun `内容首尾的空白会被去掉`() {
        assertEquals("只有这句", PromptComposer.compose(includeOfficial = false, custom = "\n  只有这句  \n"))
    }
}
