package io.tinychat.data.provider

import io.tinychat.data.db.ProviderDao
import io.tinychat.data.db.ProviderEntity
import io.tinychat.model.KnownProviders
import io.tinychat.model.ModelInfo
import io.tinychat.model.Provider
import io.tinychat.model.ProviderCheck
import io.tinychat.model.ProviderKind
import io.tinychat.model.ProviderProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 服务商配置的读写（M8-6）。
 *
 * 与 `PromptPresetStore` 同构：**领域的 `Provider` 只在 core 里流动**，
 * `:app` 不接触 Room 类型（决策 23 的教训）。
 */
class ProviderStore(private val dao: ProviderDao) {

    fun observe(): Flow<List<Provider>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun all(): List<Provider> = dao.all().map { it.toDomain() }

    suspend fun byId(id: String): Provider? = dao.byId(id)?.toDomain()

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun setCheck(id: String, check: ProviderCheck, message: String?, at: Long) {
        dao.setCheck(id, check.wire, message, at)
    }

    /** 写一个服务商级标记（例如 `no_effort=1`），用于「试过一次失败就记住」的降级 */
    suspend fun setMeta(id: String, key: String, value: String) {
        val row = dao.byId(id) ?: return
        val meta = runCatching {
            json.decodeFromString(META_SERIALIZER, row.metaJson)
        }.getOrDefault(emptyMap()) + (key to value)
        dao.upsert(row.copy(metaJson = json.encodeToString(META_SERIALIZER, meta)))
    }

    /**
     * 首次启动播种 DeepSeek：老用户升级上来时，`sessions.provider_id` 已经是 `deepseek`，
     * 这一行必须存在，否则历史会话会被当成「服务商已移除」。
     */
    suspend fun ensureSeeded() {
        if (dao.byId(KnownProviders.deepSeek.id) != null) return
        dao.upsert(KnownProviders.deepSeek.toEntity(sort = 0))
    }

    /** 新建：自动排到末尾；编辑：保持原 sort */
    suspend fun save(provider: Provider) {
        val existing = dao.byId(provider.id)
        val sort = existing?.sort ?: dao.nextSort()
        dao.upsert(provider.toEntity(sort = sort))
    }

    private fun Provider.toEntity(sort: Int): ProviderEntity = ProviderEntity(
        id = id,
        kind = kind.name,
        displayName = displayName,
        baseUrl = baseUrl.trimEnd('/'),
        protocol = protocol.wire,
        modelsJson = json.encodeToString(ListSerializer(ModelInfo.serializer()), models),
        builtin = builtin,
        sort = sort,
        checkStatus = check.wire,
        checkMessage = checkMessage,
        checkedAt = null,
        metaJson = json.encodeToString(META_SERIALIZER, meta),
    )

    private fun ProviderEntity.toDomain(): Provider = Provider(
        id = id,
        kind = runCatching { ProviderKind.valueOf(kind) }.getOrDefault(ProviderKind.OPENAI_COMPATIBLE),
        displayName = displayName,
        baseUrl = baseUrl,
        models = runCatching {
            json.decodeFromString(ListSerializer(ModelInfo.serializer()), modelsJson)
        }.getOrDefault(emptyList()),
        supportsBalance = kind == ProviderKind.DEEPSEEK_OFFICIAL.name,
        supportsTts = kind == ProviderKind.DEEPSEEK_OFFICIAL.name,
        protocol = ProviderProtocol.fromWire(protocol),
        builtin = builtin,
        check = ProviderCheck.fromWire(checkStatus),
        checkMessage = checkMessage,
        meta = runCatching { json.decodeFromString(META_SERIALIZER, metaJson) }.getOrDefault(emptyMap()),
    )

    companion object {
        /** `:app` 不接触 Room 类型（决策 23 的教训）：建实例也放在 core:data 里 */
        fun create(database: io.tinychat.data.db.TinyChatDatabase): ProviderStore =
            ProviderStore(database.providerDao())

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        private val META_SERIALIZER = MapSerializer(String.serializer(), String.serializer())
    }
}
