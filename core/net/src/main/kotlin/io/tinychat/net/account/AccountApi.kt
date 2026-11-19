package io.tinychat.net.account

import io.tinychat.net.transport.OkHttpChatTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** 账户接口（M7-4/M7-6）：`GET /models` 校验 Key，`GET /user/balance` 查余额。 */
object AccountRequests {

    /** 校验 Key 用；不计费，也不消耗额度。 */
    const val MODELS_PATH = "/models"

    /** 余额；只有 DeepSeek 官方有。 */
    const val BALANCE_PATH = "/user/balance"

    fun get(baseUrl: String, path: String, apiKey: String): Request =
        Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
}

/** Key 校验结论：只有三种，够 UI 用了。 */
sealed interface KeyCheck {
    data object Ok : KeyCheck
    data object Invalid : KeyCheck
    data class Failed(val message: String) : KeyCheck
}

/** 一条余额记录（`balance_infos[]`）。金额是字符串，服务端可能给 "110.00" 或 "0"。 */
data class BalanceInfo(
    val currency: String,
    val totalBalance: String,
    val grantedBalance: String,
    val toppedUpBalance: String,
)

data class BalanceSnapshot(
    val isAvailable: Boolean,
    val infos: List<BalanceInfo>,
) {
    /** 人民币优先（官方以 CNY 结算）；没有就取第一条。 */
    val primary: BalanceInfo?
        get() = infos.firstOrNull { it.currency.equals("CNY", ignoreCase = true) } ?: infos.firstOrNull()
}

class AccountException(message: String, val httpStatus: Int? = null) : Exception(message)

/**
 * 账户客户端。与聊天共用一套 OkHttp 配置，但**不挂日志拦截器**（请求头里有 Key）。
 */
class AccountClient(
    private val client: OkHttpClient = OkHttpChatTransport.defaultClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    /** 校验 Key：200 算通过，401 算无效，其余给一句人话。 */
    suspend fun checkKey(apiKey: String): KeyCheck = withContext(Dispatchers.IO) {
        val request = AccountRequests.get(baseUrl, AccountRequests.MODELS_PATH, apiKey)
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> KeyCheck.Ok
                    response.code == 401 || response.code == 403 -> KeyCheck.Invalid
                    response.code == 402 -> KeyCheck.Failed("账户余额不足")
                    response.code == 429 -> KeyCheck.Failed("请求过于频繁，请稍后再试")
                    response.code >= 500 -> KeyCheck.Failed("服务端异常，请稍后再试")
                    else -> KeyCheck.Failed("校验失败（HTTP ${response.code}）")
                }
            }
        } catch (e: IOException) {
            KeyCheck.Failed("网络不通，请检查网络后重试")
        }
    }

    suspend fun balance(apiKey: String): BalanceSnapshot = withContext(Dispatchers.IO) {
        val request = AccountRequests.get(baseUrl, AccountRequests.BALANCE_PATH, apiKey)
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw AccountException("余额查询失败（HTTP ${response.code}）", response.code)
                }
                parseBalance(response.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            throw AccountException("网络不通", null)
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"

        private val json = Json { ignoreUnknownKeys = true }

        /** 容错解析：缺字段按 0，类型不对也尽量给出结构，让 UI 有东西可显示。 */
        fun parseBalance(raw: String): BalanceSnapshot {
            val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
                ?: throw AccountException("余额返回格式无法解析")
            val infos = runCatching {
                root["balance_infos"]?.jsonArray.orEmpty().map { element ->
                    val obj = element.jsonObject
                    BalanceInfo(
                        currency = obj.string("currency") ?: "CNY",
                        totalBalance = obj.string("total_balance") ?: "0",
                        grantedBalance = obj.string("granted_balance") ?: "0",
                        toppedUpBalance = obj.string("topped_up_balance") ?: "0",
                    )
                }
            }.getOrElse { throw AccountException("余额返回格式无法解析") }
            return BalanceSnapshot(
                isAvailable = root["is_available"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true,
                infos = infos,
            )
        }

        private fun JsonObject.string(key: String): String? =
            this[key]?.jsonPrimitive?.contentOrNull
    }
}
