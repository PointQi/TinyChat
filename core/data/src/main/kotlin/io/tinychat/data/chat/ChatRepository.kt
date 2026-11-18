package io.tinychat.data.chat

import io.tinychat.data.db.MessageEntity
import io.tinychat.data.db.MessageWithFragments
import io.tinychat.data.db.SessionEntity
import io.tinychat.data.db.TinyChatDatabase
import io.tinychat.data.db.toDomain
import io.tinychat.data.db.toEntity
import io.tinychat.model.Fragment
import io.tinychat.model.BranchNode
import io.tinychat.model.FragmentPayloadCodec
import io.tinychat.model.KnownProviders
import io.tinychat.model.MessageStatus
import io.tinychat.model.pickVersion
import io.tinychat.model.PromptMode
import io.tinychat.model.RequestPayload
import io.tinychat.model.ResponsePayload
import io.tinychat.model.Role
import io.tinychat.model.SessionSearchHit
import io.tinychat.model.ThinkingEffort
import io.tinychat.model.buildSearchHit
import io.tinychat.net.context.ContextRebuilder
import io.tinychat.net.context.ContextTurn
import io.tinychat.net.context.ReminderBuilder
import io.tinychat.net.protocol.ChatCommand
import io.tinychat.net.protocol.DeepSeekMessagesProtocol
import io.tinychat.net.protocol.WireMessage
import io.tinychat.net.transport.ChatTransport
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

/**
 * 聊天仓库：把「协议 + 传输 + 归并 + 落库」串起来（M1-5/M1-7/M1-10）。
 *
 * 上下文按 02 §5.2 的规则重建（见 [ContextRebuilder]）；
 * 本轮用户元信息按 §5.4 生成（见 [ReminderBuilder]）。
 */
