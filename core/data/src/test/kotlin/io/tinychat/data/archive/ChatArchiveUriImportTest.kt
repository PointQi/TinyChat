package io.tinychat.data.archive

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.tinychat.data.db.TinyChatDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * SAF 那条路（`ChatArchive.import(Uri)`）与 `importFile` 的差别只有「把流落成临时文件」。
 * 这里用 Robolectric 给 ContentResolver 注册一个流，把这段也钉住 —— 模拟器上的
 * DocumentsUI 看不到我们 push 进去的 zip，UI 点选这条路只能在真机上补验。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatArchiveUriImportTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var db: TinyChatDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, TinyChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `从 Uri 导入会先落临时文件再解析`() = runBlocking {
        val uri = Uri.parse("content://test/archive.zip")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(sampleZip()))

        val archive = ChatArchive(context, db)
        val report = archive.import(uri)

        assertEquals(1, report.sessions)
        assertEquals(1, report.messages)
        assertEquals(1, db.sessionDao().count())
        // 临时文件不能留在 cacheDir 里
        val leftovers = File(context.cacheDir, "import-${""}").parentFile
            ?.listFiles { f -> f.name.startsWith("import-") && f.name.endsWith(".zip") }
            .orEmpty()
        assertTrue("导入临时文件应当清理掉：${leftovers.map { it.name }}", leftovers.isEmpty())
    }

    private fun sampleZip(): ByteArray {
        val sessionId = "11111111-2222-4333-8444-555555555555"
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            entry(V3Format.MANIFEST, """{"export_format_version":3}""")
            entry(V3Format.USER, "{}")
            entry(
                V3Format.sessionEntry(sessionId),
                """
                {"id":"$sessionId","title":"t","pinned":false,"current_message_id":1,
                 "inserted_at":"2026-11-15T12:37:50.105Z","updated_at":"2026-11-15T12:41:51.575Z",
                 "messages":[{"id":1,"parent_id":null,"role":"USER","status":"FINISHED",
                   "thinking_enabled":false,"search_enabled":false,"accumulated_token_usage":0,
                   "inserted_at":"2026-11-15T12:37:50.105Z",
                   "fragments":[{"type":"REQUEST","content":"hi"}]}]}
                """.trimIndent(),
            )
            entry(
                V3Format.INDEX,
                """[{"id":"$sessionId","title":"t","pinned":false,"message_count":1,
                    "inserted_at":"2026-11-15T12:37:50.105Z","updated_at":"2026-11-15T12:41:51.575Z"}]"""
                    .trimIndent(),
            )
        }
        return out.toByteArray()
    }
}
