package io.tinychat.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 消息树分支（M1-8）：编辑重发产生 USER 兄弟，重新生成产生 ASSISTANT 兄弟，
 * 切换版本后指针要落到目标分支的叶子上。
 *
 * 这棵树：
 * ```
 * 1 提问 ── 2 回答A ── 5 追问 ── 6 回答C
 *        ├─ 3 回答B ── 7 追问2
 *        └─ 4 回答D
 * ```
 */
class MessageBranchTest {

    private val nodes = listOf(
        BranchNode(id = 1, parentId = null, insertedAt = 10),
        BranchNode(id = 2, parentId = 1, insertedAt = 11),
        BranchNode(id = 3, parentId = 1, insertedAt = 12),
        BranchNode(id = 4, parentId = 1, insertedAt = 13),
        BranchNode(id = 5, parentId = 2, insertedAt = 14),
        BranchNode(id = 6, parentId = 5, insertedAt = 15),
        BranchNode(id = 7, parentId = 3, insertedAt = 16),
    )

    @Test
    fun `兄弟按插入时间排序`() {
        assertEquals(listOf(2L, 3L, 4L), siblingsOf(nodes, 1).map { it.id })
        assertTrue(siblingsOf(nodes, 99).isEmpty())
    }

    @Test
    fun `最深叶子沿着最新子节点走`() {
        assertEquals(4L, deepestLeaf(nodes, 4)?.id)
        assertEquals(6L, deepestLeaf(nodes, 2)?.id)
        assertEquals(7L, deepestLeaf(nodes, 3)?.id)
    }

    @Test
    fun `切到下一版并落到目标分支自己的叶子`() {
        // 当前回答 A（有后续追问），下一版是回答 B，B 下面还有追问 7
        assertEquals(7L, pickVersion(nodes, messageId = 2, delta = 1)?.id)
        // 回答 B 往下一版是回答 D，D 没有下文
        assertEquals(4L, pickVersion(nodes, messageId = 3, delta = 1)?.id)
    }

    @Test
    fun `到头就不动，不循环`() {
        // 已经是最后一版（回答 D）：再往后没有了
        assertNull(pickVersion(nodes, messageId = 4, delta = 1))
        // 已经是第一版（回答 A）：再往前没有了
        assertNull(pickVersion(nodes, messageId = 2, delta = -1))
        // 中间版本可以双向走
        assertEquals(6L, pickVersion(nodes, messageId = 3, delta = -1)?.id)
        assertEquals(4L, pickVersion(nodes, messageId = 3, delta = 1)?.id)
    }
    @Test
    fun `只有一版时不切换`() {
        assertNull(pickVersion(nodes, 6, 1))
        assertNull(pickVersion(nodes, 1, 1))
    }

    @Test
    fun `版本指针给出 1 基下标与总数`() {
        assertEquals(1 to 3, versionPointer(nodes, 2))
        assertEquals(3 to 3, versionPointer(nodes, 4))
        assertEquals(1 to 1, versionPointer(nodes, 1))
        assertEquals(1 to 1, versionPointer(nodes, 999))
    }
}

