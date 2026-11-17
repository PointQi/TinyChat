package io.tinychat.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M0-8 的验收：两个 provider 的能力矩阵必须能被单测断言。
 * 这些断言直接对应 docs/01 §4.2 的一手事实，**改动即视为破坏协议假设**。
 */
class CapabilitiesTest {

    @Test
    fun `flash 支持全部扩展能力`() {
        val c = KnownProviders.flash.capabilities
        assertTrue(c.webSearch)
        assertTrue(c.readUrl)
        assertTrue(c.imageInput)
        assertTrue(c.fileUpload)
        assertTrue(c.serverSideTitle)
        assertTrue(c.safetyCheck)
        assertTrue(c.thinking)
        assertTrue(c.effort)
        assertTrue(c.supportsExtensions)
    }

    @Test
    fun `v4-pro 只有思考与强度，没有扩展能力`() {
        val c = KnownProviders.v4Pro.capabilities
        assertFalse(c.webSearch)
        assertFalse(c.readUrl)
        assertFalse(c.imageInput)
        assertFalse(c.fileUpload)
        assertFalse(c.serverSideTitle)
        assertFalse(c.safetyCheck)
        assertTrue(c.thinking)
        assertTrue(c.effort)
        assertFalse(c.supportsExtensions)
    }

    @Test
    fun `两个模型的上下文与输出上限一致`() {
        assertEquals(1_000_000, KnownProviders.flash.contextWindow)
        assertEquals(384_000, KnownProviders.flash.maxOutputTokens)
        assertEquals(1_000_000, KnownProviders.v4Pro.contextWindow)
        assertEquals(384_000, KnownProviders.v4Pro.maxOutputTokens)
    }

    @Test
    fun `官方服务商带余额与 TTS`() {
        assertTrue(KnownProviders.deepSeek.supportsBalance)
        assertTrue(KnownProviders.deepSeek.supportsTts)
        assertEquals(KnownProviders.flash, KnownProviders.deepSeek.defaultModel)
        assertEquals("https://api.deepseek.com", KnownProviders.deepSeek.baseUrl)
    }

    @Test
    fun `通用 OpenAI 兼容一律降级`() {
        val p = KnownProviders.genericOpenAiCompatible("https://example.com/v1", "some-model")
        val c = p.models.single().capabilities
        assertFalse(c.supportsExtensions)
        assertFalse(c.safetyCheck)
        assertFalse(c.serverSideTitle)
        assertFalse(c.tts)
        assertEquals(ProviderKind.OPENAI_COMPATIBLE, p.kind)
        assertNull(p.model("deepseek-flash"))
        assertEquals("some-model", p.defaultModel?.id)
    }
}