class ChatRepository(
    private val db: TinyChatDatabase,
    private val transport: ChatTransport,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val runner = ChatStreamRunner(db.messageDao(), db.fragmentDao(), clock)

    fun observeMessages(sessionId: String): Flow<List<MessageWithFragments>> =
        db.messageDao().observeWithFragments(sessionId)

    fun observeSessions(): Flow<List<SessionEntity>> = db.sessionDao().observeAll()

    suspend fun session(sessionId: String): SessionEntity? = db.sessionDao().byId(sessionId)

    /** 冷启动恢复用：最近活跃的一条未删除会话（只看 `updated_at` 倒序，不受置顶影响）。 */
    suspend fun latestSession(): SessionEntity? = db.sessionDao().latest()

    suspend fun renameSession(sessionId: String, title: String?) {
        db.sessionDao().rename(sessionId, title?.trim()?.takeIf { it.isNotEmpty() }, clock())
    }

    suspend fun setSessionPinned(sessionId: String, pinned: Boolean) {
        db.sessionDao().setPinned(sessionId, pinned, clock())
    }

    /** 软删；消息与碎片留给 M6 的清理任务，不在交互路径上做级联删除。 */
    suspend fun deleteSession(sessionId: String) {
        db.sessionDao().softDelete(sessionId, clock())
    }

    /**
     * 会话搜索：正文命中或标题命中都算，一条会话最多一条结果（正文命中优先）。
     *
     * 正文命中给「上下文 + 高亮」；只有标题命中时没有上下文，只回标题行。
     */
    suspend fun searchSessions(query: String, limit: Int = 30): List<SessionSearchHit> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()

        val hits = LinkedHashMap<String, SessionSearchHit>()

        db.fragmentDao().searchPayloads(needle).forEach { row ->
            if (hits.containsKey(row.sessionId)) return@forEach
            val content = when (val payload = FragmentPayloadCodec.decode(row.payload)) {
                is RequestPayload -> payload.content
                is ResponsePayload -> payload.content
                else -> null
            } ?: return@forEach
            buildSearchHit(row.sessionId, row.title, content, needle)?.let { hits[row.sessionId] = it }
        }

        // 只有标题命中的会话补一条（正文里找不到，就没有灰色上下文行）
        db.sessionDao().all()
            .filter { it.title?.contains(needle, ignoreCase = true) == true }
            .forEach { session -> hits.getOrPut(session.id) { titleHit(session.id, session.title, needle) } }

        return hits.values.take(limit)
    }

    private fun titleHit(sessionId: String, title: String?, query: String): SessionSearchHit {
        val titleText = title?.trim()?.takeIf { it.isNotEmpty() } ?: "新对话"
        val index = titleText.indexOf(query, ignoreCase = true)
        return if (index < 0) {
            SessionSearchHit(sessionId, titleText, "", "", "", matchedInTitle = true)
        } else {
            SessionSearchHit(
                sessionId = sessionId,
                title = titleText,
                before = titleText.substring(0, index),
                match = titleText.substring(index, index + query.length),
                after = titleText.substring(index + query.length),
                matchedInTitle = true,
            )
        }
    }

    suspend fun createSession(
        thinkingEnabled: Boolean = true,
        searchEnabled: Boolean = false,
        effort: ThinkingEffort? = ThinkingEffort.HIGH,
        systemPrompt: String? = null,
        systemMode: PromptMode = PromptMode.REPLACE,
    ): String {
        val id = UUID.randomUUID().toString()
        val now = clock()
        db.sessionDao().upsert(
            SessionEntity(
                id = id,
                createdAt = now,
                updatedAt = now,
                providerId = KnownProviders.deepSeek.id,
                model = KnownProviders.flash.id,
                thinkingEnabled = thinkingEnabled,
                searchEnabled = searchEnabled,
                effort = effort,
                systemPromptSnapshot = systemPrompt,
                systemPromptMode = systemMode,
            ),
        )
        return id
    }

    /** 启动时把上次被杀进程留下的 WIP 归为 INCOMPLETE（02 §4.6） */
    suspend fun recoverInterrupted() {
        db.messageDao().recoverWipAll()
    }

    /**
     * 发送一条用户消息并流式接收回复。**取消即停止**：
     * 上层 cancel 协程 → 传输层断流 → 状态落 INCOMPLETE、已生成内容保留。
     */
    /** 底栏切模型/强度/搜索：只改会话快照，不影响已有消息 */
    suspend fun updateSettings(
        sessionId: String,
        thinkingEnabled: Boolean,
        searchEnabled: Boolean,
        effort: ThinkingEffort?,
        model: String,
    ) {
        db.sessionDao().updateSettings(
            id = sessionId,
            thinking = thinkingEnabled,
            search = searchEnabled,
            effort = effort,
            model = model,
            now = clock(),
        )
    }


    fun observeSession(sessionId: String): Flow<SessionEntity?> = db.sessionDao().observeById(sessionId)

    suspend fun send(sessionId: String, text: String, apiKey: String): RunOutcome {
        val session = db.sessionDao().byId(sessionId) ?: error("会话不存在：$sessionId")
        val parentId = session.currentMessageId
            ?: db.messageDao().withFragments(sessionId).lastOrNull()?.message?.id
        return sendInternal(session, parentId, text, apiKey)
    }

    /**
     * 编辑重发（02 §5.1）：在原消息的**父节点**下新建一条 USER 兄弟。
     * 旧分支留在库里（M1-8 的版本切换要用），当前分支改走新的。
     */
    suspend fun editMessage(
        sessionId: String,
        messageId: Long,
        text: String,
        apiKey: String,
    ): RunOutcome {
        val session = db.sessionDao().byId(sessionId) ?: error("会话不存在：$sessionId")
        val original = db.messageDao().byId(messageId) ?: error("消息不存在：$messageId")
        return sendInternal(session, original.parentId, text, apiKey)
    }

    /**
     * 重新生成（M1-8）：在同一条提问下再挂一条 ASSISTANT 兄弟，旧回答留着当版本。
     *
     * 不新建用户消息，上下文回溯到那条提问为止，再按原样把提问追加回请求。
     */
    suspend fun regenerate(
        sessionId: String,
        assistantMessageId: Long,
        apiKey: String,
    ): RunOutcome {
        val session = db.sessionDao().byId(sessionId) ?: error("会话不存在：$sessionId")
        val original = db.messageDao().byId(assistantMessageId) ?: error("消息不存在：$assistantMessageId")
        val userMessageId = original.parentId ?: error("这条回答没有对应的提问，无法重新生成")
        val question = db.messageDao().byId(userMessageId) ?: error("找不到对应的提问：$userMessageId")

        val userText = db.fragmentDao().byMessage(userMessageId)
            .mapNotNull { (FragmentPayloadCodec.decode(it.payload) as? RequestPayload)?.content }
            .joinToString("")
        if (userText.isBlank()) error("这条回答对应的提问内容为空，无法重新生成")

        return streamAssistant(
            session = session,
            userMessageId = userMessageId,
            userText = userText,
            // 沿用当时那条提问的元信息快照，避免上下文里凭空多出一条新的 reminder
            reminder = question.reminderSnapshot ?: ReminderBuilder.build(date = LocalDate.now(), location = null),
            apiKey = apiKey,
        )
    }

    /**
     * 切换版本（M1-8）：把当前分支指针移到 [messageId] 的相邻兄弟分支，落到那条分支的叶子上。
     *
     * @return 新的叶子消息 id；只有一版时返回 null
     */
    suspend fun switchVersion(sessionId: String, messageId: Long, delta: Int): Long? {
        val nodes = db.messageDao().withFragments(sessionId).map {
            BranchNode(it.message.id, it.message.parentId, it.message.insertedAt)
        }
        val leaf = pickVersion(nodes, messageId, delta) ?: return null
        db.sessionDao().setCurrentMessage(sessionId, leaf.id, clock())
        return leaf.id
    }

    /**
     * 继续生成（M1-9）：把已停止/截断的回答接着写下去。
     *
     * 做法是把「提问 + 已生成的部分正文」作为 assistant 预填发过去，模型从断点续写；
     * 新内容仍落在同一条消息上（`ord` 递增追加），所以界面上是原地接续。
     *
     * 三个前置条件（02 §5.1）任一不满足就抛错，按钮本身在 UI 侧已经置灰：
     * 1. 该轮没调用过服务端工具（服务端会 400）；
     * 2. 状态是 INCOMPLETE（用户停止 / max_tokens / 中断）；
     * 3. 不含模板块、正文非空。
     *
     * 预填属于 assistant 末尾消息，按 Anthropic 规矩**不能**同时开思考，因此这一轮关掉思考。
     */
    suspend fun continueGeneration(
        sessionId: String,
        assistantMessageId: Long,
        apiKey: String,
    ): RunOutcome {
        val session = db.sessionDao().byId(sessionId) ?: error("会话不存在：$sessionId")
        val target = db.messageDao().byId(assistantMessageId) ?: error("消息不存在：$assistantMessageId")
        require(target.status == MessageStatus.INCOMPLETE) { "这条回答已经完整，不需要继续" }

        val userMessageId = target.parentId ?: error("这条回答没有对应的提问，无法继续生成")
        val question = db.messageDao().byId(userMessageId) ?: error("找不到对应的提问：$userMessageId")
        val fragments = db.fragmentDao().byMessage(assistantMessageId)

        require(fragments.none { it.type.startsWith("TOOL_") }) { "这一轮用过联网工具，不能继续生成" }
        require(fragments.none { it.type == "TEMPLATE_RESPONSE" }) { "这一轮是模板回复，不能继续生成" }

        val partial = fragments
            .filter { it.type == "RESPONSE" }
            .mapNotNull { (FragmentPayloadCodec.decode(it.payload) as? ResponsePayload)?.content }
            .joinToString("")
        require(partial.isNotBlank()) { "没有可继续的内容" }

        val userText = db.fragmentDao().byMessage(userMessageId)
            .mapNotNull { (FragmentPayloadCodec.decode(it.payload) as? RequestPayload)?.content }
            .joinToString("")

        val modelInfo = KnownProviders.deepSeek.model(session.model) ?: KnownProviders.flash
        val history = chainTurns(sessionId, question.parentId)
        val reminder = question.reminderSnapshot ?: ReminderBuilder.build(date = LocalDate.now(), location = null)

        val command = ChatCommand(
            capabilities = modelInfo.capabilities,
            model = session.model,
            systemPrompt = session.systemPromptSnapshot,
            systemMode = session.systemPromptMode,
            thinkingEnabled = false,
            effort = session.effort,
            searchEnabled = false,
            includeTitle = false,
            messages = ContextRebuilder.build(history, reminder) +
                WireMessage.text(WireMessage.ROLE_USER, userText) +
                WireMessage.text(WireMessage.ROLE_ASSISTANT, partial),
        )

        val request = DeepSeekMessagesProtocol.build(
            baseUrl = KnownProviders.deepSeek.baseUrl,
            apiKey = apiKey,
            command = command,
        )

        db.messageDao().setStatus(assistantMessageId, MessageStatus.WIP)
        val outcome = try {
            runner.run(assistantMessageId, request, transport)
        } catch (e: Throwable) {
            // 失败要把状态退回「未完成」，否则界面一直显示生成中，还挡住下一次继续
            runCatching { db.messageDao().setStatus(assistantMessageId, MessageStatus.INCOMPLETE) }
            throw e
        }
        db.sessionDao().touch(sessionId, clock())
        return outcome
    }

    private suspend fun sendInternal(
        session: SessionEntity,
        parentId: Long?,
        text: String,
        apiKey: String,
    ): RunOutcome {
        val sessionId = session.id
        val now = clock()

        // 本轮的用户元信息（02 §5.4）；地点暂未接设置项，留空但保留逗号
        val reminder = ReminderBuilder.build(date = LocalDate.now(), location = null)

        val userId = db.messageDao().insert(
            MessageEntity(
                sessionId = sessionId,
                parentId = parentId,
                role = Role.USER,
                status = MessageStatus.FINISHED,
                thinkingEnabled = session.thinkingEnabled,
                searchEnabled = session.searchEnabled,
                insertedAt = now,
                reminderSnapshot = reminder,
            ),
        )
        db.fragmentDao().insert(Fragment(messageId = userId, ord = 0, payload = RequestPayload(text)).toEntity())

        return streamAssistant(
            session = session,
            userMessageId = userId,
            userText = text,
            reminder = reminder,
            apiKey = apiKey,
            assistantInsertedAt = now + 1,
        )
    }

    /**
     * 在一条**已存在的**提问下流式生成回答：发送与重新生成共用这一段。
     */
    private suspend fun streamAssistant(
        session: SessionEntity,
        userMessageId: Long,
        userText: String,
        reminder: String?,
        apiKey: String,
        assistantInsertedAt: Long = clock(),
    ): RunOutcome {
        val sessionId = session.id
        val modelInfo = KnownProviders.deepSeek.model(session.model) ?: KnownProviders.flash

        val assistantId = db.messageDao().insert(
            MessageEntity(
                sessionId = sessionId,
                parentId = userMessageId,
                role = Role.ASSISTANT,
                status = MessageStatus.WIP,
                thinkingEnabled = session.thinkingEnabled,
                searchEnabled = session.searchEnabled,
                insertedAt = assistantInsertedAt,
                model = session.model,
                effort = session.effort,
            ),
        )
        db.sessionDao().setCurrentMessage(sessionId, assistantId, clock())

        // 上下文只取**当前分支**：到这条提问的父节点为止，再追加本次提问
        val question = db.messageDao().byId(userMessageId)
        val history = chainTurns(sessionId, question?.parentId)

        val command = ChatCommand(
            capabilities = modelInfo.capabilities,
            model = session.model,
            systemPrompt = session.systemPromptSnapshot,
            systemMode = session.systemPromptMode,
            thinkingEnabled = session.thinkingEnabled,
            effort = session.effort,
            searchEnabled = session.searchEnabled,
            includeTitle = session.title == null,
            messages = ContextRebuilder.build(history, reminder) +
                WireMessage.text(WireMessage.ROLE_USER, userText),
        )

        val request = DeepSeekMessagesProtocol.build(
            baseUrl = KnownProviders.deepSeek.baseUrl,
            apiKey = apiKey,
            command = command,
        )

        val outcome = runner.run(assistantId, request, transport)

        // 服务端标题只在会话还没有标题时采用（02 §5.3）
        if (outcome.title != null && session.title == null) {
            db.sessionDao().rename(sessionId, outcome.title, clock())
        }
        db.sessionDao().touch(sessionId, clock())
        return outcome
    }

    /** 从 leaf 往上回溯成 root → leaf 的顺序 */
    private suspend fun chainTurns(sessionId: String, leafId: Long?): List<ContextTurn> {
        if (leafId == null) return emptyList()
        val all = db.messageDao().withFragments(sessionId).associateBy { it.message.id }
        val path = ArrayDeque<MessageWithFragments>()
        var cursor: Long? = leafId
        val guard = HashSet<Long>()
        while (cursor != null && guard.add(cursor)) {
            val node = all[cursor] ?: break
            path.addFirst(node)
            cursor = node.message.parentId
        }
        return path.map { entry ->
            ContextTurn(
                message = entry.message.toDomain(),
                fragments = entry.orderedFragments.map { it.toDomain() },
            )
        }
    }
}
