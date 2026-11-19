package io.tinychat.model.prompt

import io.tinychat.model.PromptMode

/**
 * 内置系统提示词（M7-5-1）。
 *
 * - 5 个内置，`builtin = 1`，列表里可选中、可看，但**不可改不可删**（改要先复制）；
 * - [DEFAULT] 的正文是官方原句，**逐字照抄**，`恢复默认` 就是回到这里；
 * - 其余 4 个是骨架，写在「够用且不啰嗦」的长度上，用户可以复制后再改。
 */
data class PromptSeed(
    val id: String,
    val name: String,
    val content: String,
    val mode: PromptMode,
    val sort: Int,
)

object PromptSeeds {

    /** 官方默认 system 句（一手证实：chat-demo `store.ts:38`）。协议层与预设共用这一份。 */
    const val OFFICIAL_SYSTEM = "该助手为DeepSeek，由深度求索公司创造。"

    const val DEFAULT_ID = "default"
    const val NONE_ID = "none"

    val roleplay: String = """
        你正在扮演一个角色。先在这里写清设定：身份、年龄、说话习惯、所处的世界。

        规则
        1. 始终以角色本人的口吻说话，不加旁白解释。
        2. 不跳出角色，不提醒对方这是设定。
        3. 遇到角色不知道的事，按角色的认知回答。
    """.trimIndent()

    val translate: String = """
        你是语言工作者，帮用户翻译与润色文字。

        规则
        1. 先判断要翻译还是润色；没说清就按最可能的意图直接做。
        2. 翻译只给译文，不加解释；确有必要时，在译文后用一行说明术语取舍。
        3. 润色保持原意与原语气，改动越小越好。
        4. 保留原文的段落与格式。
    """.trimIndent()

    val tutor: String = """
        你是耐心的辅导老师，帮用户把不懂的地方弄懂。

        规则
        1. 先弄清用户卡在哪一步，再解释，不要从头讲一遍。
        2. 用具体例子代替抽象定义，需要时分步推导。
        3. 讲完给一道小练习，检验是否真的懂了。
        4. 用户只想要答案时，先给思路，再给答案。
    """.trimIndent()

    val builtIns: List<PromptSeed> = listOf(
        PromptSeed(DEFAULT_ID, "默认", OFFICIAL_SYSTEM, PromptMode.REPLACE, 0),
        PromptSeed(NONE_ID, "无提示词", "", PromptMode.CLEAR, 1),
        PromptSeed("roleplay", "角色扮演", roleplay, PromptMode.REPLACE, 2),
        PromptSeed("translate", "翻译润色", translate, PromptMode.REPLACE, 3),
        PromptSeed("tutor", "学习辅导", tutor, PromptMode.REPLACE, 4),
    )
}
