package io.tinychat.model

/**
 * 服务商实例的命名（P 阶段，决策 48）。
 *
 * 目录里的预设只是模板：同一家可以添加多次（两块不同的 Key、两个网关、两个地区）。
 * 所以「身份」不再等于预设 id —— 预设 id 只用来预填，实例 id 在这里去重生成。
 */
object ProviderNaming {

    /**
     * 生成一个没被占用的实例 id：`opencode-go` → `opencode-go-2` → `opencode-go-3`。
     *
     * 保留预设 id 作为第一个实例的 id 是有意的：老用户的 `deepseek` 行、按 id 存的密钥、
     * 会话里的 `provider_id` 都不需要迁移。
     */
    fun uniqueId(base: String, taken: Set<String>): String {
        val clean = base.trim().ifBlank { "provider" }
        if (clean !in taken) return clean
        var index = 2
        while ("$clean-$index" in taken) index++
        return "$clean-$index"
    }

    /** 显示名同理：`OpenCode Go` → `OpenCode Go 2`。用户随时可以自己改。 */
    fun uniqueName(base: String, taken: Set<String>): String {
        val clean = base.trim().ifBlank { "未命名服务商" }
        if (clean !in taken) return clean
        var index = 2
        while ("$clean $index" in taken) index++
        return "$clean $index"
    }
}
