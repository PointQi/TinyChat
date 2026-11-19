package io.tinychat

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.tinychat.data.archive.ChatArchive
import io.tinychat.data.attachment.AttachmentStore
import io.tinychat.data.attachment.AttachmentUploader
import io.tinychat.data.chat.ChatRepository
import io.tinychat.data.db.DatabaseFactory
import io.tinychat.data.settings.KeystoreSecretStore
import io.tinychat.data.settings.SecretStore
import io.tinychat.data.settings.UserSettings
import io.tinychat.feature.chat.voice.VoiceController
import io.tinychat.net.account.AccountClient
import io.tinychat.net.transport.OkHttpChatTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 手写依赖容器（决策 21）。M1 只有数据库、传输、仓库三样东西，不值得引 Hilt。
 *
 * 凭据（M7-6）：Key 从「编译期常量」改成运行时可变——存 Keystore 加密后的 DataStore，
 * 通过 [apiKey] 这个 StateFlow 分发给所有使用方。debug 首次启动会把
 * `BuildConfig.DEV_API_KEY` 播种进去，免得每装一次都要手输。
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

    /** 本地设置（M5 起用它）：自动朗读、朗读音色 */
    val userSettings = UserSettings(app)

    /** 语音（M5）：录音、频谱、ASR、TTS */
    val voiceController = VoiceController(app)

    /** 账户接口（M7）：`GET /models` 验 Key、`GET /user/balance` 查余额 */
    val account = AccountClient()

    private val _apiKey = MutableStateFlow("")

    /** 用户元信息开关的同步快照（仓库在 IO 线程按需读，不阻塞） */
    private val reminderEnabled = MutableStateFlow(true)

    /** 当前 Key；空串 = 未配置。所有请求每次现取，换 Key 立刻生效。 */
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    /** Keystore 加密存储（M7-6）；单测里换成假实现。 */
    val secrets: SecretStore = KeystoreSecretStore(app)

    init {
        scope.launch {
            userSettings.reminderEnabled.collect { reminderEnabled.value = it }
        }
        scope.launch {
            val stored = runCatching { secrets.read() }.getOrNull()
            if (stored.isNullOrBlank()) {
                // 仅本机联调便利：把构建期注入的 Key 播种一次。正式版这里不该有内容。
                val seed = BuildConfig.DEV_API_KEY
                if (seed.isNotBlank()) {
                    runCatching { secrets.write(seed) }
                    _apiKey.value = seed
                }
            } else {
                _apiKey.value = stored
            }
        }
    }

    suspend fun saveApiKey(key: String) {
        val trimmed = key.trim()
        secrets.write(trimmed)
        _apiKey.value = trimmed
    }

    suspend fun clearApiKey() {
        secrets.clear()
        _apiKey.value = ""
    }

    val repository = ChatRepository(
        db = database,
        transport = transport,
        isOnline = isOnline,
        attachmentStore = attachmentStore,
        uploader = AttachmentUploader(),
        reminderEnabled = { reminderEnabled.value },
    )
}
