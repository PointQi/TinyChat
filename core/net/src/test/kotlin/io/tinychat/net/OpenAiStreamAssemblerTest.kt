package io.tinychat.net

import io.tinychat.model.ResponsePayload
import io.tinychat.model.ThinkPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M8-1：OpenAI 流的归并（思考/正文/用量/终止原因/错误帧）。 */
class OpenAiStreamAssemblerTest {

    private fun frame(content: String? = null, reasoning: String? = null, finish: String? = null): String {
        val delta = buildList {
            content?.let { add("\"content\":\"$it\"") }
            reasoning?.let { add("\"reasoning_content\":\"$it\"") }
        }.joinToString(",")
        val tail = finish?.let { ",\"finish_reason\":\"$it\"" } ?: ",\"finish_reason\":null"
        return """{"choices":[{"index":0,"delta":{$delta}$tail}]}"""
    }

    private fun OpenAiStreamAssembler.feed(vararg frames: String) {
        frames.forEach { onFrame(null, it) }
    }

    @Test
    fun `思考与正文各成一块且顺序正确`() {
        val assembler = OpenAiStreamAssembler()
        assembler.feed(
            frame(reasoning = "先想"),
            frame(reasoning = "一下"),
            frame(content = "你"),
            frame(content = "好"),
            frame(content = "", finish = "stop"),
            "[DONE]",
        )

        val fragments = assembler.fragments()
        assertEquals(2, fragments.size)
        assertEquals(0, fragments[0].ord)
        assertEquals("先想一下", (fragments[0].payload as ThinkPayload).content)
        assertEquals(1, fragments[1].ord)
        assertEquals("你好", (fragments[1].payload as ResponsePayload).content)
        assertEquals(StopReason.END_TURN, assembler.stopReason)
        assertTrue(assembler.sawMessageStop)
        assertNull(assembler.thinkingSignature)
    }

    @Test
    fun `没有思考时正文独占 ord 0`() {
        val assembler = OpenAiStreamAssembler()
        assembler.feed(frame(content = "hi"), "[DONE]")
        val fragments = assembler.fragments()
        assertEquals(1, fragments.size)
        assertEquals(0, fragments[0].ord)
        assertEquals("hi", (fragments[0].payload as ResponsePayload).content)
    }

    @Test
    fun `用量按命中拆分`() {
        val assembler = OpenAiStreamAssembler()
        assembler.feed(
            frame(content = "hi"),
            """{"choices":[],"usage":{"prompt_tokens":100,"completion_tokens":20,"total_tokens":120,"prompt_tokens_details":{"cached_tokens":60}}}""",
            "[DONE]",
        )
        val usage = assembler.usage!!
        assertEquals(40, usage.promptTokens)
        assertEquals(60, usage.cacheHitTokens)
        assertEquals(20, usage.completionTokens)
        assertEquals(100, usage.totalInput)
        assertEquals(120, usage.contextUsed)
    }

    @Test
    fun `长度截断映射为 max_tokens`() {
        val assembler = OpenAiStreamAssembler()
        assembler.feed(frame(content = "半句"), frame(finish = "length"), "[DONE]")
        assertEquals(StopReason.MAX_TOKENS, assembler.stopReason)
        assertTrue(assembler.sawMessageStop)
    }

    @Test
    fun `内容审核映射为 refusal`() {
        val assembler = OpenAiStreamAssembler()
        assembler.feed(frame(finish = "content_filter"), "[DONE]")
        assertEquals(StopReason.REFUSAL, assembler.stopReason)
    }

    @Test
    fun `错误帧保留服务端文案`() {
        val assembler = OpenAiStreamAssembler()
        assembler.onFrame(null, """{"error":{"type":"invalid_request_error","message":"model not found"}}""")
        assertNotNull(assembler.error)
        assertEquals("model not found", assembler.error!!.message)
        assertEquals("invalid_request_error", assembler.error!!.type)
    }

    @Test
    fun `没有 done 也不算结束`() {
        val assembler = OpenAiStreamAssembler()
        assembler.feed(frame(content = "半句"))
        assertNull(assembler.stopReason)
        assertTrue(!assembler.sawMessageStop)
        assertNull(assembler.usage)
    }

    @Test
    fun `未知帧不崩`() {
        val assembler = OpenAiStreamAssembler()
        assembler.feed("not json", """{"choices":[]}""", "[DONE]")
        assertTrue(assembler.unknownFrames.isNotEmpty())
        assertNull(assembler.error)
    }

    @Test
    fun `reasoning 字段的别名也认`() {
        val assembler = OpenAiStreamAssembler()
        assembler.feed(
            """{"choices":[{"delta":{"reasoning":"想一想"}}]}""",
            frame(content = "答"),
            "[DONE]",
        )
        val think = assembler.fragments().first { it.payload is ThinkPayload }
        assertEquals("想一想", (think.payload as ThinkPayload).content)
    }
}
