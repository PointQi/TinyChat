package io.tinychat.data.chat

import androidx.test.core.app.ApplicationProvider
import io.tinychat.data.attachment.AttachmentStore
import io.tinychat.data.attachment.AttachmentUploader
import io.tinychat.data.db.DatabaseFactory
import io.tinychat.data.db.MessageEntity
import io.tinychat.data.db.TinyChatDatabase
import io.tinychat.data.provider.ProviderStore
import io.tinychat.model.KnownProviders
import io.tinychat.model.MessageStatus
import io.tinychat.model.Role
import io.tinychat.model.ThinkingEffort
import io.tinychat.net.SseEvent
import io.tinychat.net.protocol.PreparedRequest
import io.tinychat.net.transport.ChatTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 账单归属（P 阶段，决策 47）。
 *
 * 会话允许中途换服务商，所以：① 每条消息记下这一轮的服务商；② 费用在落库时定格；
 * ③ 老数据（没有定格费用）仍按旧口径补算，升级前后金额不跳。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatRepositoryCostTest {

    private lateinit var db: TinyChatDatabase
    private lateinit var providerStore: ProviderStore
    private lateinit var attachmentStore: AttachmentStore

    /** 第三方链路：回一段最简的 OpenAI 流，证明「第三方不写费用」 */
    private class PlainTransport : ChatTransport {
        override fun stream(request: PreparedRequest): Flow<SseEvent> = flow {
            emit(SseEvent(null, """{"choices":[{"index":0,"delta":{"content":"ok"},"finish_reason":null}]}"""))
            emit(SseEvent(null, """{"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}"""))
            emit(SseEvent(null, "[DONE]"))
        }
    }

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = DatabaseFactory.createInMemory(context)
        attachmentStore = AttachmentStore(context)
        providerStore = ProviderStore.create(db)
        providerStore.ensureSeeded()
        providerStore.save(
            KnownProviders.genericOpenAiCompatible(
                id = "acme",
                displayName = "Acme",
                baseUrl = "https://acme.example/v1",
                modelId = "m1",
            ),
        )
    }

    @After
    fun tearDown() = db.close()

    private fun repository(transport: ChatTransport = PlainTransport()) = ChatRepository(
        db = db,
        transport = transport,
        clock = { 1_700_000_000_000L },
        isOnline = { true },
        attachmentStore = attachmentStore,
        uploader = AttachmentUploader(),
        providerStore = providerStore,
        apiKeyFor = { "k" },
    )

    @Test
    fun `第三方作答的消息不写费用`() = runBlocking {
        val repository = repository()
        val sessionId = repository.createSession(providerId = "acme", model = "m1")
        repository.send(sessionId, "hi")

        val assistant = db.messageDao().allInSession(sessionId).single { it.role == Role.ASSISTANT }
        assertEquals("acme", assistant.providerId)
        assertNull("第三方没有单价表，不写 0 冒充免费", assistant.costMicros)
        assertEquals(0L, repository.usageSummary(sessionId).costMicros)
    }

    @Test
    fun `定格的费用不随会话换服务商而变化`() = runBlocking {
        val repository = repository()
        val sessionId = repository.createSession(providerId = "deepseek", model = "deepseek-flash")

        val stamped = db.messageDao().insert(
            MessageEntity(
                sessionId = sessionId,
                role = Role.ASSISTANT,
                status = MessageStatus.FINISHED,
                insertedAt = 1_700_000_000_000L,
                model = "deepseek-flash",
                providerId = "deepseek",
                promptTokens = 1_000,
                completionTokens = 1_000,
            ),
        )
        db.messageDao().setCost(stamped, 12_345L)

        // 换成第三方继续聊：已经花掉的 ￥ 不能因为当前服务商变了就消失
        repository.updateSettings(
            sessionId = sessionId,
            providerId = "acme",
            thinkingEnabled = true,
            searchEnabled = false,
            effort = ThinkingEffort.LOW,
            model = "m1",
        )
        assertEquals(12_345L, repository.usageSummary(sessionId).costMicros)
    }

    @Test
    fun `老数据按消息自己的服务商补算`() = runBlocking {
        val repository = repository()
        val sessionId = repository.createSession(providerId = "deepseek", model = "deepseek-flash")

        // 迁移会把老行归属到会话当时的服务商；这里模拟那一列已补上、但没有定格费用的情况
        db.messageDao().insert(
            MessageEntity(
                sessionId = sessionId,
                role = Role.ASSISTANT,
                status = MessageStatus.FINISHED,
                insertedAt = 1_700_000_000_000L,
                model = "deepseek-flash",
                providerId = "deepseek",
                promptTokens = 1_000,
                completionTokens = 1_000,
            ),
        )
        val legacyCost = repository.usageSummary(sessionId).costMicros
        assertTrue("官方老数据仍能算出费用", legacyCost > 0)

        // 会话换到第三方后，老数据仍按它自己的服务商补算，不能凭空归零
        repository.updateSettings(
            sessionId = sessionId,
            providerId = "acme",
            thinkingEnabled = true,
            searchEnabled = false,
            effort = ThinkingEffort.LOW,
            model = "m1",
        )
        assertEquals(legacyCost, repository.usageSummary(sessionId).costMicros)
    }
}
