package io.tinychat.net.protocol

import io.tinychat.model.KnownProviders
import io.tinychat.model.PromptMode
import io.tinychat.model.ThinkingEffort
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M1-3：请求体 golden JSON 快照。
 *
 * 期望值全部照抄 03 §6.1 的原始报文；比对用 JsonObject 相等（不依赖键顺序与空白）。
 */
class DeepSeekMessagesProtocolTest {

    private val baseUrl = "https://api.deepseek.com"

    private fun build(
        capabilities: io.tinychat.model.Capabilities = KnownProviders.flash.capabilities,
        model: String = "deepseek-flash",
        systemPrompt: String? = DeepSeekMessagesProtocol.OFFICIAL_DEFAULT_SYSTEM,
        systemMode: PromptMode = PromptMode.REPLACE,
        thinkingEnabled: Boolean = true,
        effort: ThinkingEffort? = ThinkingEffort.HIGH,
        searchEnabled: Boolean = false,
        includeTitle: Boolean = false,
        hasFileBlocks: Boolean = false,
    ): PreparedRequest = DeepSeekMessagesProtocol.build(
        baseUrl = baseUrl,
        apiKey = "sk-test",
        command = ChatCommand(
            capabilities = capabilities,
            model = model,
            systemPrompt = systemPrompt,
            systemMode = systemMode,
            thinkingEnabled = thinkingEnabled,
            effort = effort,
            searchEnabled = searchEnabled,
            includeTitle = includeTitle,
            hasFileBlocks = hasFileBlocks,
            messages = listOf(
                WireMessage.text(WireMessage.ROLE_REMINDER, "2026-11-17,星期一,浙江,App,中文"),
                WireMessage.text(WireMessage.ROLE_USER, "DeepSeek V4.1 有哪些更新？"),
            ),
        ),
    )

    private fun JsonObject.assertSameAs(expectedJson: String) {
        assertEquals(Json.parseToJsonElement(expectedJson), this)
    }

    @Test
    fun `flash 全开 与原始报文逐字段一致`() {
        val prepared = build(searchEnabled = true, includeTitle = true)

        assertEquals("$baseUrl/anthropic/v1/messages", prepared.url)
        assertEquals("sk-test", prepared.headers["x-api-key"])
        assertEquals("application/json", prepared.headers["Content-Type"])
        assertFalse(prepared.headers.containsKey("anthropic-beta"))

        val expected = """
            {
              "model": "deepseek-flash",
              "system": "该助手为DeepSeek，由深度求索公司创造。",
              "thinking": { "type": "enabled" },
              "output_config": { "effort": "high" },
              "tools": [
                { "type": "deepseek_read_url",   "name": "deepseek_read_url" },
                { "type": "deepseek_web_search", "name": "deepseek_web_search" }
              ],
              "safety_check": { "level": "high" },
              "title": { "enabled": true },
              "messages": [
                { "role": "latest_reminder", "content": "2026-11-17,星期一,浙江,App,中文" },
                { "role": "user", "content": "DeepSeek V4.1 有哪些更新？" }
              ],
              "stream": true
            }
        """.trimIndent()

        assertEquals(
            Json.parseToJsonElement(expected) as JsonObject,
            Json.parseToJsonElement(prepared.bodyJson) as JsonObject,
        )
    }

    @Test
    fun `搜索关掉时只声明 read_url`() {
        val body = Json.parseToJsonElement(build(searchEnabled = false).bodyJson) as JsonObject
        val tools = body["tools"]!!.toString()
        assertTrue(tools.contains("deepseek_read_url"))
        assertFalse(tools.contains("deepseek_web_search"))
    }

    @Test
    fun `v4-pro 不带任何扩展字段`() {
        val prepared = build(
            capabilities = KnownProviders.v4Pro.capabilities,
            model = "deepseek-v4-pro",
            searchEnabled = true,
            includeTitle = true,
        )
        val body = Json.parseToJsonElement(prepared.bodyJson) as JsonObject

        assertNull("v4-pro 不支持工具", body["tools"])
        assertNull("v4-pro 不支持 safety_check", body["safety_check"])
        assertNull("v4-pro 不支持 title", body["title"])
        assertEquals("high", (body["output_config"] as JsonObject)["effort"]!!.jsonPrimitive.content)
    }

    @Test
    fun `system CLEAR 不发送 system 字段`() {
        val body = Json.parseToJsonElement(build(systemMode = PromptMode.CLEAR).bodyJson) as JsonObject
        assertNull(body["system"])
    }

    @Test
    fun `system APPEND 拼在官方默认句之后`() {
        val prepared = build(systemPrompt = "你是一只猫。", systemMode = PromptMode.APPEND)
        val body = Json.parseToJsonElement(prepared.bodyJson) as JsonObject
        assertEquals(
            "该助手为DeepSeek，由深度求索公司创造。\n\n你是一只猫。",
            (body["system"] as JsonPrimitive).content,
        )
    }

    @Test
    fun `关掉思考时不发 effort 只发 disabled`() {
        val body = Json.parseToJsonElement(
            build(thinkingEnabled = false, effort = ThinkingEffort.MAX).bodyJson,
        ) as JsonObject
        assertEquals("""{"type":"disabled"}""", body["thinking"].toString())
        assertNull("thinking 关掉后 effort 无意义", body["output_config"])
    }

    @Test
    fun `带文件块时补 anthropic-beta 头`() {
        val prepared = build(hasFileBlocks = true)
        assertEquals("files-api-2025-04-14", prepared.headers["anthropic-beta"])
    }

    @Test
    fun `baseUrl 尾部斜杠不影响拼接`() {
        val prepared = DeepSeekMessagesProtocol.build(
            baseUrl = "https://api.deepseek.com/",
            apiKey = "k",
            command = ChatCommand(
                capabilities = KnownProviders.flash.capabilities,
                model = "deepseek-flash",
                messages = listOf(WireMessage.text(WireMessage.ROLE_USER, "hi")),
            ),
        )
        assertEquals("$baseUrl/anthropic/v1/messages", prepared.url)
    }

    @Test
    fun `messages 为空直接拒绝`() {
        val error = runCatching {
            DeepSeekMessagesProtocol.build(
                baseUrl = baseUrl,
                apiKey = "k",
                command = ChatCommand(
                    capabilities = KnownProviders.flash.capabilities,
                    model = "deepseek-flash",
                    messages = emptyList(),
                ),
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `文件块形状`() {
        val file = DeepSeekMessagesProtocol.fileBlock("file-123")
        assertEquals(
            Json.parseToJsonElement(
                """{"type":"document","source":{"type":"file","file_id":"file-123"}}""",
            ),
            file,
        )
        val image = DeepSeekMessagesProtocol.imageBlock("file-456")
        assertEquals(
            Json.parseToJsonElement(
                """{"type":"image","source":{"type":"file","file_id":"file-456"}}""",
            ),
            image,
        )
    }
}
