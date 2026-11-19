package io.tinychat.net.account

import io.tinychat.model.QuotaSnapshot
import io.tinychat.model.QuotaWindow
import io.tinychat.net.transport.OkHttpChatTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * 订阅额度（M8-6）：OpenCode Go 的 `GET {zenBase}/v1/usage`。
 *
 * 返回三个窗口的**已用**百分比与重置时间；只读账户状态，不消耗订阅额度。
 * 口径（docs/05 §8）：三窗口对应月额度的 20% / 50% / 100%，`percent` 是已用。
 */
class QuotaClient(
    private val client: OkHttpClient = OkHttpChatTransport.defaultClient(),
) {

    suspend fun openCodeGo(zenBase: String, apiKey: String): QuotaSnapshot = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(zenBase.trimEnd('/') + USAGE_PATH)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw AccountException("额度查询失败（HTTP ${response.code}）", response.code)
                }
                parse(response.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            throw AccountException("网络不通", null)
        }
    }

    companion object {
        const val USAGE_PATH = "/v1/usage"

        private val json = Json { ignoreUnknownKeys = true }

        private val LABELS = mapOf(
            "rolling" to "5 小时",
            "weekly" to "本周",
            "monthly" to "本月",
        )

        /** 容错解析：坏字段丢弃、不坏整份；一个窗口都解析不出来才报错。 */
        fun parse(raw: String, now: Long = System.currentTimeMillis()): QuotaSnapshot {
            val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
                ?: throw AccountException("额度返回格式无法解析")
            val usage = runCatching { root["usage"]?.jsonObject }.getOrNull()
                ?: throw AccountException("额度返回缺少 usage")
            val windows = usage.entries
                .mapNotNull { (key, element) ->
                    val label = LABELS[key] ?: return@mapNotNull null
                    val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
                    val status = obj["status"]?.jsonPrimitive?.contentOrNull
                    val rawPercent = obj["percent"]?.jsonPrimitive?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
                        ?: return@mapNotNull null
                    val used = when {
                        status == "rate-limited" -> 100
                        else -> rawPercent.coerceIn(0, 100)
                    }
                    QuotaWindow(
                        key = key,
                        label = label,
                        usedPercent = used,
                        resetAt = obj["resetsAt"]?.jsonPrimitive?.contentOrNull,
                    )
                }
                .sortedBy { WINDOW_ORDER.indexOf(it.key) }
            if (windows.isEmpty()) throw AccountException("额度返回里没有可用窗口")
            return QuotaSnapshot(windows = windows, fetchedAt = now)
        }

        private val WINDOW_ORDER = listOf("rolling", "weekly", "monthly")
    }
}
