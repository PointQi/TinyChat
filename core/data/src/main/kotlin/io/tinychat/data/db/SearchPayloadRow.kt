package io.tinychat.data.db

import androidx.room.ColumnInfo

/**
 * 会话搜索的原始命中行：带出 session 标题与 payload，正文片段由仓库层解析。
 */
data class SearchPayloadRow(
    @ColumnInfo(name = "session_id") val sessionId: String,
    val title: String?,
    val payload: String,
    @ColumnInfo(name = "inserted_at") val insertedAt: Long,
)
