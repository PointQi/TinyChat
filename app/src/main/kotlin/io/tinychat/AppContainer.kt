package io.tinychat

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.tinychat.data.archive.ChatArchive
import io.tinychat.data.attachment.AttachmentStore
import io.tinychat.data.attachment.AttachmentUploader
import io.tinychat.data.chat.ChatRepository
import io.tinychat.data.db.DatabaseFactory
import io.tinychat.data.prompt.PromptPresetStore
import io.tinychat.data.provider.ProviderStore
import io.tinychat.data.settings.KeystoreSecretStore
import io.tinychat.data.settings.SecretStore
import io.tinychat.data.settings.UserSettings
import io.tinychat.feature.chat.voice.VoiceController
import io.tinychat.model.Provider
import io.tinychat.model.ProviderCheck
import io.tinychat.model.ProviderKind
import io.tinychat.model.ProviderMeta
import io.tinychat.model.QuotaSnapshot
import io.tinychat.model.prompt.PromptMeta
import io.tinychat.net.account.AccountClient
import io.tinychat.net.account.KeyCheck
import io.tinychat.net.account.QuotaClient
import io.tinychat.net.protocol.ProviderModels
import io.tinychat.net.transport.OkHttpChatTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 手写依赖容器（决策 21）。
 *
 * 凭据（M7-6 / M8-6）：每家服务商一把 Key，存 Keystore 加密后的 DataStore，
 * 通过 [apiKeyFor] 交给聊天仓库按会话的服务商取用；[apiKey] 只保留 DeepSeek 官方那一把，
 * 供语音与余额这类官方专属接口使用。
 */
class AppContainer(context: Context) {

    private val app = context.applicationContext

