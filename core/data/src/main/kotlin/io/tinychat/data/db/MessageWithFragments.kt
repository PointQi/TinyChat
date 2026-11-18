package io.tinychat.data.db

import androidx.room.Embedded
import androidx.room.Relation

/**
 * 消息 + 它的碎片 + 它的附件。`fragments` **不保证顺序**，用的时候按 `ord` 排；
 * `attachments` 按插入顺序即可（同一轮里文件在前、图片在后，顺序由用户选择决定）。
 */
data class MessageWithFragments(
    @Embedded val message: MessageEntity,
    @Relation(parentColumn = "id", entityColumn = "message_id")
    val fragments: List<FragmentEntity>,
    @Relation(parentColumn = "id", entityColumn = "message_id")
    val attachments: List<AttachmentEntity> = emptyList(),
) {
    val orderedFragments: List<FragmentEntity>
        get() = fragments.sortedBy { it.ord }
}
