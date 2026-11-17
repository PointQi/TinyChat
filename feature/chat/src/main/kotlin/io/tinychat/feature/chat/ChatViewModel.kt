package io.tinychat.feature.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.tinychat.data.chat.ChatRepository
import io.tinychat.data.db.FragmentEntity
import io.tinychat.data.db.MessageWithFragments
import io.tinychat.model.FragmentPayloadCodec
import io.tinychat.model.KnownProviders
import io.tinychat.model.MessageStatus
import io.tinychat.model.RequestPayload
import io.tinychat.model.ResponsePayload
import io.tinychat.model.Role
import io.tinychat.model.SessionRow
import io.tinychat.model.ThinkPayload
import io.tinychat.model.ThinkingEffort
import io.tinychat.model.ToolFindPayload
import io.tinychat.model.ToolOpenPayload
import io.tinychat.model.ToolSearchPayload
import io.tinychat.net.transport.ChatException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 思维链里的一步：一段思考文字，或一次工具动作。 */
sealed interface TimelineItem {
    data class Text(val content: String) : TimelineItem
    data class Tool(val step: ToolStep) : TimelineItem
}

enum class ToolKind { SEARCH, OPEN, FIND }

/**
 * 工具步骤（§13）：搜索 → `搜索到 N 个网页`；浏览 → `已浏览 N 个页面`；
 * 页内查找 → `查找关键词 "pattern"`。
 */
data class ToolStep(
    val kind: ToolKind,
    val title: String,
    val subtitle: String?,
    val items: List<String>,
)

/** 会话页的一条消息（已从碎片拼好）。 */
data class ChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    /** 引用角标（正文里的 [n]） */
    val citations: List<String>,
    /** 思维链时间轴；纯工具轮次也可能只有工具项 */
    val timeline: List<TimelineItem>,
    val thinkingSeconds: Double?,
    val isThinking: Boolean,
    val status: MessageStatus,
) {
    val hasThinking: Boolean get() = timeline.isNotEmpty()
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
    val title: String? = null,
    val modelId: String = KnownProviders.flash.id,
    /**
     * 默认 `Flash 低`（对齐官方 App 默认设定）。
     *
     * 日常问答上更高强度换不来多少质量，却要多花时间与思考 token；需要深想时手动点底栏切。
     */
    val effort: ThinkingEffort = ThinkingEffort.LOW,
    val thinkingEnabled: Boolean = true,
    val searchEnabled: Boolean = false,
    /** 非空 = 正在编辑这条用户消息 */
    val editingMessageId: Long? = null,
)

/**
 * 会话页状态机。发送/停止、底栏模型与强度都在这里收口。
 * 停止 = `job.cancel()`，取消会一路传到 OkHttp 的 `call.cancel()`。
 */
