package io.tinychat.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M1-2：用一手抓包固件把 SSE 解析器钉死（不需要网络与 Key）。
 */
class SseReaderTest {

    private fun parse(raw: String): List<SseEvent> =
        SseReader.read(Buffer().writeUtf8(raw)).toList()

    @Test
    fun `注释行不产生帧`() {
        assertEquals(1, parse(": keep-alive\n\ndata: {}\n\n").size)
    }

    @Test
    fun `多行 data 用换行拼接`() {
        val events = parse("event: e\ndata: a\ndata: b\n\n")
        assertEquals(1, events.size)
        assertEquals("e", events[0].name)
        assertEquals("a\nb", events[0].data)
    }

    @Test
    fun `CRLF 与 EOF 缺结尾空行都能解析`() {
        val events = parse("event: e\r\ndata: {\"a\":1}\r\n")
        assertEquals(1, events.size)
        assertEquals("{\"a\":1}", events[0].data)
    }

    @Test
    fun `无名 data 帧的 name 为 null`() {
        val events = parse("data: {\"type\":\"error\"}\n\n")
        assertNull(events[0].name)
        assertTrue(events[0].data.contains("error"))
    }

    @Test
    fun `冒号后无空格与空 data 都不丢`() {
        val events = parse("event:x\ndata:y\n\ndata:\n\n")
        assertEquals(2, events.size)
        assertEquals("y", events[0].data)
        assertEquals("", events[1].data)
    }

    @Test
    fun `搜索流样本 首帧是 message_start 末帧是 message_stop`() {
        val events = parse(SseFixtures.SEARCH_STREAM)
        assertEquals("message_start", events.first().name)
        assertTrue(events.last().data.contains("message_stop"))
        assertTrue("心跳帧应当被保留为帧", events.any { it.data.contains("\"ping\"") })
    }

    @Test
    fun `搜索流样本 每帧 data 都是 JSON 且 event 名与 type 一致`() {
        val events = parse(SseFixtures.SEARCH_STREAM)
        assertTrue(events.isNotEmpty())
        events.forEach { event ->
            val obj = Json.parseToJsonElement(event.data) as JsonObject
            val type = obj["type"]!!.jsonPrimitive.content
            // 规范：event 名与 JSON type 一致；缺名时以 type 为准
            if (event.name != null) assertEquals(type, event.name)
        }
    }

    @Test
    fun `READ_LINK 样本读到 signature`() {
        val events = parse(SseFixtures.READ_LINK_STREAM)
        val fetchResult = events.first { it.data.contains("web_fetch_tool_result") }
        assertTrue("signature 必须能读出来并回传（硬约束 3）", fetchResult.data.contains("signature"))
    }

    @Test
    fun `拒答 服务端错误 未知事件 都不崩`() {
        val refusal = parse(SseFixtures.REFUSAL_STREAM)
        assertTrue(refusal.any { it.data.contains("\"refusal\"") })

        val error = parse(SseFixtures.SERVER_ERROR_STREAM)
        assertTrue(error.any { it.data.contains("api_error") })

        val future = parse(SseFixtures.FUTURE_EVENT_STREAM)
        assertEquals(1, future.size)
        assertEquals("future_thing", future[0].name)
    }
}
