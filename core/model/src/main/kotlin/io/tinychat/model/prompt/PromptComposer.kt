package io.tinychat.model.prompt

/**
 * 「是否发送官方提示词」与「我的内容」怎么拼成真正要发的那句 system（决策 45）。
 *
 * 四种状态全部收在这里，UI 与仓库都不许再各写一份：
 * | 开关 | 内容 | 结果 |
 * |---|---|---|
 * | 开 | 空 | 官方提示词 |
 * | 开 | 有 | 官方提示词 + 空行 + 我的内容 |
 * | 关 | 有 | 只有我的内容 |
 * | 关 | 空 | 不发送（null） |
 */
object PromptComposer {

    fun compose(includeOfficial: Boolean, custom: String): String? {
        val text = custom.trim()
        return when {
            includeOfficial && text.isEmpty() -> PromptSeeds.OFFICIAL_SYSTEM
            includeOfficial -> PromptSeeds.OFFICIAL_SYSTEM + "\n\n" + text
            text.isEmpty() -> null
            else -> text
        }
    }
}
