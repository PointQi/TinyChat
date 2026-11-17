package io.tinychat.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.tinychat.model.AttachmentState
import io.tinychat.model.Fragment
import io.tinychat.model.MessageStatus
import io.tinychat.model.Role
import io.tinychat.model.SearchResult
import io.tinychat.model.ToolSearchPayload
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 数据层 CRUD（M1-1 验收）。
 *
 * SDK 固定 34：Robolectric 的 android-all 在各 SDK 间行为有差异，
 * 而本项目 minSdk 26 用的是最普通的 SQLite 行为，不需要更高 SDK 覆盖。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TinyChatDatabaseTest {

    private lateinit var db: TinyChatDatabase
    private val now = 1_700_000_000_000L

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        db = DatabaseFactory.createInMemory(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // —— 会话 ——

    @Test
    fun `列表按置顶与活跃时间排序 软删后不再出现`() = runBlocking {
        val dao = db.sessionDao()
        dao.upsert(session("s1", updatedAt = now))
        dao.upsert(session("s2", updatedAt = now + 1))
        dao.upsert(session("s3", updatedAt = now, pinned = true))

        assertEquals(listOf("s3", "s2", "s1"), dao.all().map { it.id })

        dao.softDelete("s2", now + 2)
        assertEquals(listOf("s3", "s1"), dao.all().map { it.id })
        // 软删不删数据
        assertNotNull(dao.byId("s2"))
        assertEquals(2, dao.count())
    }

    @Test
    fun `改名与切换当前分支`() = runBlocking {
        val dao = db.sessionDao()
        dao.upsert(session("s1"))
        dao.rename("s1", "翻译 cs307 项目", now + 5)
        dao.setCurrentMessage("s1", 42L, now + 5)

        val loaded = dao.byId("s1")!!
        assertEquals("翻译 cs307 项目", loaded.title)
        assertEquals(42L, loaded.currentMessageId)
        assertEquals(now + 5, loaded.updatedAt)
    }

    // —— 消息树 ——

    @Test
    fun `重新生成产生兄弟节点 编辑重发产生新的父链`() = runBlocking {
        db.sessionDao().upsert(session("s1"))
        val messageDao = db.messageDao()

        val user = messageDao.insert(message("s1", role = Role.USER, insertedAt = now))
        val firstAnswer = messageDao.insert(
            message("s1", parentId = user, role = Role.ASSISTANT, insertedAt = now + 1),
        )
        val secondAnswer = messageDao.insert(
            message("s1", parentId = user, role = Role.ASSISTANT, insertedAt = now + 2),
        )

        // 同一 USER 下的两个 ASSISTANT 就是「重新生成」的版本
        val siblings = messageDao.children(user)
        assertEquals(listOf(firstAnswer, secondAnswer), siblings.map { it.id })

        // 编辑提问：新 USER 挂到原 USER 的父（此处为根），下游重新长
        val edited = messageDao.insert(message("s1", parentId = null, role = Role.USER, insertedAt = now + 3))
        assertNull(messageDao.byId(edited)!!.parentId)
        assertEquals(2, messageDao.children(null).size) // 原 USER + 编辑后的 USER
    }

    @Test
    fun `删除消息级联删掉碎片与附件`() = runBlocking {
        db.sessionDao().upsert(session("s1"))
        val messageId = db.messageDao().insert(message("s1"))
        db.fragmentDao().insert(
            FragmentEntity(messageId = messageId, ord = 0, type = "RESPONSE", payload = """{"type":"RESPONSE","content":"hi"}"""),
        )
        db.attachmentDao().upsert(attachment(messageId, "f1"))

        db.messageDao().delete(messageId)

        assertEquals(0, db.fragmentDao().byMessage(messageId).size)
        assertEquals(0, db.attachmentDao().byMessage(messageId).size)
    }

    @Test
    fun `删除会话级联删掉消息`() = runBlocking {
        db.sessionDao().upsert(session("s1"))
        db.messageDao().insert(message("s1"))
        db.messageDao().insert(message("s1"))

        db.sessionDao().hardDelete("s1")

        assertEquals(0, db.messageDao().countInSession("s1"))
    }

    @Test
    fun `进程被杀后残留 WIP 自愈为 INCOMPLETE`() = runBlocking {
        db.sessionDao().upsert(session("s1"))
        val dao = db.messageDao()
        val wip = dao.insert(message("s1", status = MessageStatus.WIP))
        val done = dao.insert(message("s1", status = MessageStatus.FINISHED))

        assertEquals(1, dao.wipInSession("s1").size)
        assertEquals(1, dao.recoverWipAll())

        assertEquals(MessageStatus.INCOMPLETE, dao.byId(wip)!!.status)
        assertEquals(MessageStatus.FINISHED, dao.byId(done)!!.status)
    }

    // —— 碎片 ——

    @Test
    fun `碎片 ord 唯一 且 nextOrd 连续`() = runBlocking {
        db.sessionDao().upsert(session("s1"))
        val messageId = db.messageDao().insert(message("s1"))
        val dao = db.fragmentDao()

        assertEquals(0, dao.nextOrd(messageId))
        dao.insert(FragmentEntity(messageId = messageId, ord = 0, type = "THINK", payload = """{"type":"THINK","content":"a"}"""))
        assertEquals(1, dao.nextOrd(messageId))

        val duplicate = runCatching {
            dao.insert(FragmentEntity(messageId = messageId, ord = 0, type = "THINK", payload = "{}"))
        }.exceptionOrNull()
        assertNotNull("同一 ord 重复插入必须失败", duplicate)
    }

    @Test
    fun `碎片 payload 经数据库往返不丢`() = runBlocking {
        db.sessionDao().upsert(session("s1"))
        val messageId = db.messageDao().insert(message("s1"))
        val payload = ToolSearchPayload(
            status = "FINISHED",
            stageId = 2,
            queries = listOf("咳嗽 缓解", "site:cdc.gov cough"),
            results = listOf(
                SearchResult(
                    url = "https://www.pumch.cn/detail/44634.html",
                    title = "大寒时节",
                    snippet = "协和医院",
                    publishedAt = "2025-10-22T16:00:00Z",
                    siteIcon = "https://cdn.deepseek.com/site-icons/pumch.cn",
                    siteName = "北京协和医院",
                ),
            ),
        )
        db.fragmentDao().insert(Fragment(messageId = messageId, ord = 3, payload = payload).toEntity())

        val loaded = db.fragmentDao().byMessage(messageId).single().toDomain()
        assertEquals(payload, loaded.payload)
        assertEquals(3, loaded.ord)
        assertEquals("TOOL_SEARCH", db.fragmentDao().byMessage(messageId).single().type)
    }

    @Test
    fun `流式追加走原地更新`() = runBlocking {
        db.sessionDao().upsert(session("s1"))
        val messageId = db.messageDao().insert(message("s1"))
        val dao = db.fragmentDao()
        val id = dao.insert(
            FragmentEntity(messageId = messageId, ord = 0, type = "RESPONSE", payload = """{"type":"RESPONSE","content":"你"}"""),
        )

        dao.updatePayload(id, """{"type":"RESPONSE","content":"你好世界"}""")

        val loaded = dao.byMessage(messageId).single().toDomain()
        assertEquals("你好世界", (loaded.payload as io.tinychat.model.ResponsePayload).content)
    }

    // —— 构造辅助 ——

    private fun session(
        id: String,
        updatedAt: Long = now,
        pinned: Boolean = false,
    ) = SessionEntity(
        id = id,
        createdAt = now,
        updatedAt = updatedAt,
        providerId = "deepseek",
        model = "deepseek-flash",
        pinned = pinned,
    )

    private fun message(
        sessionId: String,
        parentId: Long? = null,
        role: Role = Role.USER,
        status: MessageStatus = MessageStatus.FINISHED,
        insertedAt: Long = now,
    ) = MessageEntity(
        sessionId = sessionId,
        parentId = parentId,
        role = role,
        status = status,
        insertedAt = insertedAt,
    )

    private fun attachment(messageId: Long, id: String) = AttachmentEntity(
        id = id,
        messageId = messageId,
        fileName = "CS307-Fall26-project1.pdf",
        fileSize = 345_386,
        state = AttachmentState.LOCAL,
    )
}
