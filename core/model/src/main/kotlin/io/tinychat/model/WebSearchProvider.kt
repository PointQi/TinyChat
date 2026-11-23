package io.tinychat.model

enum class WebSearchProvider(val wire: String, val label: String) {
    DEEPSEEK("deepseek", "DeepSeek"),
    TAVILY("tavily", "Tavily"),
    ANYSEARCH("anysearch", "AnySearch"),
    ;

    fun usesBuiltInSearch(protocol: ProviderProtocol?, modelSupportsSearch: Boolean): Boolean =
        this == DEEPSEEK && protocol == ProviderProtocol.DEEPSEEK_MESSAGES && modelSupportsSearch

    companion object {
        fun fromWire(value: String?): WebSearchProvider = entries.firstOrNull { it.wire == value } ?: DEEPSEEK
    }
}

data class WebSearchCredentials(
    val savedKeys: Set<WebSearchProvider> = emptySet(),
    val canReuseDeepSeekKey: Boolean = false,
) {
    fun hasKey(provider: WebSearchProvider): Boolean =
        provider in savedKeys || (provider == WebSearchProvider.DEEPSEEK && canReuseDeepSeekKey)
}
