package io.tinychat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 右抽屉松手时的开合结算（P-028）。
 *
 * 阈值口径与 02 §16 的底部抽屉一致：走过 35%（展开状态下是往回走 35%）或者甩得够快。
 * 这些用例就是把真机上「拖到一半松手」的几种落点钉死。
 */
class SidePanelTest {

    private fun target(progress: Float, velocity: Float, open: Boolean) =
        panelTargetOpen(progress, velocity, currentlyOpen = open)

    @Test
    fun `收起状态只拖了一点就松手，回弹`() {
        assertFalse(target(progress = 0.2f, velocity = 0f, open = false))
    }

    @Test
    fun `收起状态走过 35 就开`() {
        assertTrue(target(progress = 0.35f, velocity = 0f, open = false))
    }

    @Test
    fun `收起状态没走够但向左甩，开`() {
        assertTrue(target(progress = 0.08f, velocity = -900f, open = false))
    }

    @Test
    fun `展开状态往回拖了 40 仍算开着`() {
        assertTrue(target(progress = 0.7f, velocity = 0f, open = true))
    }

    @Test
    fun `展开状态往回拖到 65 及以下就关`() {
        assertFalse(target(progress = 0.65f, velocity = 0f, open = true))
        assertFalse(target(progress = 0.4f, velocity = 0f, open = true))
    }

    @Test
    fun `展开状态向右甩，关`() {
        assertFalse(target(progress = 0.95f, velocity = 900f, open = true))
    }

    @Test
    fun `速度优先于位置：位置够但反向甩回`() {
        assertFalse(target(progress = 0.9f, velocity = 1200f, open = false))
    }

    @Test
    fun `方向为零的极端值不会算错`() {
        assertEquals("收起且进度 0、速度 0 时不该开", false, target(0f, 0f, false))
        assertEquals("展开且进度 1、速度 0 时应保持", true, target(1f, 0f, true))
    }
}
