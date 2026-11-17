package io.tinychat.data.db

import androidx.room.Embedded
import androidx.room.Relation

/**
 * 消息 + 它的碎片。`fragments` **不保证顺序**，用的时候按 `ord` 排。
 */
data class MessageWithFragments(
    @Embedded val message: MessageEntity,
    @Relation(parentColumn = "id", entityColumn = "message_id")
    val fragments: List<FragmentEntity>,
) {
    val orderedFragments: List<FragmentEntity>
        get() = fragments.sortedBy { it.ord }
}
