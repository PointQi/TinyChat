package io.tinychat.net.search

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class AnySearchClientTest {
    @Test
    fun `sends the search key and normalizes the official response`() = runBlocking {
        lateinit var request: Request
        val client = clientReturning(
            200,
            """{
              "code": 0,
              "message": "success",
              "data": {
                "results": [
                  {"url":"https://www.example.com/a","title":"First","snippet":"Short summary","content":"Full text"},
                  {"url":"https://www.example.com/a","title":"Duplicate"},
                  {"url":"javascript:void(0)","title":"Invalid"},
                  {"title":"Missing URL"},
                  {"url":"https://second.example/b","title":"","content":"Text fallback"}
                ],
                "metadata": {"total_results":5}
              }
            }""".trimIndent(),
        ) { request = it }

        val hits = AnySearchClient(client).search("test-search-key", "今日新闻")
        val json = Buffer().apply { request.body!!.writeTo(this) }.readUtf8()
        val payload = Json.parseToJsonElement(json).jsonObject

        assertEquals("POST", request.method)
        assertEquals("https://api.anysearch.com/v1/search", request.url.toString())
        assertEquals("Bearer test-search-key", request.header("Authorization"))
        assertEquals("今日新闻", payload["query"]!!.jsonPrimitive.content)
        assertEquals(5, payload["max_results"]!!.jsonPrimitive.int)
        assertEquals(listOf("First", "second.example"), hits.map { it.title })
        assertEquals(listOf("Short summary", "Text fallback"), hits.map { it.snippet })
        assertEquals(listOf("example.com", "second.example"), hits.map { it.siteName })
    }

    @Test
    fun `HTTP quota errors omit server text that could contain credentials`() {
        val client = clientReturning(402, """{"code":-1,"message":"api_key=example-private-key"}""")
        val error = assertThrows(IOException::class.java) {
            runBlocking { AnySearchClient(client).search("test-search-key", "weather") }
        }
        assertEquals("AnySearch HTTP 402", error.message)
    }

    @Test
    fun `nonzero API status is reported without returning results or server text`() {
        val client = clientReturning(200, """{"code":-1,"message":"server details","data":{"results":[]}}""")
        val error = assertThrows(IOException::class.java) {
            runBlocking { AnySearchClient(client).search("test-search-key", "weather") }
        }
        assertEquals("AnySearch 返回错误（代码 -1）", error.message)
    }

    private fun clientReturning(code: Int, body: String, capture: (Request) -> Unit = {}): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            capture(request)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("Test response")
                .body(body.toResponseBody())
                .build()
        }.build()
}