    /** 只用于凭据读写这类一次性后台任务，跟随进程生命周期。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database = DatabaseFactory.create(app)

    private val transport = OkHttpChatTransport()

    /** 发请求前的连通性预检：离线直接判失败，不用等连接超时 */
    private val isOnline: () -> Boolean = {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = manager?.activeNetwork
        val capabilities = network?.let { manager.getNetworkCapabilities(it) }
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /** 附件落盘（M4）：选图/选文件后先把元信息与缩略图准备好 */
    val attachmentStore = AttachmentStore(app)

    /** 导入/导出（M6）：v3 兼容包 */
    val archive = ChatArchive(app, database)

    /** 本地设置（M5 起用它）：自动朗读、朗读音色、服务商与模型选择 */
    val userSettings = UserSettings(app)

    /** 语音（M5）：录音、频谱、ASR、TTS */
    val voiceController = VoiceController(app)

    /** 账户接口（M7/M8-6）：验 Key、查余额、拉模型目录 */
    val account = AccountClient()

    /** 订阅额度（M8-6）：OpenCode Go 的三窗口 usage */
    private val quotaClient = QuotaClient()

    /** 系统提示词（决策 45）：开关 + 用户预设，给新会话拼快照 */
    val promptStore = PromptPresetStore.create(
        database = database,
        selected = userSettings.promptSelectedId,
        selectPreset = { userSettings.setPromptSelectedId(it) },
        includeOfficial = userSettings.promptIncludeOfficial,
        setIncludeOfficial = { userSettings.setPromptIncludeOfficial(it) },
        migrated = userSettings.promptMigrated,
        markMigrated = { userSettings.markPromptMigrated() },
    )

    /** 服务商配置（M8-6）：列表、增删改、校验状态 */
    val providerStore = ProviderStore.create(database)

    /** 用户元信息开关的同步快照（仓库在 IO 线程按需读，不阻塞） */
    private val reminderEnabled = MutableStateFlow(true)

    /** 提示词变量的同步快照（所在地/语言），同样按需读 */
    private val promptMeta = MutableStateFlow(PromptMeta())

    private val _apiKey = MutableStateFlow("")

    /** DeepSeek 官方那一把 Key；空串 = 未配置。语音与余额用它。 */
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _keyedProviders = MutableStateFlow<Set<String>>(emptySet())

    /** 哪些服务商已经存了 Key；服务商目录或凭据变化时刷新，UI 用它判断「未配置」 */
    val keyedProviders: StateFlow<Set<String>> = _keyedProviders.asStateFlow()

    private val _providers = MutableStateFlow<List<Provider>>(emptyList())

    /** 服务商目录（DB 为准）：列表页、模型选择器都读它 */
    val providers: StateFlow<List<Provider>> = _providers.asStateFlow()

    private val _activeProviderId = MutableStateFlow(DEEPSEEK_ID)

    /** 新会话默认服务商；聊天页选完模型后写回 */
    val activeProviderId: StateFlow<String> = _activeProviderId.asStateFlow()

    /** Keystore 加密存储（M7-6）；按服务商 id 读写。 */
    val secrets: SecretStore = KeystoreSecretStore(app)

    init {
        scope.launch {
            userSettings.reminderEnabled.collect { reminderEnabled.value = it }
        }
        scope.launch {
            combine(userSettings.promptLocation, userSettings.promptLanguage) { location, language ->
                PromptMeta(location = location, language = language)
            }.collect { promptMeta.value = it }
        }
        scope.launch {
            // v1 的内置预设与组合方式迁到 v2 的「开关 + 用户预设」；失败不拦启动
            runCatching { promptStore.migrateIfNeeded() }
        }
        scope.launch {
            // M7-6 的单 Key 迁到 deepseek 名下（M8-6）
            runCatching { secrets.migrateLegacy("deepseek") }
            runCatching { providerStore.ensureSeeded() }
            val stored = runCatching { secrets.read("deepseek") }.getOrNull()
            if (stored.isNullOrBlank()) {
                // 仅本机联调便利：把构建期注入的 Key 播种一次。正式版这里不该有内容。
                val seed = BuildConfig.DEV_API_KEY
                if (seed.isNotBlank()) {
                    runCatching { secrets.write("deepseek", seed) }
                    _apiKey.value = seed
                }
            } else {
                _apiKey.value = stored
            }
            refreshKeyedProviders()
        }
        scope.launch {
            providerStore.observe().collect { list ->
                _providers.value = list
                refreshKeyedProviders()
            }
        }
        scope.launch {
            userSettings.activeProviderId.collect { _activeProviderId.value = it }
        }
    }

    private suspend fun refreshKeyedProviders() {
        val keyed = providerStore.all()
            .mapNotNull { provider ->
                apiKeyFor(provider.id)?.takeIf { it.isNotBlank() }?.let { provider.id }
            }
            .toSet()
        _keyedProviders.value = keyed
    }

    suspend fun apiKeyFor(providerId: String): String? =
        runCatching { secrets.read(providerId) }.getOrNull()

    /** 保存某家的 Key；传空白等价于清除。 */
    suspend fun saveProviderKey(providerId: String, key: String) {
        secrets.write(providerId, key.trim())
        if (providerId == DEEPSEEK_ID) {
            _apiKey.value = secrets.read(providerId).orEmpty()
        }
        refreshKeyedProviders()
    }

    suspend fun clearProviderKey(providerId: String) {
        secrets.clear(providerId)
        if (providerId == DEEPSEEK_ID) _apiKey.value = ""
        refreshKeyedProviders()
    }

    /** 保存服务商配置与（可选的）新 Key；`newKey == null` 表示沿用已存密钥。 */
    suspend fun saveProvider(provider: Provider, newKey: String?) {
        if (newKey != null) saveProviderKey(provider.id, newKey)
        providerStore.save(provider)
        refreshKeyedProviders()
    }

    suspend fun deleteProvider(providerId: String) {
        providerStore.delete(providerId)
        secrets.clear(providerId)
        if (providerId == DEEPSEEK_ID) _apiKey.value = ""
        refreshKeyedProviders()
    }

    /** 该服务商上次用的模型；没有就空串（由上层回落到目录第一个） */
    suspend fun lastModelFor(providerId: String): String =
        userSettings.lastModel(providerId).first()

    /**
     * 校验某个服务商：优先用传入的候选 Key（还没保存时），否则用已存的。
     * 结论会写回 `providers.check_status`，列表页的状态点直接读它。
     */
    suspend fun checkProvider(provider: Provider, candidateKey: String? = null): String? {
        val key = candidateKey?.trim()?.takeIf { it.isNotEmpty() } ?: apiKeyFor(provider.id)
        if (key.isNullOrBlank()) {
            providerStore.setCheck(provider.id, ProviderCheck.UNKNOWN, null, System.currentTimeMillis())
            return "尚未配置 API Key"
        }
        val message = when (val result = account.checkKey(ProviderModels.url(provider), key)) {
            KeyCheck.Ok -> null
            KeyCheck.Invalid -> "Key 无效"
            is KeyCheck.Failed -> result.message
        }
        // 网络不通/服务端 5xx 不代表 Key 有问题：标成「未校验」，别把点染红
        val status = when {
            message == null -> ProviderCheck.OK
            message.startsWith("网络不通") || message.startsWith("服务端异常") -> ProviderCheck.UNKNOWN
            else -> ProviderCheck.FAILED
        }
        providerStore.setCheck(
            id = provider.id,
            check = status,
            message = message,
            at = System.currentTimeMillis(),
        )
        return message
    }

    /** 拉模型目录：返回 id 列表；失败抛异常，由页面翻成一行人话。 */
    suspend fun fetchModels(provider: Provider, candidateKey: String? = null): List<String> {
        val key = candidateKey?.trim()?.takeIf { it.isNotEmpty() } ?: apiKeyFor(provider.id)
            ?: throw IllegalStateException("请先填写 API Key")
        return account.modelIds(ProviderModels.url(provider), key)
    }

    /** 订阅额度（M8-6）：只有 OpenCode Go 有；失败返回 null，UI 隐藏整块。 */
    suspend fun quotaFor(provider: Provider): QuotaSnapshot? {
        if (provider.kind != ProviderKind.OPENCODE_GO) return null
        val key = apiKeyFor(provider.id)?.takeIf { it.isNotBlank() } ?: return null
        val zenBase = provider.meta[ProviderMeta.ZEN_BASE] ?: return null
        return runCatching { quotaClient.openCodeGo(zenBase, key) }.getOrNull()
    }

    val repository = ChatRepository(
        db = database,
        transport = transport,
        isOnline = isOnline,
        attachmentStore = attachmentStore,
        uploader = AttachmentUploader(),
        reminderEnabled = { reminderEnabled.value },
        promptSnapshot = { promptStore.snapshotForNewSession() },
        promptMeta = { promptMeta.value },
        providerStore = providerStore,
        apiKeyFor = { id -> apiKeyFor(id) },
    )

    private companion object {
        const val DEEPSEEK_ID = "deepseek"
    }
}
