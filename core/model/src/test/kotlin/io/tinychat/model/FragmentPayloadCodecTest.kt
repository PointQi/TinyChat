package io.tinychat.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 碎片 payload 编解码。样本全部来自真实导出（temp-doc/deepseek_data-2026-11-16.zip）。
 *
 * 两条不变量：
 * 1. 已知类型解码后字段正确；
 * 2. 解码 → 编码 → 再解码，结果稳定（幂等），且未识别内容原样保留。
 */
class FragmentPayloadCodecTest {

    private val json = Json

    @Test
    fun `REQUEST 编码后 type 在最前`() {
        val text = FragmentPayloadCodec.encode(RequestPayload(content = "帮我翻译一下这个文档"))
        assertTrue(text, text.startsWith("""{"type":"REQUEST""""))
        assertEquals(RequestPayload("帮我翻译一下这个文档"), FragmentPayloadCodec.decode(text))
    }

    @Test
    fun `RESPONSE 保留换行与 Markdown 原文`() {
        val body = "这是翻译：\n\n---\n\n# 标题\n\n- 列表"
        val payload = FragmentPayloadCodec.decode("""{"type":"RESPONSE","content":"${body.replace("\n", "\\n")}"}""")
        assertEquals(ResponsePayload(content = body), payload)
    }

    @Test
    fun `THINK 保留 elapsed_secs`() {
        val sample = """{"type":"THINK","content":"用户需要翻译一份文档。","elapsed_secs":2.175838925}"""
        val payload = FragmentPayloadCodec.decode(sample) as ThinkPayload
        assertEquals("用户需要翻译一份文档。", payload.content)
        assertEquals(2.175838925, payload.elapsedSecs!!, 1e-9)

        // 归一化后再解一次必须相同
        val again = FragmentPayloadCodec.decode(FragmentPayloadCodec.encode(payload))
        assertEquals(payload, again)
    }

    @Test
    fun `TOOL_OPEN 解码真实样本`() {
        val sample = """
            {"type":"TOOL_OPEN","status":"FINISHED","url":"http://www.yywsb.com/article/202510/2811535.html",
            "title":"感冒后一直咳咳咳？","snippet":"蜂蜜有止咳效果","site_name":"医药卫生报",
            "published_at":"2025-10-22T16:00:00Z","site_icon":"https://cdn.deepseek.com/site-icons/yywsb.com",
            "stage_id":2}
        """.trimIndent()
        val payload = FragmentPayloadCodec.decode(sample) as ToolOpenPayload
        assertEquals("FINISHED", payload.status)
        assertEquals(2, payload.stageId)
        assertEquals("医药卫生报", payload.siteName)
        assertEquals("2025-10-22T16:00:00Z", payload.publishedAt)
        assertTrue(payload.snippet!!.contains("蜂蜜"))
    }

    @Test
    fun `TOOL_SEARCH 解码真实样本`() {
        val sample = """
            {"type":"TOOL_SEARCH","queries":["上呼吸道感染 康复后期 咳嗽 缓解","site:cdc.gov cough home care"],
            "results":[{"url":"https://www.pumch.cn/detail/44634.html","title":"大寒时节 应对感染后咳嗽",
            "snippet":"协和医院科普","published_at":"2025-10-22T16:00:00Z",
            "site_icon":"https://cdn.deepseek.com/site-icons/pumch.cn","site_name":"北京协和医院"}],
            "stage_id":2,"status":"FINISHED"}
        """.trimIndent()
        val payload = FragmentPayloadCodec.decode(sample) as ToolSearchPayload
        assertEquals(2, payload.queries.size)
        assertEquals(1, payload.results.size)
        assertEquals("北京协和医院", payload.results[0].siteName)
        assertEquals(2, payload.stageId)
    }

    @Test
    fun `FILE 保留文件元数据`() {
        val sample = """{"type":"FILE","files":[{"id":"a46c73ae-5e6f-46bd-9445-b400f516e11f","file_name":"CS307-Fall26-project1.pdf","file_size":345386,"token_usage":3362,"inserted_at":"2026-10-26T12:39:00.446Z","export_path":null,"is_image":false,"width":null,"height":null}]}"""
        val payload = FragmentPayloadCodec.decode(sample) as FilePayload
        assertEquals(1, payload.files.size)
        val file = payload.files[0]
        assertEquals("CS307-Fall26-project1.pdf", file.fileName)
        assertEquals(345386L, file.fileSize)
        assertEquals(3362, file.tokenUsage)
        assertEquals(false, file.isImage)
        // 归一化后仍能解回等价对象（null 字段被省略不影响语义）
        assertEquals(payload, FragmentPayloadCodec.decode(FragmentPayloadCodec.encode(payload)))
    }

    @Test
    fun `未识别类型原样保留`() {
        val sample = """{"type":"CONTENT_RISK","content":"This response is AI-generated, for reference only.","style":"WARNING"}"""
        val payload = FragmentPayloadCodec.decode(sample) as UnknownPayload
        assertEquals("CONTENT_RISK", payload.wireType)
        assertEquals(FragmentType.UNKNOWN, FragmentType.fromWire(payload.wireType))

        val reEncoded = FragmentPayloadCodec.encode(payload)
        assertEquals(
            json.parseToJsonElement(sample) as JsonObject,
            json.parseToJsonElement(reEncoded) as JsonObject,
        )
    }

    @Test
    fun `已知类型出现未知字段时整块降级保留`() {
        val sample = """{"type":"REQUEST","content":"你好","future_field":{"a":1}}"""
        val payload = FragmentPayloadCodec.decode(sample) as UnknownPayload
        assertEquals("REQUEST", payload.wireType)
        assertEquals(
            json.parseToJsonElement(sample) as JsonObject,
            json.parseToJsonElement(FragmentPayloadCodec.encode(payload)) as JsonObject,
        )
    }

    @Test
    fun `READ_LINK 空结果与未知字段都不丢`() {
        val empty = """{"type":"READ_LINK","results":[]}"""
        val emptyPayload = FragmentPayloadCodec.decode(empty) as ReadLinkPayload
        assertEquals(0, emptyPayload.results!!.size)

        val withSignature = """{"type":"READ_LINK","results":[{"url":"https://a.com","signature":"sig-abc"}]}"""
        val payload = FragmentPayloadCodec.decode(withSignature)
        // results 是原始 JSON 数组，signature 必须一字不差地活着
        assertEquals(
            json.parseToJsonElement(withSignature) as JsonObject,
            json.parseToJsonElement(FragmentPayloadCodec.encode(payload)) as JsonObject,
        )
    }

    @Test
    fun `类型不是对象时不崩`() {
        assertEquals(UnknownPayload("", JsonObject(emptyMap())), FragmentPayloadCodec.decode("[]"))
        assertEquals(UnknownPayload("", JsonObject(emptyMap())), FragmentPayloadCodec.decode("{oops"))
    }
}
