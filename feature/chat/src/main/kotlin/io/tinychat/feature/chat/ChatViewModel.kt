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
import io.tinychat.model.KnownProviders
import io.tinychat.model.MessageStatus
import io.tinychat.model.RequestPayload
import io.tinychat.model.ResponsePayload
import io.tinychat.model.Role
import io.tinychat.model.ThinkPayload
import io.tinychat.model.ThinkingEffort
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
    /** 思维链用时（秒）；服务端没给就是 null */
    val thinkingSeconds: Double?,
    /** 还在思考（流式中）→ 折叠头显示「正在思考」 */
    val isThinking: Boolean,
    val status: MessageStatus,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
    val title: String? = null,
    val modelId: String = KnownProviders.flash.id,
    val effort: ThinkingEffort = ThinkingEffort.HIGH,
    val thinkingEnabled: Boolean = true,
    val searchEnabled: Boolean = false,
)

/**
 * 会话页状态机。M1-7 的「发送 / 停止」在这里收口：
 * 停止 = `job.cancel()`，取消会一路传到 OkHttp 的 `call.cancel()`。
 *
 * 底栏的模型/强度/搜索也是这里管：改的是**会话快照**，只影响之后的请求。
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
                val id = sessionId ?: repository.createSession(
                    thinkingEnabled = state.thinkingEnabled,
                    searchEnabled = state.searchEnabled,
                    effort = state.effort,
                ).also {
                    sessionId = it
                    observe(it)
                }

                if (sessionId == id) persistSettings(id)

                val outcome = repository.send(id, text, apiKey)
                state = state.copy(title = repository.session(id)?.title ?: outcome.title)
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
        state = ChatUiState(
            modelId = state.modelId,
            effort = state.effort,
            thinkingEnabled = state.thinkingEnabled,
            searchEnabled = state.searchEnabled,
        )
    }

    fun toggleThinking() {
        state = state.copy(thinkingEnabled = !state.thinkingEnabled)
        persistStandalone()
    }

    fun toggleSearch() {
        state = state.copy(searchEnabled = !state.searchEnabled)
        persistStandalone()
    }

    fun selectModel(modelId: String, effort: ThinkingEffort) {
        // v4-pro 不支持服务端搜索，切过去就顺手关掉
        val capabilities = KnownProviders.deepSeek.model(modelId)?.capabilities
        val search = state.searchEnabled && (capabilities?.webSearch == true)
        state = state.copy(modelId = modelId, effort = effort, searchEnabled = search)
        persistStandalone()
    }

    fun clearError() {
        state = state.copy(error = null)
    }

    private fun persistStandalone() {
        val id = sessionId ?: return
        viewModelScope.launch { persistSettings(id) }
    }

    private suspend fun persistSettings(id: String) {
        repository.updateSettings(
            sessionId = id,
            thinkingEnabled = state.thinkingEnabled,
            searchEnabled = state.searchEnabled,
            effort = state.effort,
            model = state.modelId,
        )
    }

    private fun observe(id: String) {
        observing?.cancel()
        observing = viewModelScope.launch {
            repository.observeMessages(id).collect { rows ->
                state = state.copy(messages = rows.mapNotNull { it.toChatMessage() })
            }
        }
        viewModelScope.launch {
            state = state.copy(title = repository.session(id)?.title)
        }
    }

    private fun MessageWithFragments.toChatMessage(): ChatMessage? {
        val fragments = orderedFragments
        val isUser = message.role == Role.USER

        if (isUser) {
            val request = fragments
                .filter { it.type == "REQUEST" }
                .mapNotNull { (FragmentPayloadCodec.decode(it.payload) as? RequestPayload)?.content }
                .joinToString("")
            if (request.isBlank()) return null
            return ChatMessage(message.id, true, request, null, null, false, message.status)
        }

        val text = fragments
            .filter { it.type == "RESPONSE" }
            .mapNotNull { (FragmentPayloadCodec.decode(it.payload) as? ResponsePayload)?.content }
            .joinToString("")

        val thinkPayloads = fragments
            .filter { it.type == "THINK" }
            .mapNotNull { FragmentPayloadCodec.decode(it.payload) as? ThinkPayload }

        val thinking = thinkPayloads.joinToString("") { it.content }.ifBlank { null }
        val seconds = thinkPayloads.mapNotNull { it.elapsedSecs }.takeIf { it.isNotEmpty() }?.sum()

        if (text.isBlank() && thinking == null) return null

        return ChatMessage(
            id = message.id,
            isUser = false,
            text = text,
            thinking = thinking,
            thinkingSeconds = seconds,
            isThinking = message.status == MessageStatus.WIP,
            status = message.status,
        )
    }

    companion object {
        fun factory(repository: ChatRepository, apiKey: String) = viewModelFactory {
            initializer { ChatViewModel(repository, apiKey) }
        }
    }
}
