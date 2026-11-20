package io.tinychat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 侧栏松手时的开合结算（P-028 ~ P-031）。
 *
 * 两个方向的判据不对称：**打开很松**（走过 2%，或者 250dp/s 的轻拨就整个拉出），
 * **关闭要明确**（走过 35%，或者 800dp/s）—— 前者就是官方那种「轻轻一划抽屉就出来」，
 * 后者保证手一抖不会把已打开的抽屉收掉。速度只决定开合，不决定动画快慢。
 * 速度方向统一成「正 = 打开」，左右两个侧栏由调用方换符号。
 */
class SidePanelTest {

    private fun target(progress: Float, velocity: Float, open: Boolean) =
        panelTargetOpen(progress, velocity, currentlyOpen = open)

    @Test
    fun `收起状态只要有一点位移就开`() {
        assertTrue(target(progress = 0.02f, velocity = 0f, open = false))
        assertTrue(target(progress = 0.3f, velocity = 0f, open = false))
    }

    @Test
    fun `收起状态几乎没动才回弹`() {
        assertFalse(target(progress = 0.005f, velocity = 0f, open = false))
    }

    @Test
    fun `收起状态轻轻一拨也开`() {
        assertTrue(target(progress = 0.01f, velocity = 300f, open = false))
    }

    @Test
    fun `收起状态反向甩回就回弹`() {
        assertFalse(target(progress = 0.1f, velocity = -900f, open = false))
    }

    @Test
    fun `展开状态往回拖一点仍然开着`() {
        assertTrue(target(progress = 0.7f, velocity = 0f, open = true))
        assertTrue(target(progress = 0.9f, velocity = 0f, open = true))
    }

    @Test
    fun `展开状态往回拖到 65 及以下就关`() {
        assertFalse(target(progress = 0.65f, velocity = 0f, open = true))
        assertFalse(target(progress = 0.4f, velocity = 0f, open = true))
    }

    @Test
    fun `展开状态朝关闭方向甩，关`() {
        assertFalse(target(progress = 0.95f, velocity = -900f, open = true))
    }

    @Test
    fun `方向为零的极端值不会算错`() {
        assertEquals("收起且进度 0、速度 0 时不该开", false, target(0f, 0f, false))
        assertEquals("展开且进度 1、速度 0 时应保持", true, target(1f, 0f, true))
    }
}
