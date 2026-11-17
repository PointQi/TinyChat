package io.tinychat.net

import io.tinychat.model.FragmentType
import io.tinychat.model.ReadLinkPayload
import io.tinychat.model.ResponsePayload
import io.tinychat.model.ThinkPayload
import io.tinychat.model.ToolOpenPayload
import io.tinychat.model.ToolSearchPayload
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M1-4：事件 → 碎片归并。
 *
 * 覆盖 6 种块（thinking / text / server_tool_use / web_search_tool_result /
 * web_fetch_tool_result / 未知块）与乱序、未配对、重复到达等边界。
 */
class MessageAssemblerTest {

    private fun assemble(raw: String, now: Long = 1_000): MessageAssembler {
        val assembler = MessageAssembler(clock = { now })
        assembler.onEvents(SseReader.read(Buffer().writeUtf8(raw)))
        assembler.finishUnpairedTools()
        return assembler
    }

    private fun frame(type: String, body: String): String =
        "event: $type\ndata: $body\n\n"

    @Test
    fun `搜索流 归并出 5 个碎片且顺序与流一致`() {
        val assembler = assemble(SseFixtures.SEARCH_STREAM)
        val fragments = assembler.fragments()

        assertEquals(
            listOf(
                FragmentType.THINK,
                FragmentType.TOOL_SEARCH,
                FragmentType.THINK,
                FragmentType.RESPONSE,
                FragmentType.RESPONSE,
            ),
            fragments.map { it.type },
        )
        assertEquals(listOf(0, 1, 2, 3, 4), fragments.map { it.ord })

        // 思考全文跨块拼接
        assertEquals("先确认最新版本。", (fragments[0].payload as ThinkPayload).content)
        assertEquals("整理成三点。", (fragments[2].payload as ThinkPayload).content)

        // 正文被引用切成两块，各自保留；引用之前的正文都留在同一块里
        assertEquals(
            "DeepSeek V4.1 主要更新：\n1. 上下文 1M",
            (fragments[3].payload as ResponsePayload).content,
        )
        assertEquals(1, (fragments[3].payload as ResponsePayload).citations?.size)
        assertEquals("\n2. 输出 384K", (fragments[4].payload as ResponsePayload).content)
    }

    @Test
    fun `搜索结果由调用块的 queries 与结果块的 results 合成`() {
        val search = assemble(SseFixtures.SEARCH_STREAM).fragments()
            .first { it.type == FragmentType.TOOL_SEARCH }
            .payload as ToolSearchPayload

        assertEquals(MessageAssembler.STATUS_FINISHED, search.status)
        assertEquals(listOf("DeepSeek V4.1 new features", "DeepSeek V4.1 价格"), search.queries)
        assertEquals(2, search.results.size)
        assertEquals("https://a.example.com", search.results[0].url)
        assertEquals("DeepSeek", search.results[0].siteName)
        // page_age 映射到 publishedAt
        assertEquals("2026-10-16T16:00:00+00:00", search.results[0].publishedAt)
    }

    @Test
    fun `用量以 message_delta 为准`() {
        val assembler = assemble(SseFixtures.SEARCH_STREAM)
        val usage = assembler.usage!!
        assertEquals(9078, usage.promptTokens)
        assertEquals(4096, usage.cacheHitTokens)
        assertEquals(1686, usage.completionTokens)
        assertEquals(1, usage.searchCalls)
        // input_tokens 不含缓存命中部分
        assertEquals(13174, usage.totalInput)
    }

    @Test
    fun `停止原因 标题 消息 id 都拿到`() {
        val assembler = assemble(SseFixtures.SEARCH_STREAM)
        assertEquals(StopReason.END_TURN, assembler.stopReason)
        assertEquals("DeepSeek V4.1 更新", assembler.title)
        assertEquals("b161a9be-0000-4000-8000-000000000001", assembler.messageId)
        assertEquals("deepseek-flash", assembler.model)
        assertTrue(assembler.sawMessageStop)
        assertTrue(assembler.usedServerTools())
    }

    @Test
    fun `READ_LINK 保留 signature`() {
        val payload = assemble(SseFixtures.READ_LINK_STREAM).fragments()
            .single { it.type == FragmentType.READ_LINK }
            .payload as ReadLinkPayload

        val result = payload.results!!.single().toString()
        assertTrue(result, result.contains("signature"))
        assertTrue(result, result.contains("q3J0b1RmZ1c4cE1x"))
        assertTrue(result, result.contains("https://example.com/a"))
    }

