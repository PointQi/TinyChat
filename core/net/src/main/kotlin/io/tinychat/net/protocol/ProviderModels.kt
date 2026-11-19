package io.tinychat.net.protocol

import io.tinychat.model.Provider
import io.tinychat.model.ProviderKind
import io.tinychat.model.ProviderMeta
import io.tinychat.model.ProviderProtocol

/**
 * 模型目录探测地址（M8-6）。
 *
 * 各家的 `/models` 不在同一层：
 * - 官方 DeepSeek：`https://api.deepseek.com/models`（没有 `/v1`）；
 * - OpenAI 兼容：`{base}/v1/models`（地址已带 `/v1` 就不再拼）；
 * - OpenCode Go：`zen/go/v1/models`（对话在 inference 域，目录在 zen 域）。
 */
object ProviderModels {

    fun url(provider: Provider): String = when (provider.protocol) {
        ProviderProtocol.DEEPSEEK_MESSAGES ->
            provider.baseUrl.trimEnd('/') + "/models"

        ProviderProtocol.OPENAI_CHAT -> {
            val base = if (provider.kind == ProviderKind.OPENCODE_GO) {
                provider.meta[ProviderMeta.ZEN_BASE] ?: provider.baseUrl
            } else {
                provider.baseUrl
            }
            val trimmed = base.trimEnd('/')
            if (trimmed.endsWith("/v1")) "$trimmed/models" else "$trimmed/v1/models"
        }

        ProviderProtocol.ANTHROPIC_MESSAGES -> {
            val trimmed = provider.baseUrl.trimEnd('/')
            if (trimmed.endsWith("/v1")) "$trimmed/models" else "$trimmed/v1/models"
        }
    }
}
