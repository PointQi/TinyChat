package io.tinychat.data.chat

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.tinychat.data.attachment.AttachmentStore
import io.tinychat.data.attachment.AttachmentUploader
import io.tinychat.data.attachment.PickedFile
import io.tinychat.data.db.DatabaseFactory
import io.tinychat.data.provider.ProviderStore
import io.tinychat.model.AttachmentState
import io.tinychat.model.KnownProviders
import io.tinychat.net.SseEvent
import io.tinychat.net.protocol.PreparedRequest
import io.tinychat.net.transport.ChatTransport
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatRepositoryAttachmentTest {
    @Test
    fun `OpenAI PDF and text attachments are read locally for send follow up and retry`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = DatabaseFactory.createInMemory(context)
        val localStore = AttachmentStore(context)
        var questionId: Long? = null
        try {
            val providers = ProviderStore.create(db)
            val custom = KnownProviders.genericOpenAiCompatible("test", "Test", "https://example.com/v1", "chat")
            val model = custom.models.single()
            providers.save(custom.copy(models = listOf(model.copy(capabilities = model.capabilities.copy(fileUpload = true)))))
            val requests = mutableListOf<PreparedRequest>()
            val repo = ChatRepository(
                db = db,
                transport = object : ChatTransport {
                    override fun stream(request: PreparedRequest): Flow<SseEvent> = flow {
                        requests += request
                        emit(SseEvent(null, """{"choices":[{"index":0,"delta":{"content":"reply"},"finish_reason":"stop"}]}"""))
                        emit(SseEvent(null, "[DONE]"))
                    }
                },
                attachmentStore = localStore,
                uploader = AttachmentUploader(),
                providerStore = providers,
                apiKeyFor = { "chat-key" },
            )
            val pdf = File(context.cacheDir, "fixture.pdf").apply { writeText("%PDF-1.7\n%%EOF") }
            val text = File(context.cacheDir, "fixture.txt").apply { writeText("notes from the document") }
            val id = repo.createSession(providerId = custom.id, model = model.id)
            val reply = repo.send(id, "", attachments = listOf(
                PickedFile(localStore.newId(), Uri.fromFile(pdf), pdf.name, pdf.length(), "application/pdf", false),
                PickedFile(localStore.newId(), Uri.fromFile(text), text.name, text.length(), "text/plain", false),
            ))
            val userId = db.messageDao().byId(reply.messageId)!!.parentId!!
            questionId = userId
            val stored = db.attachmentDao().byMessage(userId)
            assertEquals(2, stored.size)
            assertTrue(stored.all { it.remoteId == null && it.state == AttachmentState.LOCAL })

            repo.send(id, "follow up")
            repo.retryTurn(id, userId)
            assertEquals(3, requests.size)
            requests.forEach { request ->
                assertTrue(request.bodyJson.contains("\"type\":\"file\""))
                assertTrue(request.bodyJson.contains("data:application/pdf;base64,"))
                assertTrue(request.bodyJson.contains("fixture.pdf"))
                assertTrue(request.bodyJson.contains("fixture.txt"))
                assertTrue(request.bodyJson.contains("notes from the document"))
            }
        } finally {
            questionId?.let(localStore::deleteMessageDir)
            db.close()
        }
    }
}
