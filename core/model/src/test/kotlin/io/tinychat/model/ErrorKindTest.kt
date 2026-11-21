package io.tinychat.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 错误映射（M1-11）：11 类错误各有固定文案与重试策略。
 *
 * 铁律在 runner 里执行（收到过 content_block_start 就不再自动重试），
 * 这里只锁「能不能自动重试」与「重试几次」。
 */
class ErrorKindTest {

    @Test
    fun `每一类都有面向用户的文案`() {
        val messages = ErrorKind.entries.associateWith { it.userMessage }
        assertEquals(ErrorKind.entries.size, messages.size)
        messages.forEach { (kind, text) ->
            assertTrue("$kind 文案为空", text.isNotBlank())
            // 文案里不该出现 HTTP 码或英文异常名
            assertFalse("$kind 文案泄露技术细节：$text", text.contains("HTTP") || text.contains("http"))
        }
    }

    @Test
    fun `文案与 02 号文档一致`() {
        assertEquals("网络连接失败", ErrorKind.NETWORK.userMessage)
        assertEquals("API Key 无效，请在设置中更新", ErrorKind.HTTP_401.userMessage)
        assertEquals("账户余额不足，充值后即可继续", ErrorKind.HTTP_402.userMessage)
        assertEquals("请求异常（内部错误）", ErrorKind.HTTP_422.userMessage)
        assertEquals("操作太频繁，稍后再试", ErrorKind.HTTP_429.userMessage)
        assertEquals("服务繁忙，稍后再试", ErrorKind.HTTP_5XX.userMessage)
        assertEquals("已停止生成", ErrorKind.INTERRUPTED.userMessage)
        assertEquals("暂时无法回答，换个说法试试", ErrorKind.REFUSAL.userMessage)
        assertEquals("上传失败，点击重试", ErrorKind.UPLOAD.userMessage)
        assertEquals("附件已失效，请重新上传", ErrorKind.FILE_INVALID.userMessage)
    }

    @Test
    fun `服务繁忙点名服务商，拿不到名字时退回通用文案`() {
        assertEquals("DeepSeek 服务繁忙，稍后再试", ErrorKind.HTTP_5XX.userMessage("DeepSeek"))
        assertEquals("服务繁忙，稍后再试", ErrorKind.HTTP_5XX.userMessage("  "))
        // 其余类型不受服务商名影响
        assertEquals("网络连接失败", ErrorKind.NETWORK.userMessage("DeepSeek"))
    }

    @Test
    fun `限流只补一次，网络与服务端故障最多两次`() {
        assertEquals(1, ErrorKind.HTTP_429.maxAutoRetries)
        assertEquals(2, ErrorKind.NETWORK.maxAutoRetries)
        assertEquals(2, ErrorKind.HTTP_5XX.maxAutoRetries)
    }

    @Test
    fun `Key 错、余额、参数错、拒答一律不自动重试`() {
        listOf(
            ErrorKind.HTTP_401,
            ErrorKind.HTTP_402,
            ErrorKind.HTTP_422,
            ErrorKind.REFUSAL,
            ErrorKind.STREAM,
            ErrorKind.INTERRUPTED,
            ErrorKind.UPLOAD,
            ErrorKind.FILE_INVALID,
        ).forEach {
            assertFalse("$it 不该自动重试", it.retryable)
            assertEquals(0, it.maxAutoRetries)
        }
    }

    @Test
    fun `落库用的 wire 名与文档一致`() {
        assertEquals("http.401", ErrorKind.HTTP_401.wire)
        assertEquals("http.5xx", ErrorKind.HTTP_5XX.wire)
        assertEquals("fileInvalid", ErrorKind.FILE_INVALID.wire)
        assertEquals(ErrorKind.INTERRUPTED, ErrorKind.fromWire("interrupted"))
        assertEquals(null, ErrorKind.fromWire("nope"))
    }
}
