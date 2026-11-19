package io.tinychat.net.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** M8-6：OpenCode Go 额度的解析（三窗口、已用比例、触顶）。 */
class QuotaApiTest {

    @Test
    fun `解析三窗口并按固定顺序排列`() {
        val raw = """
            {
              "usage": {
                "monthly": { "status": "ok", "percent": 7,  "resetsAt": "2026-12-03T13:15:46.000Z" },
                "rolling": { "status": "ok", "percent": 3,  "resetsAt": "2026-11-07T12:42:26.000Z" },
                "weekly":  { "status": "ok", "percent": 14, "resetsAt": "2026-11-09T00:00:00.000Z" }
              }
            }
        """.trimIndent()

        val snapshot = QuotaClient.parse(raw, now = 1L)
        assertEquals(listOf("rolling", "weekly", "monthly"), snapshot.windows.map { it.key })
        assertEquals(listOf("5 小时", "本周", "本月"), snapshot.windows.map { it.label })
        assertEquals(3, snapshot.windows[0].usedPercent)
        assertEquals(14, snapshot.windows[1].usedPercent)
        assertEquals(7, snapshot.windows[2].usedPercent)
        assertTrue(snapshot.windows.none { it.exhausted })
    }

    @Test
    fun `rate-limited 视为已用满`() {
        val raw = """{"usage":{"rolling":{"status":"rate-limited","percent":12,"resetsAt":"2026-11-07T12:42:26.000Z"}}}"""
        val window = QuotaClient.parse(raw).windows.single()
        assertEquals(100, window.usedPercent)
        assertEquals(100, window.usedPercent)
        assertTrue(window.exhausted)
    }

    @Test
    fun `未知窗口忽略，缺字段的窗口丢弃`() {
        val raw = """
            {"usage":{
              "rolling":{"status":"ok","percent":40},
              "weird":{"status":"ok","percent":99},
              "weekly":{"status":"ok"}
            }}
        """.trimIndent()
        val snapshot = QuotaClient.parse(raw)
        assertEquals(listOf("rolling"), snapshot.windows.map { it.key })
    }

    @Test
    fun `一个窗口都没有时报错`() {
        assertThrows(AccountException::class.java) {
            QuotaClient.parse("""{"usage":{}}""")
        }
    }

    @Test
    fun `格式不对时报错`() {
        assertThrows(AccountException::class.java) {
            QuotaClient.parse("not json")
        }
    }
}
