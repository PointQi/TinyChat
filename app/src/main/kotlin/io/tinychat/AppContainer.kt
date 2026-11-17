package io.tinychat

import android.content.Context
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

    val repository = ChatRepository(database, transport)

    val apiKey: String = BuildConfig.DEV_API_KEY
}
