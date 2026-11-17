package io.tinychat.data.chat

import io.tinychat.data.db.FragmentDao
import io.tinychat.data.db.MessageDao
import io.tinychat.data.db.toEntity
import io.tinychat.model.Fragment
import io.tinychat.model.FragmentPayloadCodec
import io.tinychat.model.FragmentType
import io.tinychat.model.MessageStatus
import io.tinychat.model.Usage
import io.tinychat.net.MessageAssembler
import io.tinychat.net.StopReason
import io.tinychat.net.transport.ChatException
import io.tinychat.net.transport.ChatTransport
import io.tinychat.net.protocol.PreparedRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/** 一次流式生成的结果。 */
data class RunOutcome(
    val messageId: Long,
    val status: MessageStatus,
    val usage: Usage?,
    val stopReason: StopReason?,
    val title: String?,
    val error: ChatException? = null,
)

/**
 * 流式落库与状态流转（M1-5）。
 *
 * 三条硬规则：
 * 1. **先落行再追加**：块一开始就插入空碎片行，后续原地 UPDATE，进程被杀也不会丢半句话；
 * 2. **节流写库**：默认 200ms 一次，逐 delta 写会把 SQLite 打满；
 * 3. **状态只由结局决定**：FINISHED / INCOMPLETE / CONTENT_FILTER / FAILED，
 *    永远不留 WIP（取消也要落 INCOMPLETE）。
 */
class ChatStreamRunner(
    private val messageDao: MessageDao,
    private val fragmentDao: FragmentDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val flushIntervalMillis: Long = 200,
) {

    suspend fun run(
        assistantMessageId: Long,
        request: PreparedRequest,
        transport: ChatTransport,
    ): RunOutcome {
        val assembler = MessageAssembler(clock)
        val rowIds = HashMap<Int, Long>()
        val written = HashMap<Int, String>()
        var lastFlush = 0L

        suspend fun flush() {
            val fragments = assembler.fragments()
            for (fragment in fragments) {
                val json = FragmentPayloadCodec.encode(fragment.payload)
                if (written[fragment.ord] == json) continue
                val rowId = rowIds[fragment.ord]
                if (rowId == null) {
                    rowIds[fragment.ord] = fragmentDao.insert(fragment.forMessage(assistantMessageId).toEntity())
                } else {
                    fragmentDao.updateTypeAndPayload(rowId, fragment.payload.typeWire, json)
                }
                written[fragment.ord] = json
            }
            lastFlush = clock()
        }

        try {
            transport.stream(request).collect { event ->
                assembler.onFrame(event.name, event.data)
                if (clock() - lastFlush >= flushIntervalMillis) flush()
            }
            assembler.finishUnpairedTools()
            flush()
            return finalize(assistantMessageId, assembler)
        } catch (e: ChatException) {
            runCatching { flush() }
            messageDao.setStatus(assistantMessageId, MessageStatus.FAILED)
            return RunOutcome(
                messageId = assistantMessageId,
                status = MessageStatus.FAILED,
                usage = assembler.usage,
                stopReason = assembler.stopReason,
                title = assembler.title,
                error = e,
            )
        } catch (e: CancellationException) {
            // 用户点了停止：已生成的内容必须保留
            runCatching { flush() }
            messageDao.setStatus(assistantMessageId, MessageStatus.INCOMPLETE)
            throw e
        }
    }

    private suspend fun finalize(messageId: Long, assembler: MessageAssembler): RunOutcome {
        val stopReason = assembler.stopReason
        val status = when {
            assembler.error != null -> MessageStatus.FAILED
            // 拒答：按协议丢弃已下发的思考与正文，只保留工具块
            stopReason == StopReason.REFUSAL -> MessageStatus.CONTENT_FILTER
            stopReason == StopReason.MAX_TOKENS -> MessageStatus.INCOMPLETE
            assembler.sawMessageStop && stopReason != null -> MessageStatus.FINISHED
            else -> MessageStatus.INCOMPLETE
        }

        if (status == MessageStatus.CONTENT_FILTER) {
            fragmentDao.deleteByTypes(
                messageId,
                listOf(FragmentType.THINK.wire, FragmentType.RESPONSE.wire),
            )
        }

        val usage = assembler.usage
        val entity = messageDao.byId(messageId)
        if (entity != null) {
            messageDao.update(
                entity.copy(
                    status = status,
                    promptTokens = usage?.promptTokens,
                    cacheHitTokens = usage?.cacheHitTokens,
                    completionTokens = usage?.completionTokens,
                    thinkingTokens = usage?.thinkingTokens,
                    safetyTokens = usage?.safetyTokens,
                    searchCalls = usage?.searchCalls,
                    fetchCalls = usage?.fetchCalls,
                    imageSearchCalls = usage?.imageSearchCalls,
                    accumulatedTokenUsage = usage?.contextUsed ?: entity.accumulatedTokenUsage,
                    errorKind = if (status == MessageStatus.FAILED) {
                        assembler.error?.let { io.tinychat.model.ErrorKind.STREAM }
                    } else {
                        null
                    },
                    errorMessage = assembler.error?.message,
                ),
            )
        }

        return RunOutcome(
            messageId = messageId,
            status = status,
            usage = usage,
            stopReason = stopReason,
            title = assembler.title,
        )
    }

    private fun Fragment.forMessage(messageId: Long): Fragment = copy(messageId = messageId)
}
