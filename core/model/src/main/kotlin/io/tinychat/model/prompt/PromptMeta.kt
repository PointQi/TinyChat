package io.tinychat.model.prompt

/**
 * 用户元信息（喂 `{{location}}` / `{{language}}` 与 `latest_reminder`）。
 *
 * 刻意只放这两个：它们会变化、又必须每次发送时现取；
 * 其余变量（日期、时间、模型）由调用方当场给。
 */
data class PromptMeta(
    val location: String = "",
    val language: String = "中文",
)
