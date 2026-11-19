package io.tinychat.net

import io.tinychat.model.Fragment
import io.tinychat.model.Usage

/**
 * 流 → 碎片的归并器（M8-1 抽出的协议边界）。
 *
 * 两种协议（Messages / OpenAI Chat）产出的对外形状完全一致：
 * 碎片序列 + 用量 + 终止原因 + 错误 + 标题。上层的落库/状态机（`ChatStreamRunner`）不关心
 * 哪家协议，只消费这个接口。
 */
interface StreamAssembler {

    /** 协议给的终止原因；还在流中时为 `null` */
    val stopReason: StopReason?

    /** 服务端下发的会话标题（只有 Messages 协议会给）；没有就是 `null` */
    val title: String?

    val usage: Usage?

    /** 流中主动下发的错误（`error` 帧）；没有就是 `null` */
    val error: StreamError?

    /** 是否见到过协议层的「结束」标记（Messages: `message_stop`；OpenAI: `[DONE]`） */
    val sawMessageStop: Boolean

    /** Messages 协议的思考签名，续写要回传；OpenAI 链路恒 `null` */
    val thinkingSignature: String?

    fun onFrame(name: String?, data: String)

    /** 当前已归并出的碎片（按 ord 升序） */
    fun fragments(): List<Fragment>

    /** 流结束时把没配对的工具块落成 WIP（只有 Messages 协议用得到） */
    fun finishUnpairedTools()

    /** 本轮是否用过服务端工具（继续生成的前置条件，只有官方有） */
    fun usedServerTools(): Boolean
}
