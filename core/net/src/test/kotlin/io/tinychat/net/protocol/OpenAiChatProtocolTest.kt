package io.tinychat.net.protocol

import io.tinychat.model.Capabilities
import io.tinychat.model.KnownProviders
import io.tinychat.model.PromptMode
import io.tinychat.model.ProviderKind
import io.tinychat.model.ProviderMeta
import io.tinychat.model.ProviderProtocol
import io.tinychat.model.ThinkingEffort
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8-1：OpenAI Chat Completions 的请求体与头。
 *
 * 只做文本链路：没有 tools / safety_check / title，标题本地生成。
 */
class OpenAiChatProtocolTest {

    private fun command(
        model: String = "gpt-4o-mini",
        systemPrompt: String? = "你是 TinyChat 的助手。",
        systemMode: PromptMode = PromptMode.REPLACE,
        capabilities: Capabilities = Capabilities(
            webSearch = false,
            thinking = true,
            effort = true,
            serverSideTitle = false,
        ),
        thinkingEnabled: Boolean = true,
        effort: ThinkingEffort? = ThinkingEffort.HIGH,
        withReminder: Boolean = true,
    ) = ChatCommand(
        capabilities = capabilities,
        model = model,
        systemPrompt = systemPrompt,
        systemMode = systemMode,
        thinkingEnabled = thinkingEnabled,
        effort = effort,
        messages = buildList {
            if (withReminder) add(WireMessage.text(WireMessage.ROLE_REMINDER, "2026-11-17,星期一,浙江,App,中文"))
            add(WireMessage.text(WireMessage.ROLE_USER, "你好"))
            add(WireMessage.text(WireMessage.ROLE_ASSISTANT, "你好，有什么可以帮你？"))
            add(WireMessage.text(WireMessage.ROLE_USER, "写一句诗"))
        },
    )

    @Test
    fun `地址归一三种写法`() {
        assertEquals("https://x/v1/chat/completions", OpenAiChatProtocol.endpoint("https://x"))
        assertEquals("https://x/v1/chat/completions", OpenAiChatProtocol.endpoint("https://x/v1"))
        assertEquals("https://x/v1/chat/completions", OpenAiChatProtocol.endpoint("https://x/v1/"))
        assertEquals("https://x/gw/v1/chat/completions", OpenAiChatProtocol.endpoint("https://x/gw"))
    }

