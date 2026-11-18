package io.tinychat.net.context

import io.tinychat.model.Fragment
import io.tinychat.model.Message
import io.tinychat.model.MessageStatus
import io.tinychat.model.RequestPayload
import io.tinychat.model.ReadLinkPayload
import io.tinychat.model.ResponsePayload
import io.tinychat.model.Role
import io.tinychat.model.TemplateResponsePayload
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M1-10：上下文重建的六条规则。全部离线，不联网。
 */
class ContextRebuilderTest {

    private val reminder = "2026-11-17,星期一,,App,中文"

    private fun user(id: Long, text: String, snapshot: String? = null) = ContextTurn(
        message = Message(
            id = id,
            sessionId = "s",
            role = Role.USER,
            status = MessageStatus.FINISHED,
            insertedAt = id,
            reminderSnapshot = snapshot,
        ),
        fragments = listOf(Fragment(messageId = id, ord = 0, payload = RequestPayload(text))),
    )

    private fun assistant(
        id: Long,
        text: String,
        status: MessageStatus = MessageStatus.FINISHED,
        extra: List<Fragment> = emptyList(),
    ) = ContextTurn(
        message = Message(
            id = id,
            sessionId = "s",
            parentId = id - 1,
            role = Role.ASSISTANT,
            status = status,
            insertedAt = id,
        ),
        fragments = listOf(Fragment(messageId = id, ord = 0, payload = ResponsePayload(text))) + extra,
    )

    @Test
    fun `read_link 的调用块与结果块成对回传，签名保留`() {
        val call = buildJsonObject {
            put("type", JsonPrimitive("server_tool_use"))
            put("id", JsonPrimitive("srvtoolu_rl_01"))
            put("name", JsonPrimitive("deepseek_web_fetch"))
            put("kind", JsonPrimitive("read_link"))
            put("input", buildJsonObject { put("action", JsonPrimitive("read_link")) })
        }
        val link = Fragment(
            messageId = 2,
            ord = 1,
            payload = ReadLinkPayload(
                results = JsonArray(
                    listOf(
                        buildJsonObject {
                            put("type", JsonPrimitive("web_fetch_result"))
                            put("url", JsonPrimitive("https://example.com/a"))
                            put("signature", JsonPrimitive("q3J0b1RmZ1c4cE1x"))
                        },
                    ),
                ),
                call = call,
            ),
        )

        val out = ContextRebuilder.build(
            listOf(user(1, "读一下这个链接"), assistant(2, "读完了", extra = listOf(link))),
            currentReminder = null,
        )

        val assistant = out.last()
        val blocks = (assistant.content as JsonArray).map { it as JsonObject }
        assertEquals(3, blocks.size)
        assertEquals("text", (blocks[0]["type"] as JsonPrimitive).content)
        // 调用块原样回传
        assertEquals("srvtoolu_rl_01", (blocks[1]["id"] as JsonPrimitive).content)
        // 结果块补回 tool_use_id 与签名，且不再把 signature 混在 content 里
        assertEquals("web_fetch_tool_result", (blocks[2]["type"] as JsonPrimitive).content)
        assertEquals("srvtoolu_rl_01", (blocks[2]["tool_use_id"] as JsonPrimitive).content)
        assertEquals("q3J0b1RmZ1c4cE1x", (blocks[2]["signature"] as JsonPrimitive).content)
        assertNull((blocks[2]["content"] as JsonObject)["signature"])
    }

    @Test
    fun `没有 read_link 的轮次仍是纯文本消息`() {
        val out = ContextRebuilder.build(listOf(user(1, "你好"), assistant(2, "你也好")), null)

        assertTrue(out.last().content is JsonPrimitive)
    }

    private fun roles(messages: List<io.tinychat.net.protocol.WireMessage>) = messages.map { it.role }

    @Test
    fun `成对轮次按顺序还原`() {
        val out = ContextRebuilder.build(
            listOf(
                user(1, "第一次"),
                assistant(2, "答一"),
                // 第二轮已经带着当天的 reminder，不应重复插
                user(3, "第二次", snapshot = reminder),
                assistant(4, "答二"),
            ),
            currentReminder = reminder,
        )

        assertEquals(
            listOf("latest_reminder", "user", "assistant", "user", "assistant"),
            roles(out),
        )
    }

    @Test
    fun `孤立 USER 整轮跳过`() {
        val out = ContextRebuilder.build(
            listOf(user(1, "有回复"), assistant(2, "好"), user(3, "没等到回复")),
            currentReminder = reminder,
        )
        // 末尾的 USER 由调用方另外追加，这里不应出现
        assertEquals(listOf("latest_reminder", "user", "assistant"), roles(out))
    }

    @Test
    fun `失败态 ASSISTANT 整轮跳过`() {
        val out = ContextRebuilder.build(
            listOf(user(1, "问题"), assistant(2, "——", status = MessageStatus.FAILED)),
            currentReminder = reminder,
        )
        assertEquals(emptyList<String>(), roles(out))
    }

    @Test
    fun `服务端模板块不入上下文`() {
        val out = ContextRebuilder.build(
            listOf(
                user(1, "问题"),
                assistant(
                    2,
                    "你好，这个问题我暂时无法回答",
                    extra = listOf(
                        Fragment(messageId = 2, ord = 1, payload = TemplateResponsePayload("模板话术")),
                    ),
                ),
            ),
            currentReminder = reminder,
        )
        assertEquals(emptyList<String>(), roles(out))
    }

    @Test
    fun `空正文不入上下文`() {
        val out = ContextRebuilder.build(
            listOf(user(1, "问题"), assistant(2, "   ")),
            currentReminder = reminder,
        )
        assertEquals(emptyList<String>(), roles(out))
    }

    @Test
    fun `reminder 只在快照不同时插入`() {
        val out = ContextRebuilder.build(
            listOf(
                user(1, "第一轮", snapshot = null),          // 从未发过 → 插
                assistant(2, "答一"),
                user(3, "第二轮", snapshot = reminder),      // 与当前相同 → 不插
                assistant(4, "答二"),
                user(5, "第三轮", snapshot = "2026-11-16,星期日,,App,中文"), // 变了 → 插
                assistant(6, "答三"),
            ),
            currentReminder = reminder,
        )

        assertEquals(
            listOf(
                "latest_reminder", "user", "assistant",
                "user", "assistant",
                "latest_reminder", "user", "assistant",
            ),
            roles(out),
        )
    }

    @Test
    fun `reminder 关闭时一条都不插`() {
        val out = ContextRebuilder.build(
            listOf(user(1, "问题"), assistant(2, "答案")),
            currentReminder = null,
        )
        assertEquals(listOf("user", "assistant"), roles(out))
    }

    @Test
    fun `INCOMPLETE 也能进上下文`() {
        val out = ContextRebuilder.build(
            listOf(user(1, "问题"), assistant(2, "说到一半", status = MessageStatus.INCOMPLETE)),
            currentReminder = reminder,
        )
        assertEquals(listOf("latest_reminder", "user", "assistant"), roles(out))
    }
}