class ChatViewModel(
    private val repository: ChatRepository,
    private val apiKey: String,
) : ViewModel() {

    var state by mutableStateOf(ChatUiState())
        private set

    /** 抽屉列表数据（真实库，按 `pinned DESC, updated_at DESC`） */
    var sessions by mutableStateOf<List<SessionRow>>(emptyList())
        private set

    var sessionId: String? by mutableStateOf(null)
        private set

    private var job: Job? = null
    private var observing: Job? = null

    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    init {
        viewModelScope.launch {
            repository.recoverInterrupted()
            // 冷启动恢复上次会话：没有就停在新对话（会话首次发送才落库）
            repository.latestSession()?.let { openSession(it.id) }
        }
        viewModelScope.launch {
            repository.observeSessions().collect { rows ->
                sessions = rows.map { SessionRow(it.id, it.title, it.pinned, it.updatedAt) }
            }
        }
    }

    /** 抽屉里点一条会话 */
    fun openSession(id: String) {
        if (id == sessionId) return
        job?.cancel()
        sessionId = id
        state = state.copy(
            isStreaming = false,
            error = null,
            editingMessageId = null,
            input = "",
        )
        observe(id)
        // 会话保存了自己的模型/强度/开关，切过来要跟着变（一次性读，不跟本地切换打架）
        viewModelScope.launch {
            val row = repository.session(id) ?: return@launch
            state = state.copy(
                modelId = row.model,
                effort = row.effort ?: ThinkingEffort.LOW,
                thinkingEnabled = row.thinkingEnabled,
                searchEnabled = row.searchEnabled,
                title = row.title,
            )
        }
    }

    fun renameSession(id: String, title: String) {
        viewModelScope.launch { repository.renameSession(id, title) }
    }

    fun togglePinnedSession(id: String, pinned: Boolean) {
        viewModelScope.launch { repository.setSessionPinned(id, pinned) }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            repository.deleteSession(id)
            if (id == sessionId) newChat()
        }
    }

    fun onInputChange(text: String) {
        state = state.copy(input = text)
    }

    fun send() {
        val text = state.input.trim()
        if (text.isEmpty() || state.isStreaming) return

        val editingId = state.editingMessageId
        state = state.copy(input = "", isStreaming = true, error = null, editingMessageId = null)
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

                persistSettings(id)
                val outcome = if (editingId != null) {
                    // 编辑重发：在新分支上跑，旧分支留库
                    repository.editMessage(id, editingId, text, apiKey)
                } else {
                    repository.send(id, text, apiKey)
                }
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

    /** 点一下用户消息：进入编辑重发 */
    fun startEdit(message: ChatMessage) {
        if (message.isUser.not() || state.isStreaming) return
        state = state.copy(editingMessageId = message.id, input = message.text)
    }

    fun cancelEdit() {
        state = state.copy(editingMessageId = null, input = "")
    }

    /** 复制整个会话（纯文本，用户/AI 交替） */
    fun conversationText(): String = buildString {
        state.messages.forEach { message ->
            if (message.isUser) append("我：")
            append(message.text.trim())
            append("\n\n")
        }
    }.trim()

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
            combine(
                repository.observeMessages(id),
                repository.observeSession(id),
            ) { rows, session -> rows to session }
                .collect { (rows, session) ->
                    // 只渲染**当前分支**：从 current_message_id 往回走
                    val chain = chainOf(rows, session?.currentMessageId)
                    state = state.copy(
                        messages = chain.mapNotNull { it.toChatMessage() },
                        title = session?.title,
                    )
                }
        }
    }

    private fun chainOf(rows: List<MessageWithFragments>, leafId: Long?): List<MessageWithFragments> {
        if (leafId == null) return emptyList()
        val byId = rows.associateBy { it.message.id }
        val path = ArrayDeque<MessageWithFragments>()
        var cursor: Long? = leafId
        val guard = HashSet<Long>()
        while (cursor != null && guard.add(cursor)) {
            val node = byId[cursor] ?: break
            path.addFirst(node)
            cursor = node.message.parentId
        }
        return path.toList()
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
            return ChatMessage(
                id = message.id,
                isUser = true,
                text = request,
                citations = emptyList(),
                timeline = emptyList(),
                thinkingSeconds = null,
                isThinking = false,
                status = message.status,
            )
        }

        val responses = fragments
            .filter { it.type == "RESPONSE" }
            .mapNotNull { FragmentPayloadCodec.decode(it.payload) as? ResponsePayload }

        val text = responses.joinToString("") { it.content }
        val citations = responses
            .flatMap { it.citations?.mapNotNull { element -> element.jsonObject["title"]?.jsonPrimitive?.content } ?: emptyList() }

        val timeline = buildTimeline(fragments)
        if (text.isBlank() && timeline.isEmpty()) return null

        val seconds = fragments
            .filter { it.type == "THINK" }
            .mapNotNull { (FragmentPayloadCodec.decode(it.payload) as? ThinkPayload)?.elapsedSecs }
            .takeIf { it.isNotEmpty() }
            ?.sum()

        return ChatMessage(
            id = message.id,
            isUser = false,
            text = text,
            citations = citations,
            timeline = timeline,
            thinkingSeconds = seconds,
            isThinking = message.status == MessageStatus.WIP,
            status = message.status,
        )
    }

    /** 把 THINK 与工具碎片按 ord 顺序排成时间轴；连续的浏览合并成一条 */
    private fun buildTimeline(fragments: List<FragmentEntity>): List<TimelineItem> {
        val items = mutableListOf<TimelineItem>()
        val buffer = StringBuilder()
        val openSites = mutableListOf<String>()

        fun flushText() {
            val content = buffer.toString().trim()
            if (content.isNotEmpty()) items += TimelineItem.Text(content)
            buffer.setLength(0)
        }

        fun flushOpen() {
            if (openSites.isNotEmpty()) {
                items += TimelineItem.Tool(
                    ToolStep(
                        kind = ToolKind.OPEN,
                        title = "已浏览 ${openSites.size} 个页面",
                        subtitle = openSites.joinToString("、"),
                        items = openSites.toList(),
                    ),
                )
                openSites.clear()
            }
        }

        for (fragment in fragments) {
            when (fragment.type) {
                "THINK" -> {
                    flushOpen()
                    val payload = FragmentPayloadCodec.decode(fragment.payload) as? ThinkPayload ?: continue
                    buffer.append(payload.content).append('\n')
                }

                "TOOL_SEARCH" -> {
                    flushOpen()
                    val payload = FragmentPayloadCodec.decode(fragment.payload) as? ToolSearchPayload ?: continue
                    items += TimelineItem.Tool(
                        ToolStep(
                            kind = ToolKind.SEARCH,
                            title = "搜索到 ${payload.results.size} 个网页",
                            subtitle = payload.queries.joinToString(" "),
                            items = payload.results.map { it.siteName ?: it.url },
                        ),
                    )
                }

                "TOOL_OPEN" -> {
                    flushText()
                    val payload = FragmentPayloadCodec.decode(fragment.payload) as? ToolOpenPayload ?: continue
                    openSites += payload.siteName ?: payload.title
                }

                "TOOL_FIND" -> {
                    flushOpen()
                    val payload = FragmentPayloadCodec.decode(fragment.payload) as? ToolFindPayload ?: continue
                    items += TimelineItem.Tool(
                        ToolStep(
                            kind = ToolKind.FIND,
                            title = "查找关键词 \"${payload.pattern}\"",
                            subtitle = null,
                            items = emptyList(),
                        ),
                    )
                }
            }
        }

        flushText()
        flushOpen()
        return items
    }

    companion object {
        fun factory(repository: ChatRepository, apiKey: String) = viewModelFactory {
            initializer { ChatViewModel(repository, apiKey) }
        }
    }
}
