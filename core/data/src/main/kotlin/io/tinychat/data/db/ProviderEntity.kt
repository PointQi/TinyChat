package io.tinychat.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * `providers` 表（M8-6）：本机保存的服务商配置。
 *
 * 只存**配置**，不存明文 Key（Key 在 `credentials` DataStore 里按 id 加密）。
 * `models_json` 是 `List<ModelInfo>` 的 JSON：能力位跟着模型走，UI 与网络层都读它。
 */
@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val kind: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "base_url") val baseUrl: String,
    val protocol: String,
    @ColumnInfo(name = "models_json") val modelsJson: String,
    /** 内置服务商（DeepSeek）不可删 */
    val builtin: Boolean = false,
    val sort: Int = 0,
    @ColumnInfo(name = "check_status") val checkStatus: String = "unknown",
    @ColumnInfo(name = "check_message") val checkMessage: String? = null,
    @ColumnInfo(name = "checked_at") val checkedAt: Long? = null,
    @ColumnInfo(name = "meta_json") val metaJson: String = "{}",
)
