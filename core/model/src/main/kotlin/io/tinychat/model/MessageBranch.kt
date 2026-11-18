package io.tinychat.model

/**
 * 消息树里的一个节点（M1-8）。
 *
 * 版本切换只需要「谁是谁的父、谁更新」，不需要正文，所以单独一个轻量结构，
 * 让分支算法能在 core:model 里被单测，而不依赖 Room 实体。
 */
data class BranchNode(
    val id: Long,
    val parentId: Long?,
    val insertedAt: Long,
)

/**
 * 同一父节点下的兄弟（按插入时间升序），即「同一条提问的多次回答」或「同一位置的多次提问」。
 */
fun siblingsOf(nodes: List<BranchNode>, parentId: Long?): List<BranchNode> =
    nodes.filter { it.parentId == parentId }.sortedBy { it.insertedAt }

/**
 * 从 [fromId] 往下走到最深的叶子：每条都取插入时间最晚的孩子。
 *
 * 切换版本后当前分支指针要落在叶子上，否则界面会停在一个没有下文的中间节点。
 */
fun deepestLeaf(nodes: List<BranchNode>, fromId: Long): BranchNode? {
    var cursor = nodes.firstOrNull { it.id == fromId } ?: return null
    val visited = HashSet<Long>()
    while (visited.add(cursor.id)) {
        val child = nodes.filter { it.parentId == cursor.id }.maxByOrNull { it.insertedAt }
            ?: return cursor
        cursor = child
    }
    return cursor
}

/**
 * 切到 [messageId] 的相邻版本（[delta] 为 +1 / -1，循环）。
 *
 * @return 目标分支的叶子节点；只有一版时返回 null（调用方据此不显示切换器）
 */
fun pickVersion(nodes: List<BranchNode>, messageId: Long, delta: Int): BranchNode? {
    val target = nodes.firstOrNull { it.id == messageId } ?: return null
    val siblings = siblingsOf(nodes, target.parentId)
    if (siblings.size < 2) return null

    val index = siblings.indexOfFirst { it.id == messageId }
    if (index < 0) return null

    val next = siblings[Math.floorMod(index + delta, siblings.size)]
    return deepestLeaf(nodes, next.id)
}

/** 当前消息在兄弟中的位置：1 基下标与总数；只有一版时 [total] 为 1。 */
fun versionPointer(nodes: List<BranchNode>, messageId: Long): Pair<Int, Int> {
    val target = nodes.firstOrNull { it.id == messageId } ?: return 1 to 1
    val siblings = siblingsOf(nodes, target.parentId)
    val index = siblings.indexOfFirst { it.id == messageId }
    return if (index < 0) 1 to siblings.size.coerceAtLeast(1) else (index + 1) to siblings.size.coerceAtLeast(1)
}