    @Test
    fun `抓取失败时错误码必须保留在碎片里`() {
        val fragments = assemble(SseFixtures.FETCH_ERROR_STREAM).fragments()
        assertEquals(1, fragments.size)
        // 无调用块先到，按孤儿结果在流结束时补出，类型仍按 kind=read_link 判定
        assertEquals(FragmentType.READ_LINK, fragments[0].type)
        val raw = (fragments[0].payload as ReadLinkPayload).results!!.single().toString()
        assertTrue(raw, raw.contains("url_not_accessible"))
    }

    @Test
    fun `拒答语义被识别`() {
        val assembler = assemble(SseFixtures.REFUSAL_STREAM)
        assertEquals(StopReason.REFUSAL, assembler.stopReason)
        assertEquals("refusal", assembler.stopDetailsType)
    }

    @Test
    fun `服务端错误帧被记录且带用量`() {
        val assembler = assemble(SseFixtures.SERVER_ERROR_STREAM)
        val error = assembler.error
        assertNotNull(error)
        assertEquals("api_error", error!!.type)
        assertTrue(error.message!!.contains("failed to generate"))
        assertEquals(10, assembler.usage!!.promptTokens)
    }

    @Test
    fun `未知事件只登记不崩`() {
        val assembler = assemble(SseFixtures.FUTURE_EVENT_STREAM)
        assertTrue(assembler.unknownEvents.any { it.contains("future_thing") })
        assertEquals(0, assembler.fragments().size)
    }

    @Test
    fun `结果乱序到达时按 tool_use_id 正确配对`() {
        val raw = buildString {
            append(frame("content_block_start", """{"type":"content_block_start","index":0,"content_block":{"type":"server_tool_use","id":"t1","name":"deepseek_web_search","input":{"action":"search","queries":["qA"]}}}"""))
            append(frame("content_block_start", """{"type":"content_block_start","index":1,"content_block":{"type":"server_tool_use","id":"t2","name":"deepseek_web_search","input":{"action":"search","queries":["qB"]}}}"""))
            // 先到的是 t2 的结果
            append(frame("content_block_start", """{"type":"content_block_start","index":2,"content_block":{"type":"web_search_tool_result","tool_use_id":"t2","content":[{"type":"web_search_result","url":"https://b.example","title":"B","snippet":"sb"}]}}"""))
            append(frame("content_block_start", """{"type":"content_block_start","index":3,"content_block":{"type":"web_search_tool_result","tool_use_id":"t1","content":[{"type":"web_search_result","url":"https://a.example","title":"A","snippet":"sa"}]}}"""))
        }

        val tools = assemble(raw).fragments().map { it.payload as ToolSearchPayload }
        assertEquals(2, tools.size)
        assertEquals(listOf("qA"), tools[0].queries)
        assertEquals("https://a.example", tools[0].results[0].url)
        assertEquals(listOf("qB"), tools[1].queries)
        assertEquals("https://b.example", tools[1].results[0].url)
    }

    @Test
    fun `结果先于调用到达不会产生重复碎片`() {
        val raw = buildString {
            append(frame("content_block_start", """{"type":"content_block_start","index":0,"content_block":{"type":"web_search_tool_result","tool_use_id":"t1","content":[{"type":"web_search_result","url":"https://a.example","title":"A"}]}}"""))
            append(frame("content_block_start", """{"type":"content_block_start","index":1,"content_block":{"type":"server_tool_use","id":"t1","name":"deepseek_web_search","input":{"action":"search","queries":["qA"]}}}"""))
        }

        val fragments = assemble(raw).fragments()
        assertEquals(1, fragments.size)
        val payload = fragments[0].payload as ToolSearchPayload
        assertEquals(listOf("qA"), payload.queries)
        assertEquals(1, payload.results.size)
    }

    @Test
    fun `用户中途停止 未配对的调用落成 WIP`() {
        val raw = frame(
            "content_block_start",
            """{"type":"content_block_start","index":0,"content_block":{"type":"server_tool_use","id":"t9","name":"deepseek_read_url","input":{"action":"open_page","url":"https://x.example"}}}""",
        )
        val fragments = assemble(raw).fragments()
        assertEquals(1, fragments.size)
        val payload = fragments[0].payload as ToolOpenPayload
        assertEquals(MessageAssembler.STATUS_WIP, payload.status)
        assertEquals("https://x.example", payload.url)
    }
}
