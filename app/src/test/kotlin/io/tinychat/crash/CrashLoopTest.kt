package io.tinychat.crash

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 崩溃循环判定（M9-2）：算错就会变成「一直弹崩溃页」或「该弹的时候不弹」。 */
class CrashLoopTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun loopFile(): File = File(temp.newFolder("root"), "crash-loop.txt")

    @Test
    fun `同一签名记三次就是三次`() {
        val file = loopFile()
        val now = 10_000_000L

        repeat(3) { index -> CrashLoop.record(file, "Run@x", now + index * 1_000L) }

        assertEquals(3, CrashLoop.recent(file, "Run@x", now + 3_000L))
    }

    @Test
    fun `不同签名分别计数`() {
        val file = loopFile()
        val now = 10_000_000L

        CrashLoop.record(file, "Run@x", now)
        CrashLoop.record(file, "Run@y", now + 1_000L)

        assertEquals(1, CrashLoop.recent(file, "Run@x", now + 2_000L))
        assertEquals(1, CrashLoop.recent(file, "Run@y", now + 2_000L))
    }

    @Test
    fun `超出时间窗口的记录不再算数`() {
        val file = loopFile()
        val now = 10_000_000L

        CrashLoop.record(file, "Run@x", now)

        val later = now + CrashLoop.WINDOW_MILLIS + 1
        assertEquals(0, CrashLoop.recent(file, "Run@x", later))
    }

    @Test
    fun `写入时顺手丢掉窗口外的老记录`() {
        val file = loopFile()
        val now = 10_000_000L

        CrashLoop.record(file, "Run@old", now)
        CrashLoop.record(file, "Run@new", now + CrashLoop.WINDOW_MILLIS + 1_000L)

        val lines = file.readLines().filter { it.isNotBlank() }
        assertEquals(1, lines.size)
        assertEquals(1, CrashLoop.recent(file, "Run@new", now + CrashLoop.WINDOW_MILLIS + 1_000L))
    }

    @Test
    fun `文件不存在或内容坏掉时当作没有记录`() {
        val missing = File(temp.root, "nope.txt")
        assertEquals(0, CrashLoop.recent(missing, "Run@x", 1_000L))

        val broken = File(temp.newFolder("broken"), "crash-loop.txt")
        broken.writeText("这不是一行合法记录\n12345 \n", Charsets.UTF_8)
        assertEquals(0, CrashLoop.recent(broken, "Run@x", 1_000L))
    }

    @Test
    fun `clear 之后从零开始`() {
        val file = loopFile()
        CrashLoop.record(file, "Run@x", 1_000L)

        CrashLoop.clear(file)

        assertEquals(0, CrashLoop.recent(file, "Run@x", 1_000L))
    }
}
