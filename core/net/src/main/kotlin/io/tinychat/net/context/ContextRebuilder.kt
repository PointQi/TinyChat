package io.tinychat.net.context

import io.tinychat.model.Fragment
import io.tinychat.model.Message
import io.tinychat.model.MessageStatus
import io.tinychat.model.ResponsePayload
import io.tinychat.model.Role
import io.tinychat.model.TemplateResponsePayload
import io.tinychat.net.protocol.WireMessage

/** 上下文构建的一段：消息 + 它的碎片（已按 ord 排好）。 */
data class ContextTurn(
    val message: Message,
    val fragments: List<Fragment>,
)

/**
 * 上下文重建（M1-10）。**这是最容易出错的地方**，规则逐条对齐 02 §5.2：
 *
 * 1. 只沿当前分支线性回溯（由调用方保证顺序：root → leaf）；
 * 2. 只保留「USER + 可用 ASSISTANT」成对的轮次；孤立 USER、不可用 ASSISTANT 整轮跳过；
 * 3. 「可用」= 状态属于 FINISHED/INCOMPLETE、不是拒答、不含服务端模板块、正文非空；
 * 4. ASSISTANT 只回传**正文**；历史思考一律不回传（继续生成是例外，见 M1-9）；
 * 5. 每个 USER 前，若它的 reminder 快照与本轮不同，插入一条 `latest_reminder`；
 * 6. 末尾的本轮 USER 由调用方追加（含附件块，文件在前、文本在后）。
 *
 * **已知缺口（记在 03 §7）**：`READ_LINK` 的 `signature` 回传还没做 —— 需要先把
 * `server_tool_use` + 结果块按 `tool_use_id` 存下来，当前碎片只存了结果侧数据。
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

                    val userText = user.userText() ?: run {
                        pendingUser = null
                        continue
                    }

                    // 规则 5：reminder 快照不同才插
                    val snapshot = user.message.reminderSnapshot
                    if (currentReminder != null && snapshot != currentReminder) {
                        out += WireMessage.text(WireMessage.ROLE_REMINDER, currentReminder)
                    }
                    out += WireMessage.text(WireMessage.ROLE_USER, userText)
                    out += WireMessage.text(WireMessage.ROLE_ASSISTANT, assistantText)

                    pendingUser = null
                }
            }
        }

        return out
    }

    private fun ContextTurn.userText(): String? {
        val text = fragments
            .filter { it.payload is io.tinychat.model.RequestPayload }
            .joinToString("") { (it.payload as io.tinychat.model.RequestPayload).content }
        return text.takeIf { it.isNotBlank() }
    }

    /** 规则 3 + 4：可用才返回正文 */
    private fun ContextTurn.usableText(): String? {
        val message = message
        if (!message.status.isUsableInContext) return null

        val hasTemplate = fragments.any { it.payload is TemplateResponsePayload }
        if (hasTemplate) return null

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
}
