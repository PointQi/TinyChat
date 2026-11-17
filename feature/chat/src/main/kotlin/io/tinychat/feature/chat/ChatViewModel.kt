package io.tinychat.feature.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.tinychat.data.chat.ChatRepository
import io.tinychat.data.db.MessageWithFragments
import io.tinychat.model.FragmentPayloadCodec
import io.tinychat.model.MessageStatus
import io.tinychat.model.RequestPayload
import io.tinychat.model.ResponsePayload
import io.tinychat.model.Role
import io.tinychat.model.ThinkPayload
import io.tinychat.net.transport.ChatException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 会话页的一条消息（已从碎片拼好）。 */
data class ChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val thinking: String?,
    val status: MessageStatus,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
)

/**
 * 会话页状态机。M1-7 的「发送 / 停止」都在这里收口：
 * 停止 = `job.cancel()`，取消会一路传到 OkHttp 的 `call.cancel()`。
 */
class ChatViewModel(
    private val repository: ChatRepository,
    private val apiKey: String,
) : ViewModel() {

    var state by mutableStateOf(ChatUiState())
        private set

    private var sessionId: String? = null
    private var job: Job? = null
    private var observing: Job? = null

    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    init {
        viewModelScope.launch { repository.recoverInterrupted() }
    }

    fun onInputChange(text: String) {
        state = state.copy(input = text)
    }

    fun send() {
        val text = state.input.trim()
        if (text.isEmpty() || state.isStreaming) return

        state = state.copy(input = "", isStreaming = true, error = null)
        job = viewModelScope.launch {
            try {
                val id = sessionId ?: repository.createSession().also {
                    sessionId = it
                    observe(it)
                }
                repository.send(id, text, apiKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ChatException) {
                state = state.copy(error = e.message ?: "请求失败")
            } catch (e: Exception) {
                state = state.copy(error = e.message ?: "请求失败")
            } finally {
                state = state.copy(isStreaming = false)
            }
        }
    }

    fun stop() {
        job?.cancel()
        state = state.copy(isStreaming = false)
    }

    fun newChat() {
        job?.cancel()
        observing?.cancel()
        sessionId = null
        state = ChatUiState()
    }

    private fun observe(id: String) {
        observing?.cancel()
        observing = viewModelScope.launch {
            repository.observeMessages(id).collect { rows ->
                state = state.copy(messages = rows.mapNotNull { it.toChatMessage() })
            }
        }
    }

    private fun MessageWithFragments.toChatMessage(): ChatMessage? {
        val fragments = orderedFragments
        val text = fragments
            .filter { it.type == "RESPONSE" }
            .mapNotNull { (FragmentPayloadCodec.decode(it.payload) as? ResponsePayload)?.content }
            .joinToString("")
        val thinking = fragments
            .filter { it.type == "THINK" }
            .mapNotNull { (FragmentPayloadCodec.decode(it.payload) as? ThinkPayload)?.content }
            .joinToString("")
            .ifBlank { null }

        val isUser = message.role == Role.USER
        if (isUser) {
            val request = fragments
                .filter { it.type == "REQUEST" }
                .mapNotNull { (FragmentPayloadCodec.decode(it.payload) as? RequestPayload)?.content }
                .joinToString("")
            if (request.isBlank()) return null
            return ChatMessage(message.id, true, request, null, message.status)
        }

        if (text.isBlank() && thinking == null) return null
        return ChatMessage(message.id, false, text, thinking, message.status)
    }

    companion object {
        fun factory(repository: ChatRepository, apiKey: String) = viewModelFactory {
            initializer { ChatViewModel(repository, apiKey) }
        }
    }
}
