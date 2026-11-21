package io.tinychat.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 崩溃诊断信息（M9-2）。这几条一旦退化，用户手机上的报告会变成一堆读不懂或带 Key 的文本。 */
class CrashReporterTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `redact 抹掉 Key 与令牌，URL 只留主机`() {
        val raw = """
            api_key=sk-abcdefgh12345678
            Authorization: Bearer abc.def-ghi
            at io.tinychat.net.ChatClient.call(ChatClient.kt:42) https://api.deepseek.com/chat/completions?key=1
        """.trimIndent()

        val out = CrashReporter.redact(raw)

        assertFalse(out.contains("abcdefgh12345678"))
        assertFalse(out.contains("abc.def-ghi"))
        assertFalse(out.contains("/chat/completions"))
        assertTrue(out.contains("https://api.deepseek.com/…"))
        // 栈帧本身要留着，否则报告没用
        assertTrue(out.contains("io.tinychat.net.ChatClient.call"))
    }

    @Test
    fun `write 落盘、list 由新到旧`() {
        val dir = temp.newFolder("crashes")
        val first = CrashReporter.write(dir, 1_000L, "Run@a", "第一份")
        val second = CrashReporter.write(dir, 2_000L, "Run@b", "第二份")

        assertNotNull(first)
        assertNotNull(second)
        val list = CrashReporter.list(dir)
        assertEquals(2, list.size)
        assertEquals(second!!.name, list[0].name)
    }

    @Test
    fun `超过五份时只留最新的五份`() {
        val dir = temp.newFolder("crashes")
        repeat(7) { index ->
            CrashReporter.write(dir, 1_000L * (index + 1), "sig$index", "第 $index 份")
        }

        val list = CrashReporter.list(dir)
        assertEquals(CrashReporter.MAX_FILES, list.size)
        assertTrue(list.first().name.contains("sig6"))
        assertFalse(list.any { it.name.contains("sig1") })
    }

    @Test
    fun `超长报告被截断并带说明`() {
        val dir = temp.newFolder("crashes")
        val huge = "x".repeat(CrashReporter.MAX_CHARS + 5_000)

        val file = CrashReporter.write(dir, 1L, "Run@huge", huge)!!

        val text = file.readText()
        assertTrue(text.endsWith("…（超过上限，已截断）"))
        assertTrue(text.length < CrashReporter.MAX_CHARS + 100)
    }

    @Test
    fun `签名里的路径字符不会把文件写到目录外面`() {
        val root = temp.newFolder("root")
        val dir = File(root, "crashes").apply { mkdirs() }

        val file = CrashReporter.write(dir, 1L, "../../escape", "第 1 份")!!

        assertEquals(dir.canonicalPath, file.parentFile!!.canonicalPath)
        assertFalse(file.name.contains("/"))
        assertFalse(file.name.contains(".."))
    }

    @Test
    fun `clear 清掉全部报告`() {
        val dir = temp.newFolder("crashes")
        CrashReporter.write(dir, 1L, "Run@a", "第 1 份")
        CrashReporter.write(dir, 2L, "Run@b", "第 2 份")

        CrashReporter.clear(dir)

        assertTrue(CrashReporter.list(dir).isEmpty())
    }
}
