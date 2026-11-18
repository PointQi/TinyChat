package io.tinychat.net.files

import io.tinychat.model.ErrorKind
import io.tinychat.net.transport.ChatException
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M1-12：Files API 的请求形状与响应解析。全部离线，不联网。
 */
class FilesApiTest {

    private fun bodyText(prepared: PreparedUpload): String {
        val buffer = Buffer()
        prepared.body.writeTo(buffer)
        return buffer.readUtf8()
    }

    @Test
    fun `purpose 必须排在 file 之前`() {
        val prepared = FilesRequests.build(
            baseUrl = "https://api.deepseek.com",
            apiKey = "sk-test",
            fileName = "spec.md",
            mimeType = "text/markdown",
            bytes = "# 标题".toByteArray(),
            purpose = FilePurpose.USER_DATA,
        )

        val body = bodyText(prepared)
        val purposeAt = body.indexOf("name=\"purpose\"")
        val fileAt = body.indexOf("name=\"file\"")
        assertTrue("两个字段都要在: $body", purposeAt >= 0 && fileAt >= 0)
        assertTrue("purpose 必须在 file 之前", purposeAt < fileAt)
        assertTrue(body.contains("user_data"))
        assertTrue(body.contains("filename=\"spec.md\""))
    }

    @Test
    fun `上传地址与必带请求头`() {
        val prepared = FilesRequests.build(
            baseUrl = "https://api.deepseek.com",
            apiKey = "sk-test",
            fileName = "a.txt",
            mimeType = "text/plain",
            bytes = byteArrayOf(),
            purpose = FilePurpose.TEXT_RECOGNITION,
        )

        assertEquals("https://api.deepseek.com/anthropic/v1/files", prepared.url)
        assertEquals("sk-test", prepared.headers["x-api-key"])
        assertEquals("files-api-2025-04-14", prepared.headers["anthropic-beta"])
        assertTrue(bodyText(prepared).contains("text_recognition"))
    }

    @Test
    fun `解析出 file_id`() {
        assertEquals(
            "file-api-abc123",
            FilesRequests.parseFileId("""{"id":"file-api-abc123","type":"file"}"""),
        )
        // 兼容旧写法的 file_id
        assertEquals("file-xyz", FilesRequests.parseFileId("""{"file_id":"file-xyz"}"""))
    }

    @Test
    fun `响应里没有 file_id 就按上传失败处理`() {
        val error = runCatching { FilesRequests.parseFileId("""{"type":"file"}""") }.exceptionOrNull()
        assertTrue(error is ChatException)
        assertEquals(ErrorKind.UPLOAD, (error as ChatException).kind)
    }

    @Test
    fun `非 JSON 响应按上传失败处理`() {
        val error = runCatching { FilesRequests.parseFileId("<html>502</html>") }.exceptionOrNull()
        assertEquals(ErrorKind.UPLOAD, (error as ChatException).kind)
    }
}
