package io.tinychat.net

import io.tinychat.model.KnownProviders
import io.tinychat.model.ResponsePayload
import io.tinychat.net.protocol.ChatCommand
import io.tinychat.net.protocol.DeepSeekMessagesProtocol
import io.tinychat.net.protocol.WireMessage
import io.tinychat.net.files.FilePurpose
import io.tinychat.net.files.FilesClient
import io.tinychat.net.transport.OkHttpChatTransport
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 真实接口冒烟：**整条链路只打一次**，输入输出都极短，用来证明
 * 「协议 → 传输 → SSE → 归并」在真实服务端上成立。
 *
 * 无 Key 时自动跳过（`assumeTrue`），CI 不受影响。
 * Key 位置：仓库根 `.local/dev-key.txt`（已在 .gitignore）。
 */
class LiveSmokeTest {

    @Test
    fun `一次真实往返`() = runBlocking {
        // 真实请求要花钱，默认不跑；要跑就设 TINYCHAT_LIVE_TEST=1
        assumeTrue("未开启真实接口测试（TINYCHAT_LIVE_TEST=1）", System.getenv("TINYCHAT_LIVE_TEST") == "1")
        val key = readKey()
        assumeTrue("未配置 .local/dev-key.txt，跳过真实接口测试", key != null)

        val prepared = DeepSeekMessagesProtocol.build(
            baseUrl = "https://api.deepseek.com",
            apiKey = key!!,
            command = ChatCommand(
                capabilities = KnownProviders.flash.capabilities,
                model = "deepseek-flash",
                // 不传 system、不思考、不搜索：把这次请求的 token 压到最低
                systemPrompt = null,
                thinkingEnabled = false,
                effort = null,
                searchEnabled = false,
                includeTitle = false,
                messages = listOf(WireMessage.text(WireMessage.ROLE_USER, "只回复两个字：收到")),
            ),
        )

        val assembler = MessageAssembler()
        val events = OkHttpChatTransport().stream(prepared).toList()
        assertTrue("应当收到事件", events.isNotEmpty())
        assembler.onEvents(events.asSequence())

        assertNotNull("应当有 message_start 带回的 id", assembler.messageId)
        assertTrue("应当有正文", assembler.fragments().any { it.payload is ResponsePayload })
        assertTrue("应当以 message_stop 结束", assembler.sawMessageStop)
        assertNotNull("应当拿到停止原因", assembler.stopReason)
        assertNotNull("应当拿到用量", assembler.usage)
        assertTrue("输出 token 应大于 0", assembler.usage!!.completionTokens > 0)
    }

    /**
     * Files API 冒烟：上传一个几十字节的 txt（`user_data` 不计费），验证能拿到 `file_id`。
     * 同样只在 TINYCHAT_LIVE_TEST=1 时跑。
     */
    @Test
    fun `文件上传拿到 file_id`() = runBlocking {
        assumeTrue("未开启真实接口测试（TINYCHAT_LIVE_TEST=1）", System.getenv("TINYCHAT_LIVE_TEST") == "1")
        val key = readKey()
        assumeTrue("未配置 .local/dev-key.txt，跳过真实接口测试", key != null)

        val uploaded = FilesClient().upload(
            apiKey = key!!,
            fileName = "tinychat-smoke.txt",
            mimeType = "text/plain",
            bytes = "tinychat files api smoke test".toByteArray(),
            purpose = FilePurpose.USER_DATA,
        )

        assertTrue("应当返回 file id：${uploaded.fileId}", uploaded.fileId.isNotBlank())
        assertTrue("file id 形如 file-xxx：${uploaded.fileId}", uploaded.fileId.contains("file"))
    }

    /**
     * M4 文档块端到端：上传一个 md（`user_data`）→ 用 `document` 块引用 → 模型回答里
     * 必须出现文档里那个杜撰的代号。这一条同时验证三件事：
     * `file_id` 能被 messages 接口接受、`anthropic-beta` 头有效、服务端真的解析了文档内容。
     */
    @Test
    fun `文档块端到端`() = runBlocking {
        assumeTrue("未开启真实接口测试（TINYCHAT_LIVE_TEST=1）", System.getenv("TINYCHAT_LIVE_TEST") == "1")
        val key = readKey()
        assumeTrue("未配置 .local/dev-key.txt，跳过真实接口测试", key != null)

        val codename = "BLUEHARBOR"
        val uploaded = FilesClient().upload(
            apiKey = key!!,
            fileName = "tinychat-smoke-note.md",
            mimeType = "text/markdown",
            bytes = "# Note\n\nThe project codename is $codename.\n".toByteArray(),
            purpose = FilePurpose.USER_DATA,
        )

        val prepared = DeepSeekMessagesProtocol.build(
            baseUrl = "https://api.deepseek.com",
            apiKey = key,
            command = ChatCommand(
                capabilities = KnownProviders.flash.capabilities,
                model = "deepseek-flash",
                systemPrompt = null,
                thinkingEnabled = false,
                effort = null,
                searchEnabled = false,
                includeTitle = false,
                messages = listOf(
                    DeepSeekMessagesProtocol.userBlocks(
                        DeepSeekMessagesProtocol.fileBlock(uploaded.fileId),
                        kotlinx.serialization.json.buildJsonObject {
                            put("type", kotlinx.serialization.json.JsonPrimitive("text"))
                            put("text", kotlinx.serialization.json.JsonPrimitive("文档里的项目代号是什么？只回复代号本身。"))
                        },
                    ),
                ),
            ),
        )
        assertTrue("有文件块时必须带 beta 头", prepared.headers.containsKey("anthropic-beta"))

        val assembler = MessageAssembler()
        assembler.onEvents(OkHttpChatTransport().stream(prepared).toList().asSequence())
        val text = assembler.fragments()
            .mapNotNull { it.payload as? ResponsePayload }
            .joinToString("") { it.content }

        assertTrue("回答里应当出现文档中的代号，实际：$text", text.contains(codename, ignoreCase = true))
    }

    /** 从模块目录向上找仓库根的 .local/dev-key.txt */
    private fun readKey(): String? {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        repeat(5) {
            val candidate = File(dir, ".local/dev-key.txt")
            if (candidate.isFile) {
                return candidate.readText().trim().takeIf { it.isNotEmpty() }
            }
            dir = dir?.parentFile
        }
        return null
    }
}
