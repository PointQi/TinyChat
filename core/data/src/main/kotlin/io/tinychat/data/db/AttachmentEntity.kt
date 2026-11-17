package io.tinychat.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.tinychat.model.AttachmentState

/**
 * `attachments` 表（02 §3.1）。`id` 是归档里的 file id，不是自增主键。
 */
@Entity(
    tableName = "attachments",
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
    @PrimaryKey val id: String,
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
