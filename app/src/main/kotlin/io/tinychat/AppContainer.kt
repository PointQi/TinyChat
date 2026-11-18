package io.tinychat

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.tinychat.data.attachment.AttachmentStore
import io.tinychat.data.attachment.AttachmentUploader
import io.tinychat.data.chat.ChatRepository
import io.tinychat.data.db.DatabaseFactory
import io.tinychat.net.transport.OkHttpChatTransport

/**
 * 手写依赖容器（决策 21）。M1 只有数据库、传输、仓库三样东西，不值得引 Hilt。
 *
 * ⚠️ [apiKey] 目前来自 `BuildConfig.DEV_API_KEY`（debug 专用，构建时从
 * `.local/dev-key.txt` 注入）。M8-6 做完服务商页后改成从 Keystore+DataStore 读。
 */
class AppContainer(context: Context) {

    private val database = DatabaseFactory.create(context.applicationContext)

    private val transport = OkHttpChatTransport()

    /** 发请求前的连通性预检：离线直接判失败，不用等连接超时 */
    private val isOnline: () -> Boolean = {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = manager?.activeNetwork
        val capabilities = network?.let { manager.getNetworkCapabilities(it) }
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /** 附件落盘（M4）：选图/选文件后先把元信息与缩略图准备好 */
    val attachmentStore = AttachmentStore(context.applicationContext)

    val repository = ChatRepository(
        db = database,
        transport = transport,
        isOnline = isOnline,
        attachmentStore = attachmentStore,
        uploader = AttachmentUploader(),
    )

    val apiKey: String = BuildConfig.DEV_API_KEY
}
