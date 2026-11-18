package io.tinychat.net.context

import io.tinychat.model.Fragment
import io.tinychat.model.Message
import io.tinychat.model.MessageStatus
import io.tinychat.model.ReadLinkPayload
import io.tinychat.model.RequestPayload
import io.tinychat.model.ResponsePayload
import io.tinychat.model.Role
import io.tinychat.model.TemplateResponsePayload
import io.tinychat.net.protocol.DeepSeekMessagesProtocol
import io.tinychat.net.protocol.WireMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * 历史轮次里的一个附件：只带重建请求需要的东西。
 *
 * `remoteId` 为 null = 没上传成功（导入的老数据、或上传失败），**不进请求**；
 * 若该轮正文也为空，整轮会被丢弃（与「孤立 USER」同处理）。
 */
data class HistoryAttachment(
    val remoteId: String?,
    val isImage: Boolean,
) {
    /** 能不能进请求 */
    val usable: Boolean get() = !remoteId.isNullOrBlank()
}

/** 上下文构建的一段：消息 + 它的碎片（已按 ord 排好）+ 它的附件。 */
data class ContextTurn(
    val message: Message,
    val fragments: List<Fragment>,
    val attachments: List<HistoryAttachment> = emptyList(),
)

/**
 * 上下文重建（M1-10）。**这是最容易出错的地方**，规则逐条对齐 02 §5.2：
 *
 * 1. 只沿当前分支线性回溯（由调用方保证顺序：root → leaf）；
 * 2. 只保留「USER + 可用 ASSISTANT」成对的轮次；孤立 USER、不可用 ASSISTANT 整轮跳过；
 * 3. 「可用」= 状态属于 FINISHED/INCOMPLETE、不是拒答、不含服务端模板块、正文非空；
 * 4. ASSISTANT 回传**正文**；历史思考一律不回传（继续生成是例外，见 M1-9），
 *    且 `read_link` 要把**调用块 + 结果块（含 signature）**成对回传，否则多轮丢网页上下文；
 * 5. 每个 USER 前，若它的 reminder 快照与本轮不同，插入一条 `latest_reminder`；
 * 6. 末尾的本轮 USER 由调用方追加（含附件块，文件在前、文本在后）。
 */
object ContextRebuilder {

    fun build(branch: List<ContextTurn>, currentReminder: String?): List<WireMessage> {
        val out = ArrayList<WireMessage>(branch.size)

        var pendingUser: ContextTurn? = null

        for (turn in branch) {
            when (turn.message.role) {
                Role.USER -> {
                    // 上一轮 USER 没等到可用回复 —— 整轮丢弃（规则 2）
                    pendingUser = turn
                }

                Role.ASSISTANT -> {
                    val user = pendingUser ?: continue
                    val assistantText = turn.usableText() ?: continue

                    val userText = user.userText().orEmpty()
                    val attachments = user.usableAttachments()
                    // 正文和附件都空 = 这一轮没有内容可回传
                    if (userText.isBlank() && attachments.isEmpty()) {
                        pendingUser = null
                        continue
                    }

                    // 规则 5：reminder 快照不同才插
                    val snapshot = user.message.reminderSnapshot
                    if (currentReminder != null && snapshot != currentReminder) {
                        out += WireMessage.text(WireMessage.ROLE_REMINDER, currentReminder)
                    }
                    out += userMessage(userText, attachments)
                    out += turn.assistantMessage(assistantText)
                    pendingUser = null
                }
            }
        }

        return out
    }

    /**
     * 规则 6 / 附件：**文件与图片在前、文本在后**（03 §6.5 的示例顺序）。
     *
     * 没有可用附件时退化成纯文本消息，报文与 M4 之前完全一致 —— 老会话不会因为
     * 这次改动多出空 `content` 数组。
     */
    fun userMessage(text: String, attachments: List<HistoryAttachment>): WireMessage {
        val usable = attachments.filter { it.usable }
        if (usable.isEmpty()) return WireMessage.text(WireMessage.ROLE_USER, text)

        val blocks = buildList {
            usable.forEach { attachment ->
                val id = attachment.remoteId ?: return@forEach
                add(
                    if (attachment.isImage) {
                        DeepSeekMessagesProtocol.imageBlock(id)
                    } else {
                        DeepSeekMessagesProtocol.fileBlock(id)
                    },
                )
            }
            if (text.isNotBlank()) {
                add(
                    buildJsonObject {
                        put("type", JsonPrimitive("text"))
                        put("text", JsonPrimitive(text))
                    },
                )
            }
        }
        return WireMessage.blocks(WireMessage.ROLE_USER, blocks)
    }

