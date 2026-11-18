package io.tinychat.feature.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.tinychat.data.chat.ChatRepository
import io.tinychat.data.chat.RunOutcome
import io.tinychat.data.db.FragmentEntity
import io.tinychat.data.db.MessageWithFragments
import io.tinychat.model.FragmentPayloadCodec
import io.tinychat.model.BranchNode
import io.tinychat.model.ErrorKind
import io.tinychat.model.KnownProviders
import io.tinychat.model.MessageStatus
import io.tinychat.model.RequestPayload
import io.tinychat.model.ResponsePayload
import io.tinychat.model.Role
import io.tinychat.model.SessionRow
import io.tinychat.model.SessionsUiState
import io.tinychat.model.ThinkPayload
import io.tinychat.model.ThinkingEffort
import io.tinychat.model.ToolFindPayload
import io.tinychat.model.ToolOpenPayload
import io.tinychat.model.ToolSearchPayload
import io.tinychat.model.versionPointer
import io.tinychat.net.transport.ChatException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
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

/** 正文里的一处引用来源（M2-8）。同一 URL 只对应一条来源，编号复用 */
data class ChatSource(
    /** 角标编号，1 基，等于它在来源列表里的位置 */
    val index: Int,
    val title: String?,
    val url: String?,
    /** 站点域名（如 `sina.com.cn`），来自搜索结果的 site_name */
    val site: String,
    /** favicon；搜索结果里没有就用域名拼 DeepSeek 的图标 CDN */
    val iconUrl: String?,
    /** 网页发布日期，ISO-8601（UTC） */
    val publishedAt: String?,
    /** 网页摘要（搜索结果的 snippet，兜底用被引用的原句） */
    val snippet: String?,
) {
    /** `yyyy/MM/dd`，按本地时区（服务端给的是 UTC，直接截前 10 位会差一天） */
    val publishedDate: String?
        get() = publishedAt?.let {
            runCatching {
                java.time.OffsetDateTime.parse(it)
                    .atZoneSameInstant(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd"))
            }.getOrNull()
        }
}

/** 正文里的一处角标：位置 + 指向哪条来源 */
data class SourceAnchor(val anchor: Int, val index: Int)

/** 会话页的一条消息（已从碎片拼好）。 */
data class ChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    /** 引用来源（正文内联角标 + 末尾来源胶囊共用这一份） */
    val sources: List<ChatSource>,
    /** 正文里的角标位置（同一来源可能被引用多次，每次一个位置） */
    val anchors: List<SourceAnchor>,
    /** 思维链时间轴；纯工具轮次也可能只有工具项 */
    val timeline: List<TimelineItem>,
    val thinkingSeconds: Double?,
    val isThinking: Boolean,
    val status: MessageStatus,
    /** 同一条提问下的第几版回答 / 同一位置的第几次提问（1 基）；只有一版时为 1/1 */
    val versionIndex: Int = 1,
    val versionCount: Int = 1,
    /** 这条回答是停住/截断的，且没动用服务端工具 —— 可以「继续生成」 */
    val canContinue: Boolean = false,
    /**
     * 这条用户消息后面那条回答失败了（可手动重试）：值为失败回答的 messageId。
     * 界面上表现为气泡左侧的橙色重试圆钮。
     */
    val retryTargetId: Long? = null,
    /** 失败提示（灰字，右贴边），如「请检查网络后重试」 */
    val retryHint: String? = null,
) {
    val hasThinking: Boolean get() = timeline.isNotEmpty()
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
    /** 错误分类：决定文案与「去设置」这类动作 */
    val errorKind: ErrorKind? = null,
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

    /** 抽屉整块状态：列表 + 搜索页 + 多选 */
    var sessionsUi by mutableStateOf(SessionsUiState())
        private set

    var sessionId: String? by mutableStateOf(null)
        private set

    private var job: Job? = null
    private var observing: Job? = null
    private var searchJob: Job? = null

    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    init {
        viewModelScope.launch {
            repository.recoverInterrupted()
            // 冷启动**不**恢复上次会话（2026-11-18 定）：直接停在空白新对话，
            // 历史都在抽屉里。之前读 updated_at 最大的那条自动进去，用户明确不要。
        }
        viewModelScope.launch {
            repository.observeSessions().collect { rows ->
                sessionsUi = sessionsUi.copy(
                    rows = rows.map { SessionRow(it.id, it.title, it.pinned, it.updatedAt) },
                )
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

    // —— 抽屉：搜索 ——

    fun openSearch() {
        sessionsUi = sessionsUi.copy(searching = true, query = "", results = emptyList())
    }

    fun closeSearch() {
        searchJob?.cancel()
        sessionsUi = sessionsUi.copy(searching = false, query = "", results = emptyList())
    }

    /** 搜索输入：防抖 150ms，避免每个字都打一次库 */
    fun onSearchQueryChange(query: String) {
        sessionsUi = sessionsUi.copy(query = query, results = if (query.isBlank()) emptyList() else sessionsUi.results)
        searchJob?.cancel()
        if (query.isBlank()) return
        searchJob = viewModelScope.launch {
            delay(150)
            val hits = repository.searchSessions(query)
            sessionsUi = sessionsUi.copy(results = hits)
        }
    }

    /** 搜索结果点一条：切到那个会话，并让侧栏定位过去 */
    fun pickSearchResult(id: String) {
        closeSearch()
        openSession(id)
    }

    // —— 抽屉：多选 ——

    fun enterSelection() {
        sessionsUi = sessionsUi.copy(selectionMode = true, selected = emptySet())
    }

    fun exitSelection() {
        sessionsUi = sessionsUi.copy(selectionMode = false, selected = emptySet())
    }

    fun toggleSelected(id: String) {
        val selected = sessionsUi.selected
        sessionsUi = sessionsUi.copy(
            selected = if (id in selected) selected - id else selected + id,
        )
    }

    fun pinSelected() {
        val ids = sessionsUi.selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { repository.setSessionPinned(it, true) }
            exitSelection()
        }
    }

    fun deleteSelected() {
        val ids = sessionsUi.selected.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { repository.deleteSession(it) }
            if (sessionId in ids) newChat()
            exitSelection()
        }
    }

    fun onInputChange(text: String) {
        state = state.copy(input = text)
    }

    fun send() {
        val text = state.input.trim()
        if (text.isEmpty() || state.isStreaming) return

        val editingId = state.editingMessageId
        state = state.copy(input = "", isStreaming = true, error = null, errorKind = null, editingMessageId = null)
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
                state = state.copy(
                    title = repository.session(id)?.title ?: outcome.title,
                ).withOutcomeError(outcome)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ChatException) {
                state = state.copy(error = e.kind.userMessage, errorKind = e.kind)
            } catch (e: Exception) {
                // 非协议错误（本地异常）：给一句能看懂的话，别再抛原始堆栈文案
                state = state.copy(error = "生成失败，请重试", errorKind = null)
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

    /** 切换版本（M1-8）：只在有兄弟分支时有效；指针换到那条分支的叶子上 */
    fun switchVersion(messageId: Long, delta: Int) {
        val id = sessionId ?: return
        if (state.isStreaming) return
        viewModelScope.launch { repository.switchVersion(id, messageId, delta) }
    }

    /** 重新生成（M1-8）：在当前这条回答下再生成一版，旧版留着可切回 */
    fun regenerate(message: ChatMessage) {
        val id = sessionId ?: return
        if (state.isStreaming || message.isUser) return

        state = state.copy(isStreaming = true, error = null)
        job = viewModelScope.launch {
            try {
                state = state.withOutcomeError(repository.regenerate(id, message.id, apiKey))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ChatException) {
                state = state.copy(error = e.kind.userMessage, errorKind = e.kind)
            } catch (e: Exception) {
                // 非协议错误（本地异常）：给一句能看懂的话，别再抛原始堆栈文案
                state = state.copy(error = "生成失败，请重试", errorKind = null)
            } finally {
                state = state.copy(isStreaming = false)
            }
        }
    }

    /** 继续生成（M1-9）：把停住的回答接着写下去，内容原地追加 */
    fun continueGeneration(message: ChatMessage) {
        val id = sessionId ?: return
        if (state.isStreaming || message.isUser || !message.canContinue) return

        state = state.copy(isStreaming = true, error = null)
        job = viewModelScope.launch {
            try {
                state = state.withOutcomeError(repository.continueGeneration(id, message.id, apiKey))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ChatException) {
                state = state.copy(error = e.kind.userMessage, errorKind = e.kind)
            } catch (e: Exception) {
                // 非协议错误（本地异常）：给一句能看懂的话，别再抛原始堆栈文案
                state = state.copy(error = "生成失败，请重试", errorKind = null)
            } finally {
                state = state.copy(isStreaming = false)
            }
        }
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

    /**
     * 结局里的错误：可重试（断网/限流/5xx）交给行内橙色圆钮 + 灰字，不弹底部错误条；
     * 其余（Key 无效、余额不足…）才走底部提示。
     */
    private fun ChatUiState.withOutcomeError(outcome: RunOutcome): ChatUiState {
        val error = outcome.error ?: return this
        if (error.kind.retryable) return this
        return copy(error = error.kind.userMessage, errorKind = error.kind)
    }

    /** 点橙色圆钮：把失败的那一轮重发一次（不新建用户消息，不消耗修改次数） */
    fun retryFailed(message: ChatMessage) {
        val targetId = message.retryTargetId ?: return
        val id = sessionId ?: return
        if (state.isStreaming) return

        state = state.copy(isStreaming = true, error = null, errorKind = null)
        job = viewModelScope.launch {
            try {
                state = state.withOutcomeError(repository.regenerate(id, targetId, apiKey))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ChatException) {
                state = state.copy(error = e.kind.userMessage, errorKind = e.kind)
            } catch (e: Exception) {
                state = state.copy(error = "生成失败，请重试", errorKind = null)
            } finally {
                state = state.copy(isStreaming = false)
            }
        }
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
                    val branchNodes = rows.map {
                        BranchNode(it.message.id, it.message.parentId, it.message.insertedAt)
                    }
                    // 失败的那一轮：用户消息左侧要挂橙色重试圆钮，下面挂灰字提示
                    val failedAfterUser = HashMap<Long, MessageWithFragments>()
                    chain.forEachIndexed { index, entry ->
                        val next = chain.getOrNull(index + 1)?.message ?: return@forEachIndexed
                        val current = entry.message
                        val retryable = next.errorKind?.retryable == true
                        if (current.role == Role.USER && next.role == Role.ASSISTANT &&
                            next.parentId == current.id && next.status == MessageStatus.FAILED && retryable
                        ) {
                            failedAfterUser[current.id] = chain[index + 1]
                        }
                    }

                    state = state.copy(
                        messages = chain.mapNotNull { entry ->
                            val (index, count) = versionPointer(branchNodes, entry.message.id)
                            val failed = failedAfterUser[entry.message.id]
                            entry.toChatMessage()?.copy(
                                versionIndex = index,
                                versionCount = count,
                                retryTargetId = failed?.message?.id,
                                retryHint = failed?.message?.errorKind?.retryHint,
                            )
                        },
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
                sources = emptyList(),
                anchors = emptyList(),
                timeline = emptyList(),
                thinkingSeconds = null,
                isThinking = false,
                status = message.status,
            )
        }

        val responses = fragments
            .filter { it.type == "RESPONSE" }
            .mapNotNull { FragmentPayloadCodec.decode(it.payload) as? ResponsePayload }

        val textBuilder = StringBuilder()
        val sources = mutableListOf<ChatSource>()
        val anchors = mutableListOf<SourceAnchor>()
        // 引用对象只有 url/title，站名、favicon、发布日期、摘要都在搜索结果里 → 按 url 关联
        val searchByUrl = fragments
            .mapNotNull { FragmentPayloadCodec.decode(it.payload) as? ToolSearchPayload }
            .flatMap { it.results }
            .associateBy { it.url }
        val openByUrl = fragments
            .mapNotNull { FragmentPayloadCodec.decode(it.payload) as? ToolOpenPayload }
            .associateBy { it.url }
        // 同一 URL 只列一条来源、正文里复用同一个编号（官方 App 就是这么做的）
        val indexByUrl = LinkedHashMap<String, Int>()

        responses.forEach { response ->
            textBuilder.append(response.content)
            response.citations?.forEach { element ->
                val obj = element.jsonObject
                val url = obj["url"]?.jsonPrimitive?.contentOrNull
                val title = obj["title"]?.jsonPrimitive?.contentOrNull
                // 锚点要落在「最后一个非空白字符之后」，否则会掉进段落之间的空行里，
                // 段落级渲染就贴不上角标了（实测：150 落在 71..148 与 166.. 之间）
                val anchor = textBuilder.trimmedEnd()
                val key = url ?: title.orEmpty()
                val index = indexByUrl.getOrPut(key) {
                    val search = url?.let { searchByUrl[it] }
                    val open = url?.let { openByUrl[it] }
                    val host = url?.let { runCatching { java.net.URI(it).host }.getOrNull() }
                        ?.removePrefix("www.")
                    // 编号 = 来源在列表里的位置（1 基）
                    val newIndex = sources.size + 1
                    sources += ChatSource(
                        index = newIndex,
                        title = title ?: search?.title,
                        url = url,
                        site = search?.siteName ?: open?.siteName ?: host.orEmpty(),
                        iconUrl = search?.siteIcon
                            ?: open?.siteIcon
                            ?: host?.let { "https://cdn.deepseek.com/site-icons/$it" },
                        publishedAt = search?.publishedAt ?: open?.publishedAt,
                        snippet = search?.snippet ?: obj["cited_text"]?.jsonPrimitive?.contentOrNull,
                    )
                    newIndex
                }
                anchors += SourceAnchor(anchor = anchor, index = index)
            }
        }
        val text = textBuilder.toString()

        val timeline = buildTimeline(fragments)
        if (text.isBlank() && timeline.isEmpty()) return null

        val seconds = fragments
            .filter { it.type == "THINK" }
            .mapNotNull { (FragmentPayloadCodec.decode(it.payload) as? ThinkPayload)?.elapsedSecs }
            .takeIf { it.isNotEmpty() }
            ?.sum()

        // 「继续生成」靠 assistant 预填，必须有正文：思考阶段就被掐断的那种给「重新生成」
        val canContinue = message.status == MessageStatus.INCOMPLETE &&
            timeline.none { it is TimelineItem.Tool } &&
            fragments.none { it.type == "TEMPLATE_RESPONSE" } &&
            text.isNotBlank()

        return ChatMessage(
            id = message.id,
            isUser = false,
            text = text,
            sources = sources,
            anchors = anchors,
            timeline = timeline,
            thinkingSeconds = seconds,
            isThinking = message.status == MessageStatus.WIP,
            status = message.status,
            canContinue = canContinue,
        )
    }

    /** 最后一个非空白字符之后的位置（不含行尾空白） */
private fun StringBuilder.trimmedEnd(): Int {
    var i = length
    while (i > 0 && this[i - 1].isWhitespace()) i--
    return i
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
