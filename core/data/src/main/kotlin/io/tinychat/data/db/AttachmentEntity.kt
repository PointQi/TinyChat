package io.tinychat.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import io.tinychat.model.AttachmentState

/**
 * `attachments` 表（02 §3.1）。`id` 是归档里的 file id。
 *
 * ⚠️ **主键是 (id, message_id)**：同一个附件可能被同一会话里的两条提问引用
 * （真样本 124 个附件里有 20 个是这种重复引用）。只用 `id` 做主键时后写的一条
 * 会把前一条顶掉，导出时那条 `export_path` 就丢了、界面也少一个缩略图。
 */
@Entity(
    tableName = "attachments",
    primaryKeys = ["id", "message_id"],
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(name = "idx_attachments_msg", value = ["message_id"])],
)
data class AttachmentEntity(
    val id: String,
    @ColumnInfo(name = "message_id") val messageId: Long,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    val mime: String? = null,
    @ColumnInfo(name = "local_path") val localPath: String? = null,
    @ColumnInfo(name = "remote_id") val remoteId: String? = null,
    @ColumnInfo(name = "is_image") val isImage: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    val state: AttachmentState = AttachmentState.LOCAL,
)
