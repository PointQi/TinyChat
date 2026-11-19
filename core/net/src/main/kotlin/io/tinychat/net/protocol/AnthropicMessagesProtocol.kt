package io.tinychat.net.protocol

import io.tinychat.model.PromptMode
import io.tinychat.model.ProviderProtocol

/**
 * 通用 Anthropic Messages 协议（第三方 `/v1/messages`）。
 *
 * 与官方 Messages 的差别：
 * - 路径是 `/v1/messages`（官方是 `/anthropic/v1/messages`）；
 * - 除 `x-api-key` 外要带 `anthropic-version`；
 * - **不发** `tools` / `safety_check` / `title` / `thinking` / `output_config`：这些是官方扩展，
 *   第三方拒收或语义不同；
 * - `APPEND` 模式不拼官方那句「该助手为 DeepSeek」——第三方服务商不该被冒名。
 */
object AnthropicMessagesProtocol {

    const val VERSION = "2023-06-01"

    fun endpoint(baseUrl: String): String {
        val base = baseUrl.trim().trimEnd('/')
        require(base.isNotEmpty()) { "API 地址不能为空" }
        return if (base.endsWith("/v1")) "$base/messages" else "$base/v1/messages"
    }

    fun build(
        baseUrl: String,
        apiKey: String,
        command: ChatCommand,
        extraHeaders: Map<String, String> = emptyMap(),
    ): PreparedRequest {
        require(command.model.isNotBlank()) { "model 不能为空" }
        require(command.messages.isNotEmpty()) { "messages 不能为空" }

        val request = MessagesRequest(
            model = command.model,
            system = resolveSystem(command),
            thinking = null,
            outputConfig = null,
            tools = null,
            safetyCheck = null,
            title = null,
            messages = command.messages,
            stream = true,
        )
        return PreparedRequest(
            url = endpoint(baseUrl),
            headers = buildMap {
                put("Content-Type", "application/json")
                put("x-api-key", apiKey)
                put("anthropic-version", VERSION)
                extraHeaders.forEach { (name, value) -> put(name, value) }
            },
            bodyJson = DeepSeekMessagesProtocol.bodyJson.encodeToString(MessagesRequest.serializer(), request),
            protocol = ProviderProtocol.ANTHROPIC_MESSAGES,
        )
    }

    private fun resolveSystem(command: ChatCommand): String? {
        val snapshot = command.systemPrompt?.takeIf { it.isNotBlank() }
        return when (command.systemMode) {
            PromptMode.CLEAR -> null
            else -> snapshot
        }
    }
}
