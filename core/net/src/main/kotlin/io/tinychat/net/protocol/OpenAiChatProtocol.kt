package io.tinychat.net.protocol

import io.tinychat.model.PromptMode
import io.tinychat.model.ProviderProtocol
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * OpenAI Chat Completions 协议（M8-1）。
 *
 * 只发文本：`messages[].content` 一律字符串。图片走 `image_url` 是 v1.1 的事（用户拍板先不做）。
 *
 * 与官方 Messages 链路的三处差别，都在这里收口：
 * 1. 端点与鉴权（Bearer，而不是 `x-api-key`）；
 * 2. 没有 `thinking` / `tools` / `safety_check` / `title` 这些扩展字段，**标题本地生成**；
 * 3. `latest_reminder` 是 DeepSeek 的自定义角色，这里拼进 system，不伪造未知角色。
 */
object OpenAiChatProtocol {

    /**
     * 地址归一：用户可能填 `https://x/v1`、`https://x/v1/` 或只给域名。
     * 已以 `/v1` 结尾就不重复拼（02 §12.6 的地址默认值就是这个形状）。
     */
    fun endpoint(baseUrl: String): String {
        val base = baseUrl.trim().trimEnd('/')
        require(base.isNotEmpty()) { "API 地址不能为空" }
        return if (base.endsWith("/v1")) "$base/chat/completions" else "$base/v1/chat/completions"
    }

    fun build(
        baseUrl: String,
        apiKey: String,
        command: ChatCommand,
        /** 服务商额外头（OpenCode 的 `x-opencode-session`、UA） */
        extraHeaders: Map<String, String> = emptyMap(),
        /** 少数网关不认 `stream_options`，降级后传 false */
        includeUsage: Boolean = true,
    ): PreparedRequest {
        require(command.model.isNotBlank()) { "model 不能为空" }
        require(command.messages.isNotEmpty()) { "messages 不能为空" }

        val system = resolveSystem(command)
        val messages = buildJsonArray {
            if (!system.isNullOrBlank()) {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", system)
                    },
                )
            }
            command.messages
                .filter { it.role != WireMessage.ROLE_REMINDER }
                .forEach { message ->
                    val text = textOf(message)
                    if (text.isEmpty()) return@forEach
                    add(
                        buildJsonObject {
                            put("role", message.role)
                            put("content", text)
                        },
                    )
                }
        }

        val body = buildJsonObject {
            put("model", command.model)
            put("messages", messages)
            put("stream", true)
            if (includeUsage) {
                put(
                    "stream_options",
                    buildJsonObject { put("include_usage", true) },
                )
            }
            // 思考强度：只有模型声明支持、且这一轮开着思考才发
            if (command.capabilities.effort && command.thinkingEnabled) {
                command.effort?.let { put("reasoning_effort", it.wire) }
            }
        }

        return PreparedRequest(
            url = endpoint(baseUrl),
            headers = buildMap {
                put("Content-Type", "application/json")
                put("Authorization", "Bearer $apiKey")
                extraHeaders.forEach { (name, value) -> put(name, value) }
            },
            bodyJson = body.toString(),
            protocol = ProviderProtocol.OPENAI_CHAT,
        )
    }

    /**
     * system 的拼法：
     * - `CLEAR` → 只有 reminder（如果有）；
     * - `REPLACE` → 快照；
     * - `APPEND` → 快照（**不拼官方默认句**：那句是"该助手为 DeepSeek"，只属于官方服务商）；
     * 之后再拼本轮的 `latest_reminder` 文本。
     */
    private fun resolveSystem(command: ChatCommand): String? {
        val snapshot = command.systemPrompt?.takeIf { it.isNotBlank() }
        val base = when (command.systemMode) {
            PromptMode.CLEAR -> null
            else -> snapshot
        }
        val reminder = command.messages
            .filter { it.role == WireMessage.ROLE_REMINDER }
            .mapNotNull { (it.content as? JsonPrimitive)?.contentOrNull }
            .joinToString("\n")
            .takeIf { it.isNotBlank() }
        return listOfNotNull(base, reminder).joinToString("\n\n").takeIf { it.isNotBlank() }
    }

    /** 文本块拼接：字符串直接取，数组只取 `type=text`（附件块在第三方链路不出现）。 */
    private fun textOf(message: WireMessage): String = when (val content = message.content) {
        is JsonPrimitive -> content.contentOrNull.orEmpty()
        is JsonArray -> content.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val type = (obj["type"] as? JsonPrimitive)?.contentOrNull
            if (type != "text") return@mapNotNull null
            (obj["text"] as? JsonPrimitive)?.contentOrNull
        }.joinToString("")
        else -> ""
    }
}
