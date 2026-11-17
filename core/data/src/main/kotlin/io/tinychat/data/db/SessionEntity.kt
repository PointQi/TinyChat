package io.tinychat.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.tinychat.model.PromptMode
import io.tinychat.model.ThinkingEffort

/**
 * `sessions` 表（02 §3.1）。
 *
 * 索引按 (deleted, pinned, updated_at) 升序建。列表查询是 `pinned DESC, updated_at DESC`，
 * 混合方向的索引 Room 表达不了，升序索引在数据量级（几千）下足够，暂不优化。
 */
@Entity(
    tableName = "sessions",
    indices = [Index(name = "idx_sessions_updated", value = ["deleted", "pinned", "updated_at"])],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String? = null,
    val pinned: Boolean = false,
    @ColumnInfo(name = "current_message_id") val currentMessageId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "provider_id") val providerId: String,
    val model: String,
    @ColumnInfo(name = "thinking_enabled") val thinkingEnabled: Boolean = false,
    @ColumnInfo(name = "search_enabled") val searchEnabled: Boolean = false,
    val effort: ThinkingEffort? = null,
    @ColumnInfo(name = "system_prompt_snapshot") val systemPromptSnapshot: String? = null,
    @ColumnInfo(name = "system_prompt_mode") val systemPromptMode: PromptMode = PromptMode.REPLACE,
    val deleted: Boolean = false,
)
