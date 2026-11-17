package io.tinychat.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `fragments` 表（02 §3.1）。与 v3 导出同构，`payload` 是完整 JSON（含 `type`）。
 *
 * 单独建表而不是塞进 message 的 JSON 列：TOOL_SEARCH 要排序、要按 stage_id 分组、将来要检索。
 */
@Entity(
    tableName = "fragments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(name = "idx_fragments_ord", value = ["message_id", "ord"], unique = true)],
)
data class FragmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "message_id") val messageId: Long,
    val ord: Int,
    /** 与 v3 导出完全一致的枚举字符串 */
    val type: String,
    /** kotlinx.serialization JSON；未知类型也原样保留 */
    val payload: String,
)
