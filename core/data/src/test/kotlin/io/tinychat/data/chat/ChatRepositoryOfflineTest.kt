package io.tinychat.data.chat

import androidx.test.core.app.ApplicationProvider
import io.tinychat.data.db.DatabaseFactory
import io.tinychat.data.db.TinyChatDatabase
import io.tinychat.model.ErrorKind
import io.tinychat.model.MessageStatus
import io.tinychat.model.ThinkingEffort
import io.tinychat.net.protocol.PreparedRequest
import io.tinychat.net.transport.ChatTransport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 离线预检：没网时**不发请求**，立刻把这一轮落成失败（NETWORK），
 * 界面据此挂出橘色重试圆钮，用户不用干等 15s 连接超时。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatRepositoryOfflineTest {

    private lateinit var db: TinyChatDatabase

    @Before
    fun setUp() {
        db = DatabaseFactory.createInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() = db.close()

    private class NeverCalledTransport : ChatTransport {
        var calls = 0
            private set

        override fun stream(request: PreparedRequest): Flow<io.tinychat.net.SseEvent> = flow {
            calls++
        }
    }

    @Test
    fun `离线时不发请求，直接落 NETWORK 失败`() = runBlocking {
        val transport = NeverCalledTransport()
        val repository = ChatRepository(
            db = db,
            transport = transport,
            clock = { 1_700_000_000_000L },
            isOnline = { false },
        )
        val sessionId = repository.createSession(thinkingEnabled = false, effort = ThinkingEffort.LOW)

        val outcome = repository.send(sessionId, "断网时发一句", "key")

        assertEquals(0, transport.calls)
        assertEquals(MessageStatus.FAILED, outcome.status)
        assertEquals(ErrorKind.NETWORK, outcome.error?.kind)

        // 失败原因落库：界面靠 error_kind 判断要不要挂重试圆钮
        val assistant = db.messageDao().withFragments(sessionId)
            .map { it.message }
            .single { it.role == io.tinychat.model.Role.ASSISTANT }
        assertEquals(MessageStatus.FAILED, assistant.status)
        assertEquals(ErrorKind.NETWORK, assistant.errorKind)
        assertNotNull(assistant.errorMessage)
        assertTrue("用户消息仍要留在库里，便于重试", db.messageDao().withFragments(sessionId).size == 2)
        // 当前分支指向失败的回答，重试就是在这条上换一版
        assertEquals(assistant.id, db.sessionDao().byId(sessionId)!!.currentMessageId)
    }

    @Test
    fun `在线时仍然正常发请求`() = runBlocking {
        val transport = NeverCalledTransport()
        val repository = ChatRepository(
            db = db,
            transport = transport,
            clock = { 1_700_000_000_000L },
            isOnline = { true },
        )
        val sessionId = repository.createSession(thinkingEnabled = false, effort = ThinkingEffort.LOW)

        repository.send(sessionId, "在线的一句话", "key")

        assertEquals(1, transport.calls)
    }
}
