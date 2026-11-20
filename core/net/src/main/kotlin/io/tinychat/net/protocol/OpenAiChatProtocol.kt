package io.tinychat.net.protocol

import io.tinychat.model.PromptMode
import io.tinychat.model.ThinkingEffort
import io.tinychat.model.ThinkingMode
import io.tinychat.model.ProviderProtocol
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * OpenAI Chat Completions 协议（M8-1 / 决策 53 起带图片）。
 *
 * `messages[].content` 通常是字符串；这一轮带图片时变成内容数组
 * `[{"type":"text"}, {"type":"image_url","image_url":{"url":"data:…"}}]`。
 * 文件（PDF/Word）仍然只有官方 Files API 支持，第三方链路丢弃。
 *
 * 与官方 Messages 链路的三处差别，都在这里收口：
 * 1. 端点与鉴权（Bearer，而不是 `x-api-key`）；
 * 2. 没有 `thinking` / `tools` / `safety_check` / `title` 这些扩展字段，**标题本地生成**；
 * 3. `latest_reminder` 是 DeepSeek 的自定义角色，这里**拼进紧随其后的用户消息正文**——
 *    **不放 system**：system 位于请求的最开头，而元信息里带着日期，日期每天变一次，
 *    放进去就等于每天的第一条请求全部重新计算（输入未命中的价格是命中的五十倍）。
 *    拼在末尾那条用户消息里，前缀不动，只有这一条在变。
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
            // 元信息本身不是 OpenAI 的合法角色：攒着，拼到下一条消息正文的最前面。
            // 位置与官方链路一致（紧挨着用户提问），但不会动到 system 那两个字节。
            var pendingReminder: String? = null
            command.messages.forEach { message ->
                if (message.role == WireMessage.ROLE_REMINDER) {
                    textOf(message).takeIf { it.isNotEmpty() }?.let { pendingReminder = it }
                    return@forEach
                }
                val content = contentOf(message, pendingReminder) ?: return@forEach
                pendingReminder = null
                add(
                    buildJsonObject {
                        put("role", message.role)
                        put("content", content)
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
            // 思考强度：只有该模型声明了 reasoning_effort、且这一轮开着思考才发。
            // 官方取值是 low/medium/high（部分新模型还有 none/minimal/xhigh），
            // 我们的三档按 low、medium、high 映射，避免撞上不认识的枚举值。
            if (command.capabilities.effort &&
                command.thinkingEnabled &&
                command.thinking == ThinkingMode.EFFORT
            ) {
                command.effort?.let { put("reasoning_effort", it.openAiWire()) }
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
     * - `CLEAR` → 不发；
     * - `REPLACE` → 快照；
     * - `APPEND` → 快照（**不拼官方默认句**：那句是"该助手为 DeepSeek"，只属于官方服务商）。
     *
     * 用户元信息**不在这里**——它带着日期，见类注释第 3 条。
     */
    private fun resolveSystem(command: ChatCommand): String? = when (command.systemMode) {
        PromptMode.CLEAR -> null
        else -> command.systemPrompt?.takeIf { it.isNotBlank() }
    }

    /** 文本块拼接：字符串直接取，数组只取 `type=text`（图片块另行处理）。 */
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

    /**
     * 一条消息的 `content`（决策 53）：
     *
     * - 没有图片 → 字符串（元信息拼在最前面），与改动前的报文逐字节一致；
     * - 有图片 → 数组：一个 text 元素（同样把元信息拼进去）+ 若干 `image_url`；
     * - 既没有文字也没有图片 → null（这条消息整个跳过）。
     */
    private fun contentOf(message: WireMessage, reminder: String?): JsonElement? {
        val text = textOf(message)
        val merged = when {
            reminder.isNullOrEmpty() -> text
            text.isEmpty() -> reminder
            else -> "$reminder\n\n$text"
        }
        val images = imageUrls(message)
        if (images.isEmpty()) return merged.takeIf { it.isNotEmpty() }?.let(::JsonPrimitive)
        // 顺序与官方链路一致：图片在前、文本在后（03 §6.5）
        return buildJsonArray {
            images.forEach { url ->
                add(
                    buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject { put("url", url) })
                    },
                )
            }
            if (merged.isNotEmpty()) {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", merged)
                    },
                )
            }
        }
    }

    /** 数组内容里的 `image_url` 块（官方链路的历史图片不会被转成这个形状，自然不会误抓） */
    private fun imageUrls(message: WireMessage): List<String> {
        val content = message.content as? JsonArray ?: return emptyList()
        return content.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            if ((obj["type"] as? JsonPrimitive)?.contentOrNull != "image_url") return@mapNotNull null
            val url = ((obj["image_url"] as? JsonObject)?.get("url") as? JsonPrimitive)?.contentOrNull
            url?.takeIf { it.isNotBlank() }
        }
    }

    /** 我们的三档 → OpenAI 的合法枚举（low/medium/high）。 */
    private fun ThinkingEffort.openAiWire(): String = when (this) {
        ThinkingEffort.LOW -> "low"
        ThinkingEffort.HIGH -> "medium"
        ThinkingEffort.MAX -> "high"
    }
}
