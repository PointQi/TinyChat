package io.tinychat.model

/**
 * 系统提示词预设（决策 9 / 12 / 13）。
 *
 * - [DEFAULT] 是官方原文，**只读、不可删改**；「恢复默认」一键回到这里。
 * - [CUSTOM] 表示用户自己改过（与任何内置预设都不匹配）。
 * - 全局改动不影响已有会话：会话创建时把提示词快照写入会话（决策 10）。
 */
enum class PromptPreset(val id: String, val displayName: String) {
    DEFAULT("default", "默认"),
    NONE("none", "无提示词"),
    ROLEPLAY("roleplay", "角色扮演"),
    TRANSLATE("translate", "翻译润色"),
    TUTOR("tutor", "学习辅导"),
    CUSTOM("custom", "自定义"),
    ;

    val isBuiltIn: Boolean get() = this != CUSTOM

    companion object {
        fun fromId(id: String): PromptPreset = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
