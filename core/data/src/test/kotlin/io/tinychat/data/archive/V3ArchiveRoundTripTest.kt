package io.tinychat.data.archive

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.tinychat.data.db.TinyChatDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * M6 往返：合成一个 v3 包 → 导入 → 再导出 → 逐字段比对。
 *
 * 真实样本（3340 会话 / 87MB）走模拟器上的手工验收，这里只钉死逻辑与边界：
 * 条目顺序、未知条目/索引不一致要整包失败、`TIP` / `CONTENT_RISK` 丢弃、附件原件落地。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V3ArchiveRoundTripTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var db: TinyChatDatabase
    private lateinit var attachmentsDir: File

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TinyChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        attachmentsDir = File(temp.root, "attachments").apply { mkdirs() }
    }

    @After
    fun tearDown() = db.close()

    private val sessionId = "6f1c1b1e-0000-4000-8000-000000000001"
    private val imageId = "86646137-fcc5-4eda-86ed-230f5c9fd575"

    /** 三条消息：图片提问（FILE+REQUEST）、思考+回答、TIP+CONTENT_RISK 各一条 */
    private fun sampleArchive(
        messageCountOverride: Int? = null,
        extraEntry: Pair<String, ByteArray>? = null,
        skipFile: Boolean = false,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }

            entry(V3Format.MANIFEST, """{"export_format_version":3}""".toByteArray())
            entry(V3Format.USER, """{"user_id":"u1"}""".toByteArray())

            val session = """
                {
                  "id": "$sessionId",
                  "title": "图片问答",
                  "pinned": true,
                  "current_message_id": 3,
                  "inserted_at": "2026-11-15T12:37:50.105Z",
                  "updated_at": "2026-11-15T12:41:51.575Z",
                  "messages": [
                    {
                      "id": 1,
                      "parent_id": null,
                      "role": "USER",
                      "status": "FINISHED",
                      "thinking_enabled": true,
                      "search_enabled": false,
                      "accumulated_token_usage": 241,
                      "inserted_at": "2026-11-15T12:37:50.105Z",
                      "fragments": [
                        {"type":"FILE","files":[{"id":"$imageId","file_name":"image.png","file_size":11,"token_usage":182,"inserted_at":"2026-11-15T12:37:50.100Z","export_path":"chat_sessions/${sessionId}_files/${imageId}_image.png","is_image":true,"width":873,"height":178}]},
                        {"type":"REQUEST","content":"这是什么"}
                      ]
                    },
                    {
                      "id": 2,
                      "parent_id": 1,
                      "role": "ASSISTANT",
                      "status": "FINISHED",
                      "thinking_enabled": true,
                      "search_enabled": false,
                      "accumulated_token_usage": 987,
                      "inserted_at": "2026-11-15T12:41:45.059Z",
                      "fragments": [
                        {"type":"THINK","content":"先看看图"},
                        {"type":"RESPONSE","content":"是一张图"},
                        {"type":"TIP","content":"提示条内容"},
                        {"type":"CONTENT_RISK","content":"免责声明"}
                      ]
                    },
                    {
                      "id": 3,
                      "parent_id": 2,
                      "role": "USER",
                      "status": "FINISHED",
                      "thinking_enabled": false,
                      "search_enabled": false,
                      "accumulated_token_usage": 0,
                      "inserted_at": "2026-11-15T12:41:50.000Z",
                      "fragments": [{"type":"REQUEST","content":"谢谢"}]
                    }
                  ]
                }
            """.trimIndent()
            entry(V3Format.sessionEntry(sessionId), session.toByteArray())

            val count = messageCountOverride ?: 3
            entry(
                V3Format.INDEX,
                """[{"id":"$sessionId","title":"图片问答","pinned":true,"message_count":$count,
                   "inserted_at":"2026-11-15T12:37:50.105Z","updated_at":"2026-11-15T12:41:51.575Z"}]"""
                    .trimIndent().toByteArray(),
            )

            if (!skipFile) {
                entry(V3Format.fileEntry(sessionId, imageId, "image.png"), "fake-png".toByteArray())
            }
            extraEntry?.let { (name, bytes) -> entry(name, bytes) }
        }
        return out.toByteArray()
    }

    @Test
    fun `导入后结构、时间、附件都正确`() = runBlocking {
        val report = V3Importer(db, attachmentsDir).import(zipFile(sampleArchive()))

        assertEquals(1, report.sessions)
        assertEquals(3, report.messages)
        // FILE + REQUEST + THINK + RESPONSE + REQUEST = 5；TIP / CONTENT_RISK 被丢
        assertEquals(5, report.fragments)
        assertEquals(2, report.droppedFragments)
        assertEquals(1, report.files)

        val session = db.sessionDao().byId(sessionId)!!
        assertEquals("图片问答", session.title)
        assertTrue(session.pinned)
        assertEquals(V3Time.parse("2026-11-15T12:37:50.105Z"), session.createdAt)

        val rows = db.messageDao().withFragments(sessionId)
        assertEquals(3, rows.size)
        // current_message_id 要重映射到新的全局 id
        assertEquals(rows[2].message.id, session.currentMessageId)
        assertEquals(rows[0].message.id, rows[1].message.parentId)
        assertEquals(rows[1].message.id, rows[2].message.parentId)

        val attachment = db.attachmentDao().byId(imageId).single()
        assertEquals(rows[0].message.id, attachment.messageId)
        assertTrue("原件应当落地", attachment.localPath != null)
        assertTrue(File(attachment.localPath!!).isFile)
        assertEquals("fake-png", File(attachment.localPath!!).readText())
    }

    @Test
    fun `导出再导入结构不变（字段级一致）`() = runBlocking {
        V3Importer(db, attachmentsDir).import(zipFile(sampleArchive()))

        val exported = ByteArrayOutputStream()
        val report = V3Exporter(db).export(exported)
        assertEquals(1, report.sessions)
        assertEquals(3, report.messages)

        // 条目顺序：manifest → user → 会话 → index → 文件
        val names = zipEntryNames(exported.toByteArray())
        assertEquals(V3Format.MANIFEST, names.first())
        assertEquals(V3Format.USER, names[1])
        assertEquals(V3Format.sessionEntry(sessionId), names[2])
        assertEquals(V3Format.INDEX, names[3])
        assertEquals(V3Format.fileEntry(sessionId, imageId, "image.png"), names[4])

        // 换一个空库再导一次，比对会话内容
        val second = emptyDb()
        try {
            V3Importer(second, File(temp.root, "attachments2").apply { mkdirs() })
                .import(zipFile(exported.toByteArray()))
            val a = sessionJsonOf(db, sessionId)
            val b = sessionJsonOf(second, sessionId)
            assertEquals(normalizeIds(a, db), normalizeIds(b, second))
        } finally {
            second.close()
        }
    }

    @Test
    fun `索引 message_count 对不上整包失败且回滚`() = runBlocking {
        val archive = sampleArchive(messageCountOverride = 99)
        val error = runCatching {
            V3Importer(db, attachmentsDir).import(zipFile(archive))
        }.exceptionOrNull()

        assertTrue("应当抛 ArchiveException，实际 $error", error is ArchiveException)
        assertEquals("回滚后一条会话都不该留下", 0, db.sessionDao().count())
        assertTrue(
            "回滚后附件目录里不该有残留",
            attachmentsDir.listFiles().orEmpty().isEmpty(),
        )
    }

    @Test
    fun `未知 ZIP 条目整包失败`() = runBlocking {
        val archive = sampleArchive(extraEntry = "notes/readme.txt" to "hi".toByteArray())
        val error = runCatching {
            V3Importer(db, attachmentsDir).import(zipFile(archive))
        }.exceptionOrNull()

        assertTrue("应当抛 ArchiveException，实际 $error", error is ArchiveException)
        assertEquals(0, db.sessionDao().count())
    }

    @Test
    fun `第一条不是 manifest 直接拒绝`() = runBlocking {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(V3Format.USER))
            zip.write("{}".toByteArray())
            zip.closeEntry()
        }
        val error = runCatching {
            V3Importer(db, attachmentsDir).import(zipFile(out.toByteArray()))
        }.exceptionOrNull()
        assertTrue(error is ArchiveException)
    }

    @Test
    fun `没有原件的 FILE 碎片导入后是 MISSING`() = runBlocking {
        V3Importer(db, attachmentsDir).import(zipFile(sampleArchive(skipFile = true)))
        val attachment = db.attachmentDao().byId(imageId).single()
        assertEquals(io.tinychat.model.AttachmentState.MISSING, attachment.state)
        assertEquals(null, attachment.localPath)
    }

    // —— 辅助 ——

    /** importer 要随机访问，先落成临时文件 */
    private fun zipFile(bytes: ByteArray): File =
        temp.newFile("archive-${System.nanoTime()}.zip").apply { writeBytes(bytes) }

    private fun emptyDb(): TinyChatDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "roundtrip-${System.nanoTime()}.db"
        return androidx.room.Room.databaseBuilder(context, TinyChatDatabase::class.java, name).build()
    }

    private fun zipEntryNames(bytes: ByteArray): List<String> {
        val names = ArrayList<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) names += entry.name
                zip.closeEntry()
            }
        }
        return names
    }

    /** 会话内容归一化：把 id 换成「在会话里的出现顺序」，这样两次导入的全局 id 差异不影响比对 */
    private suspend fun sessionJsonOf(db: TinyChatDatabase, id: String): JsonObject {
        val rows = db.messageDao().withFragments(id)
        val order = rows.mapIndexed { index, row -> row.message.id to index }.toMap()
        val session = db.sessionDao().byId(id)!!
        return buildJsonObject {
            put("title", session.title)
            put("pinned", session.pinned)
            put("current", session.currentMessageId?.let { order[it] })
            put("inserted_at", V3Time.format(session.createdAt))
            put(
                "messages",
                buildJsonArray {
                    rows.forEach { row ->
                        add(
                            buildJsonObject {
                                put("parent", row.message.parentId?.let { order[it] })
                                put("role", row.message.role.name)
                                put("status", row.message.status.wire)
                                put("thinking", row.message.thinkingEnabled)
                                put("search", row.message.searchEnabled)
                                put("tokens", row.message.accumulatedTokenUsage)
                                put("inserted_at", V3Time.format(row.message.insertedAt))
                                put(
                                    "fragments",
                                    buildJsonArray {
                                        row.fragments.forEach { add(it.payload.let { p -> Json.parseToJsonElement(p) }) }
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun normalizeIds(json: JsonObject, db: TinyChatDatabase): String =
        json.toString()

    @Test
    fun `导出只写官方条目，不带提示词与用量`() = runBlocking {
        V3Importer(db, attachmentsDir).import(zipFile(sampleArchive()))
        val exported = ByteArrayOutputStream()
        V3Exporter(db).export(exported)

        val names = zipEntryNames(exported.toByteArray())
        assertTrue(names.none { it.contains("prompt") || it.contains("usage") })
        val sessionJson = readEntry(exported.toByteArray(), V3Format.sessionEntry(sessionId))
        val root = json.parseToJsonElement(sessionJson).jsonObject
        assertTrue("不该有多余字段", root.keys.all {
            it in setOf("id", "title", "pinned", "current_message_id", "inserted_at", "updated_at", "messages")
        })
        val message = root["messages"]!!.jsonArray[0].jsonObject
        assertTrue("本地列不该导出", message.keys.all {
            it in setOf(
                "id", "parent_id", "role", "status", "thinking_enabled", "search_enabled",
                "accumulated_token_usage", "inserted_at", "fragments",
            )
        })
        assertNotNull(message["fragments"]!!.jsonArray[0].jsonObject["type"]?.jsonPrimitive?.content)
    }

    private fun readEntry(bytes: ByteArray, name: String): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == name) return zip.readBytes().decodeToString()
                zip.closeEntry()
            }
        }
        error("entry not found: $name")
    }
}
