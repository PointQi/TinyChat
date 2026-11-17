package io.tinychat.data.chat

import io.tinychat.data.db.MessageEntity
import io.tinychat.data.db.MessageWithFragments
import io.tinychat.data.db.SessionEntity
import io.tinychat.data.db.TinyChatDatabase
import io.tinychat.data.db.toDomain
import io.tinychat.data.db.toEntity
import io.tinychat.model.Fragment
import io.tinychat.model.KnownProviders
import io.tinychat.model.MessageStatus
import io.tinychat.model.PromptMode
import io.tinychat.model.RequestPayload
import io.tinychat.model.Role
import io.tinychat.model.ThinkingEffort
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

    suspend fun send(sessionId: String, text: String, apiKey: String): RunOutcome {
        val session = db.sessionDao().byId(sessionId) ?: error("会话不存在：$sessionId")
        val modelInfo = KnownProviders.deepSeek.model(session.model) ?: KnownProviders.flash
        val history = db.messageDao().withFragments(sessionId)
        val now = clock()

        // 本轮的用户元信息（02 §5.4）；地点暂未接设置项，留空但保留逗号
        val reminder = ReminderBuilder.build(date = LocalDate.now(), location = null)

        val parentId = session.currentMessageId ?: history.lastOrNull()?.message?.id
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

        val assistantId = db.messageDao().insert(
            MessageEntity(
                sessionId = sessionId,
                parentId = userId,
                role = Role.ASSISTANT,
                status = MessageStatus.WIP,
                thinkingEnabled = session.thinkingEnabled,
                searchEnabled = session.searchEnabled,
                insertedAt = now + 1,
                model = session.model,
                effort = session.effort,
            ),
        )
        db.sessionDao().setCurrentMessage(sessionId, assistantId, now)

        val command = ChatCommand(
            capabilities = modelInfo.capabilities,
            model = session.model,
            systemPrompt = session.systemPromptSnapshot,
            systemMode = session.systemPromptMode,
            thinkingEnabled = session.thinkingEnabled,
            effort = session.effort,
            searchEnabled = session.searchEnabled,
            includeTitle = session.title == null,
            messages = ContextRebuilder.build(
                branch = history.map { entry ->
                    ContextTurn(
                        message = entry.message.toDomain(),
                        fragments = entry.orderedFragments.map { it.toDomain() },
                    )
                },
                currentReminder = reminder,
            ) + WireMessage.text(WireMessage.ROLE_USER, text),
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

}
