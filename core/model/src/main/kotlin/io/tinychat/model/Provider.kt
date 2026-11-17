package io.tinychat.model

/** 服务商类型。OpenCode Go 推迟到 v1.1，届时作为第三种 kind 加入。 */
enum class ProviderKind {
    DEEPSEEK_OFFICIAL,
    OPENAI_COMPATIBLE,
}

/**
 * 单个模型的能力矩阵。**全部由服务商+模型决定，不来自 UI 状态**。
 * 关键事实（docs/01 §4.2）：扩展能力（搜索/读链接/文件/图片/标题/安全）**只有 deepseek-flash 支持**，
 * deepseek-v4-pro 只支持思考与强度。
 */
data class Capabilities(
    val webSearch: Boolean = false,
    val readUrl: Boolean = false,
    val imageInput: Boolean = false,
    val fileUpload: Boolean = false,
    val serverSideTitle: Boolean = false,
    val safetyCheck: Boolean = false,
    val thinking: Boolean = true,
    val effort: Boolean = false,
    val tts: Boolean = false,
) {
    /** 是否属于「扩展能力」模型：决定输入区是否露出搜索/附件入口。 */
    val supportsExtensions: Boolean get() = webSearch || readUrl || fileUpload || imageInput
}

data class ModelInfo(
    val id: String,
    val displayName: String,
    val contextWindow: Int,
    val maxOutputTokens: Int,
    val capabilities: Capabilities,
)

data class Provider(
    val id: String,
    val kind: ProviderKind,
    val displayName: String,
    val baseUrl: String,
    val models: List<ModelInfo>,
    val supportsBalance: Boolean = false,
    val supportsTts: Boolean = false,
) {
    val defaultModel: ModelInfo? get() = models.firstOrNull()
    fun model(id: String): ModelInfo? = models.firstOrNull { it.id == id }
}

/** 思考强度（Messages 协议走 output_config.effort，OpenAI 兼容走 reasoning_effort）。 */
enum class ThinkingEffort(val wire: String, val displayName: String) {
    LOW("low", "低"),
    HIGH("high", "高"),
    MAX("max", "最大"),
}

/** TTS 音色：只用官方两个（决策 3）。 */
enum class TtsVoice(val wire: String, val displayName: String) {
    STELLA("stella", "Stella"),
    TIDE("tide", "Tide"),
}

/**
 * 内置服务商与模型。**这里是能力矩阵的唯一真值**，
 * UI 与网络层都从这里取，不允许在别处再写一遍 if (model == "...")。
 */
object KnownProviders {

    val flash: ModelInfo = ModelInfo(
        id = "deepseek-flash",
        displayName = "DeepSeek V4.1 Flash",
        contextWindow = 1_000_000,
        maxOutputTokens = 384_000,
        capabilities = Capabilities(
            webSearch = true,
            readUrl = true,
            imageInput = true,
            fileUpload = true,
            serverSideTitle = true,
            safetyCheck = true,
            thinking = true,
            effort = true,
            tts = true,
        ),
    )

    val v4Pro: ModelInfo = ModelInfo(
        id = "deepseek-v4-pro",
        displayName = "DeepSeek V4 Pro",
        contextWindow = 1_000_000,
        maxOutputTokens = 384_000,
        capabilities = Capabilities(
            webSearch = false,
            readUrl = false,
            imageInput = false,
            fileUpload = false,
            serverSideTitle = false,
            safetyCheck = false,
            thinking = true,
            effort = true,
            tts = true,
        ),
    )

    val deepSeek: Provider = Provider(
        id = "deepseek",
        kind = ProviderKind.DEEPSEEK_OFFICIAL,
        displayName = "DeepSeek 官方",
        baseUrl = "https://api.deepseek.com",
        models = listOf(flash, v4Pro),
        supportsBalance = true,
        supportsTts = true,
    )

    /** 通用 OpenAI 兼容服务商：一律走降级链路，扩展能力全部关闭。 */
    fun genericOpenAiCompatible(baseUrl: String, modelId: String): Provider = Provider(
        id = "openai-compatible",
        kind = ProviderKind.OPENAI_COMPATIBLE,
        displayName = "OpenAI 兼容",
        baseUrl = baseUrl,
        models = listOf(
            ModelInfo(
                id = modelId,
                displayName = modelId,
                contextWindow = 128_000,
                maxOutputTokens = 8_192,
                capabilities = Capabilities(
                    webSearch = false,
                    readUrl = false,
                    imageInput = false,
                    fileUpload = false,
                    serverSideTitle = false,
                    safetyCheck = false,
                    thinking = false,
                    effort = false,
                    tts = false,
                ),
            ),
        ),
    )
}