    @Test
    fun `请求体只含文本字段`() {
        val prepared = OpenAiChatProtocol.build(
            baseUrl = "https://gateway.example/v1",
            apiKey = "sk-test",
            command = command(),
        )
        val body = Json.parseToJsonElement(prepared.bodyJson) as JsonObject

        assertEquals("gpt-4o-mini", body["model"]!!.jsonPrimitive.content)
        assertEquals(true, body["stream"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(true, (body["stream_options"] as JsonObject)["include_usage"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("high", body["reasoning_effort"]!!.jsonPrimitive.content)
        assertNull(body["tools"])
        assertNull(body["safety_check"])
        assertNull(body["title"])
        assertEquals(ProviderProtocol.OPENAI_CHAT, prepared.protocol)
    }

    @Test
    fun `reminder 拼进 system 且不出现自定义角色`() {
        val prepared = OpenAiChatProtocol.build("https://x/v1", "k", command())
        val messages = (Json.parseToJsonElement(prepared.bodyJson) as JsonObject)["messages"]!!.jsonArray

        val roles = messages.map { it.jsonObject["role"]!!.jsonPrimitive.content }
        assertFalse("latest_reminder 不是 OpenAI 的合法角色", roles.contains("latest_reminder"))
        assertEquals(listOf("system", "user", "assistant", "user"), roles)

        val system = messages.first().jsonObject["content"]!!.jsonPrimitive.content
        assertTrue(system.startsWith("你是 TinyChat 的助手。"))
        assertTrue(system.contains("2026-11-17,星期一,浙江,App,中文"))
    }

    @Test
    fun `APPEND 不拼 DeepSeek 官方句`() {
        val prepared = OpenAiChatProtocol.build(
            "https://x/v1",
            "k",
            command(systemPrompt = "你是一只猫。", systemMode = PromptMode.APPEND, withReminder = false),
        )
        val messages = (Json.parseToJsonElement(prepared.bodyJson) as JsonObject)["messages"]!!.jsonArray
        val system = messages.first().jsonObject["content"]!!.jsonPrimitive.content
        assertEquals("你是一只猫。", system)
        assertFalse(system.contains("DeepSeek"))
    }

    @Test
    fun `CLEAR 且没有 reminder 时不发 system`() {
        val prepared = OpenAiChatProtocol.build(
            "https://x/v1",
            "k",
            command(systemMode = PromptMode.CLEAR, withReminder = false),
        )
        val messages = (Json.parseToJsonElement(prepared.bodyJson) as JsonObject)["messages"]!!.jsonArray
        assertTrue(messages.none { it.jsonObject["role"]!!.jsonPrimitive.content == "system" })
    }

    @Test
    fun `不支持强度时不发 reasoning_effort`() {
        val prepared = OpenAiChatProtocol.build(
            "https://x/v1",
            "k",
            command(capabilities = Capabilities(thinking = true, effort = false)),
        )
        val body = Json.parseToJsonElement(prepared.bodyJson) as JsonObject
        assertNull(body["reasoning_effort"])
    }

    @Test
    fun `思考关掉时不发 reasoning_effort`() {
        val prepared = OpenAiChatProtocol.build("https://x/v1", "k", command(thinkingEnabled = false))
        val body = Json.parseToJsonElement(prepared.bodyJson) as JsonObject
        assertNull(body["reasoning_effort"])
    }

    @Test
    fun `降级后不带 stream_options`() {
        val prepared = OpenAiChatProtocol.build("https://x/v1", "k", command(), includeUsage = false)
        val body = Json.parseToJsonElement(prepared.bodyJson) as JsonObject
        assertNull(body["stream_options"])
    }

    @Test
    fun `鉴权走 Bearer 并带额外头`() {
        val prepared = OpenAiChatProtocol.build(
            baseUrl = "https://x/v1",
            apiKey = "sk-test",
            command = command(),
            extraHeaders = mapOf("x-opencode-session" to "abc", "User-Agent" to "TinyChat/1.0"),
        )
        assertEquals("Bearer sk-test", prepared.headers["Authorization"])
        assertEquals("abc", prepared.headers["x-opencode-session"])
        assertEquals("TinyChat/1.0", prepared.headers["User-Agent"])
    }

    @Test
    fun `OpenCode Go 走 Zen 域并带会话头`() {
        val provider = KnownProviders.openCodeGo
        val prepared = ChatProtocols.build(
            provider = provider,
            apiKey = "go-key",
            command = command(model = "zai-org/GLM-5.3"),
            extraHeaders = ChatProtocols.extraHeaders(provider, "session-1"),
        )

        assertEquals("https://opencode.ai/zen/go/v1/chat/completions", prepared.url)
        assertEquals("Bearer go-key", prepared.headers["Authorization"])
        val marker = prepared.headers["x-opencode-session"]!!
        assertTrue(marker.length <= 64)
        assertEquals(marker, ChatProtocols.extraHeaders(provider, "session-1")["x-opencode-session"])
        assertTrue(prepared.headers["User-Agent"]!!.startsWith("TinyChat/"))
        assertEquals(ProviderKind.OPENCODE_GO, provider.kind)
    }

    @Test
    fun `不支持 stream_options 的服务商不再发`() {
        val provider = KnownProviders.openCodeGo.copy(meta = mapOf(ProviderMeta.NO_STREAM_OPTIONS to "1"))
        val prepared = ChatProtocols.build(provider, "k", command())
        val body = Json.parseToJsonElement(prepared.bodyJson) as JsonObject
        assertNull(body["stream_options"])
    }

    @Test
    fun `会话头按会话稳定且不等于明文`() {
        val a = ChatProtocols.sessionMarker("session-1")
        val b = ChatProtocols.sessionMarker("session-2")
        assertEquals(a, ChatProtocols.sessionMarker("session-1"))
        assertTrue(a != b)
        assertFalse(a.contains("session-1"))
        assertEquals(64, a.length)
    }

    @Test
    fun `Anthropic 兼容协议不带官方扩展字段`() {
        val prepared = ChatProtocols.build(
            provider = KnownProviders.genericOpenAiCompatible(
                id = "acme",
                displayName = "Acme",
                baseUrl = "https://acme.example",
                modelId = "claude-x",
                protocol = ProviderProtocol.ANTHROPIC_MESSAGES,
            ),
            apiKey = "k",
            command = command(model = "claude-x"),
        )
        assertEquals("https://acme.example/v1/messages", prepared.url)
        assertEquals("k", prepared.headers["x-api-key"])
        assertEquals("2023-06-01", prepared.headers["anthropic-version"])

        val body = Json.parseToJsonElement(prepared.bodyJson) as JsonObject
        assertNull(body["tools"])
        assertNull(body["thinking"])
        assertNull(body["title"])
        assertNull(body["safety_check"])
    }
}