    private fun ContextTurn.usableAttachments(): List<HistoryAttachment> =
        attachments.filter { it.usable }

    /**
     * 规则 4：没有 `read_link` 时就是一条纯文本消息；有时改成块数组
     * `[正文, 调用块, 结果块, …]`。
     */
    private fun ContextTurn.assistantMessage(text: String): WireMessage {
        val links = fragments.mapNotNull { it.payload as? ReadLinkPayload }.filter { it.results != null }
        if (links.isEmpty()) return WireMessage.text(WireMessage.ROLE_ASSISTANT, text)

        val blocks = buildList {
            add(
                buildJsonObject {
                    put("type", JsonPrimitive("text"))
                    put("text", JsonPrimitive(text))
                },
            )
            links.forEach { payload -> addAll(readLinkBlocks(payload)) }
        }
        return WireMessage.blocks(WireMessage.ROLE_ASSISTANT, blocks)
    }

    private fun ContextTurn.userText(): String? {
        val text = fragments
            .filter { it.payload is RequestPayload }
            .joinToString("") { (it.payload as RequestPayload).content }
        return text.takeIf { it.isNotBlank() }
    }

    /** 规则 3 + 4：可用才返回正文 */
    private fun ContextTurn.usableText(): String? {
        if (!message.status.isUsableInContext) return null
        if (fragments.any { it.payload is TemplateResponsePayload }) return null

        val text = fragments
            .filter { it.payload is ResponsePayload }
            .joinToString("") { (it.payload as ResponsePayload).content }

        return text.takeIf { it.isNotBlank() }
    }

    /** 供调用方判断：这条回复能不能进上下文（M1-10 规则 3 的对外形态） */
    fun isUsable(message: Message, fragments: List<Fragment>): Boolean {
        if (!message.status.isUsableInContext) return false
        if (fragments.any { it.payload is TemplateResponsePayload }) return false
        return fragments.any { (it.payload as? ResponsePayload)?.content?.isNotBlank() == true }
    }

    /** 终止状态是否算「正常结束」 */
    fun isFinished(status: MessageStatus): Boolean = status == MessageStatus.FINISHED

    /**
     * 一条 `read_link` 的一对块：调用块原样回传，结果块补回 `tool_use_id` 与 `signature`。
     *
     * 结果碎片里存的是 content 本体 + signature（见 MessageAssembler），所以要拆开重装；
     * 失败结果没有 signature，只回传结果块（服务端认这个形状）。
     */
    private fun readLinkBlocks(payload: ReadLinkPayload): List<JsonObject> {
        val result = payload.results?.firstOrNull() as? JsonObject ?: return emptyList()
        val signature = (result["signature"] as? JsonPrimitive)?.contentOrNull
        val callId = (payload.call?.get("id") as? JsonPrimitive)?.contentOrNull
        val toolUseId = callId ?: (result["tool_use_id"] as? JsonPrimitive)?.contentOrNull
        val kind = (payload.call?.get("kind") as? JsonPrimitive)?.contentOrNull

        val callBlock = payload.call?.let { call ->
            buildJsonObject { call.forEach { (key, value) -> put(key, value) } }
        }

        val content = JsonObject(result.filterKeys { it != "signature" && it != "tool_use_id" })

        val resultBlock = buildJsonObject {
            put("type", JsonPrimitive("web_fetch_tool_result"))
            if (toolUseId != null) put("tool_use_id", JsonPrimitive(toolUseId))
            put("content", content)
            if (kind != null) put("kind", JsonPrimitive(kind))
            if (signature != null) put("signature", JsonPrimitive(signature))
        }

        return listOfNotNull(callBlock, resultBlock)
    }
}
