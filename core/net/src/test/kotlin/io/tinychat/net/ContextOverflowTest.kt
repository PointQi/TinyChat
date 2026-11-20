package io.tinychat.net

import io.tinychat.net.transport.isContextOverflow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 400 响应体的上下文超限识别。
 *
 * 识别错了会骗用户去开新对话，所以只认几家真实措辞，其余一律当普通失败。
 */
class ContextOverflowTest {

    @Test
    fun `Anthropic 的 prompt is too long`() {
        assertTrue(
            isContextOverflow(
                """{"type":"error","error":{"type":"invalid_request_error",
                   "message":"prompt is too long: 1010000 tokens > 1000000 maximum"}}""",
            ),
        )
    }

    @Test
    fun `Anthropic 的 input length 与 max_tokens 相加超限`() {
        assertTrue(
            isContextOverflow(
                """{"type":"error","error":{"type":"invalid_request_error",
                   "message":"input length and `max_tokens` exceed context limit: 199000 + 8192 > 200000"}}""",
            ),
        )
    }

    @Test
    fun `OpenAI 的 context_length_exceeded`() {
        assertTrue(
            isContextOverflow(
                """{"error":{"message":"Maximum context length is 128000 tokens",
                   "type":"invalid_request_error","code":"context_length_exceeded"}}""",
            ),
        )
    }

    @Test
    fun `别的 400 不能被误判`() {
        assertFalse(isContextOverflow("""{"error":{"message":"invalid api key format"}}"""))
        assertFalse(isContextOverflow("""{"error":{"message":"max_tokens must be greater than 0"}}"""))
        assertFalse(isContextOverflow("HTTP 400"))
    }

    @Test
    fun `空响应体不算`() {
        assertFalse(isContextOverflow(""))
    }
}
