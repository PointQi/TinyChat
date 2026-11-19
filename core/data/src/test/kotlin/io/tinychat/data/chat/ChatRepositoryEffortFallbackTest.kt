package io.tinychat.data.chat

import androidx.test.core.app.ApplicationProvider
import io.tinychat.data.attachment.AttachmentStore
import io.tinychat.data.attachment.AttachmentUploader
import io.tinychat.data.db.DatabaseFactory
import io.tinychat.data.db.TinyChatDatabase
import io.tinychat.data.provider.ProviderStore
import io.tinychat.model.Capabilities
import io.tinychat.model.KnownProviders
import io.tinychat.model.MessageStatus
import io.tinychat.model.ProviderMeta
import io.tinychat.model.ThinkingMode
import io.tinychat.net.SseEvent
import io.tinychat.net.protocol.PreparedRequest
import io.tinychat.net.transport.ChatException
import io.tinychat.net.transport.ChatTransport
import io.tinychat.model.ErrorKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * M8-1 收尾：第三方不认 `reasoning_effort` 时的自愈。
 *
 * 第一次请求 400（报文点名参数）→ 去掉参数重发 → 成功，并在服务商上记住 `no_effort`。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatRepositoryEffortFallbackTest {

    private lateinit var db: TinyChatDatabase
    private lateinit var providerStore: ProviderStore
    private lateinit var attachmentStore: AttachmentStore

    private class FallbackTransport : ChatTransport {
        var calls = 0
            private set
        var lastBody: String? = null
            private set

        override fun stream(request: PreparedRequest): Flow<SseEvent> = flow {
            calls++
            lastBody = request.bodyJson
            if (request.bodyJson.contains("reasoning_effort")) {
                throw ChatException(
                    kind = ErrorKind.STREAM,
                    httpStatus = 400,
                    message = "reasoning_effort is not supported by this model",
                )
            }
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
        val acme = KnownProviders.genericOpenAiCompatible(
            id = "acme",
            displayName = "Acme",
            baseUrl = "https://acme.example/v1",
            modelId = "m1",
        ).copy(
            models = listOf(
                KnownProviders.genericOpenAiCompatible(
                    id = "acme",
                    displayName = "Acme",
                    baseUrl = "https://acme.example/v1",
                    modelId = "m1",
                ).models.single().copy(
                    capabilities = Capabilities(thinking = true, effort = true),
                    thinking = ThinkingMode.EFFORT,
                ),
            ),
        )
        providerStore.save(acme)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `被拒后去掉 reasoning_effort 重发并记住`() = runBlocking {
        val transport = FallbackTransport()
        val repository = ChatRepository(
            db = db,
            transport = transport,
            clock = { 1_700_000_000_000L },
            isOnline = { true },
            attachmentStore = attachmentStore,
            uploader = AttachmentUploader(),
            providerStore = providerStore,
            apiKeyFor = { "k" },
        )

        val sessionId = repository.createSession(providerId = "acme", model = "m1")
        val outcome = repository.send(sessionId, "hi")

        assertEquals("应该只重试一次", 2, transport.calls)
        assertEquals(MessageStatus.FINISHED, outcome.status)
        assertTrue("降级后的请求不再带参数", transport.lastBody?.contains("reasoning_effort") == false)
        assertEquals("1", providerStore.byId("acme")!!.meta[ProviderMeta.NO_EFFORT])

        // 再发一轮：已记住，不再先撞一次 400
        repository.send(sessionId, "again")
        assertEquals("记住之后不应再试错", 3, transport.calls)
        assertFalse(transport.lastBody?.contains("reasoning_effort") == true)
    }
}
