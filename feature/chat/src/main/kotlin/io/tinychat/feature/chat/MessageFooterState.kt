package io.tinychat.feature.chat

import io.tinychat.model.MessageStatus

/**
 * 助手消息底部「操作栏」的可见性（M2-9），官方版式固定两行：
 *
 * ```
 * 第 1 行：[复制] [朗读] [分享] …………………… [重新生成]
 * 第 2 行：[‹ 2/3 ›] …………………………………… [继续生成]
 * ```
 *
 * 规则：
 * - 生成中（WIP）整栏不出：流式中途点重新生成会打断，且内容还在变；
 * - 失败/超时态整栏不出：错误态自己在正文下方给「重试」；
 * - 只要已经有正文，复制/朗读/分享就给 —— 手动停止（INCOMPLETE）也算，
 *   因为那部分内容是可以复制、可以朗读的；
 * - 「思考到一半就被掐断、正文为空」只有重新生成可点（复制/朗读/分享没有对象，
 *   继续生成也没有可接的半句），这就是当初那条硬规则的适用面。
 */
internal data class MessageFooterState(
    /** 左侧三件：复制 / 朗读 / 分享 */
    val showActions: Boolean,
    /** 右下角重新生成 */
    val showRegenerate: Boolean,
    /** 左下角 `‹ 2/3 ›` */
    val showVersionSwitcher: Boolean,
    /** 右下角继续生成 */
    val showContinue: Boolean,
) {
    /** 第一行有东西（右侧靠重新生成撑着也要出这一行，否则图标会掉到第二行） */
    val showFirstRow: Boolean get() = showActions || showRegenerate

    /** 第二行有东西 */
    val showSecondRow: Boolean get() = showVersionSwitcher || showContinue

    val visible: Boolean get() = showFirstRow || showSecondRow
}

internal fun resolveFooterState(
    status: MessageStatus,
    hasText: Boolean,
    versionCount: Int,
    /** 调用方判断：这条是不是最新一条（只有最新一条给重新生成 / 继续生成） */
    isLatest: Boolean,
    canContinue: Boolean,
): MessageFooterState {
    val blocked = status == MessageStatus.WIP ||
        status == MessageStatus.FAILED ||
        status == MessageStatus.TIMEOUT
    return MessageFooterState(
        showActions = !blocked && hasText,
        showRegenerate = !blocked && isLatest,
        showVersionSwitcher = !blocked && versionCount > 1,
        showContinue = !blocked && isLatest && canContinue,
    )
}
