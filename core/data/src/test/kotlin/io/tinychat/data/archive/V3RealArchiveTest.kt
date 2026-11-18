package io.tinychat.data.archive

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStream

/**
 * 真样本（87MB）的**只读**巡检：只跑 reader，不碰数据库。
 *
 * 结论用日志断言，报告里写清楚：
 * - 条目读入顺序必须「先会话、后文件」；
 * - 文件条目数 = 79（与 01 文档的样本统计一致）；
 * - manifest 第一条、版本 3。
 *
 * 没有样本文件时自动跳过（CI 不带 87MB 样本）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V3RealArchiveTest {

    private fun sample(): File? {
        val candidates = listOf(
            File("temp-doc/deepseek_data-2026-11-16.zip"),
            File("../temp-doc/deepseek_data-2026-11-16.zip"),
            File("../../temp-doc/deepseek_data-2026-11-16.zip"),
        )
        return candidates.firstOrNull { it.isFile }
    }

    @Test
    fun `真样本条目顺序与计数`() = runBlocking {
        val zip = sample()
        assumeTrue("没有 temp-doc/deepseek_data-2026-11-16.zip，跳过真样本巡检", zip != null)

        var sessions = 0
        var files = 0
        var firstFileAfterSessions = true
        var indexSeen: Int? = null
        val fileIds = LinkedHashSet<String>()

        V3ArchiveReader().read(
            zip!!,
            object : V3ArchiveReader.Listener {
                override suspend fun onSession(session: V3SessionDto) {
                    sessions++
                }

                override suspend fun onIndex(entries: List<V3IndexEntryDto>) {
                    indexSeen = entries.size
                }

                override suspend fun onFile(
                    sessionId: String,
                    fileId: String,
                    fileName: String,
                    content: InputStream,
                ) {
                    if (sessions < 3340) firstFileAfterSessions = false
                    files++
                    fileIds += fileId
                    // 必须把流读完，否则后面的条目读不到
                    val sink = java.io.ByteArrayOutputStream()
                    content.copyTo(sink)
                    assertTrue("文件不该是空的：$fileName", sink.size() > 0)
                }
            },
        )

        println("真样本巡检：sessions=$sessions files=$files index=$indexSeen 唯一文件 id=${fileIds.size}")
        assertEquals(3340, sessions)
        assertEquals(79, files)
        assertEquals(3340, indexSeen)
        assertTrue("文件条目必须出现在全部会话之后", firstFileAfterSessions)
        assertEquals("同一附件会出现在多条会话里?", files, fileIds.size)
    }
}
