package io.tinychat.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize

/**
 * 菜单落点的纯计算：给定锚点矩形、菜单尺寸、窗口尺寸，算出菜单左上角的窗口坐标。
 *
 * 规则（`06` §二）：
 * - 水平：以锚点中心对齐，左右各留 [margin]，超界就往里推；
 * - 垂直：默认贴在锚点上方、留 [gap]；上方放不下就翻到下方；上下都放不下就取空间大的一侧，再钳进窗口。
 *
 * 抽成纯函数是为了能在 JVM 上把边界跑完（8 条单测），不必上真机拿手指试。
 */
internal fun menuOffset(
    anchor: IntRect,
    popup: IntSize,
    window: IntSize,
    gap: Int,
    margin: Int,
): IntOffset {
    val maxX = (window.width - margin - popup.width).coerceAtLeast(margin)
    val x = (anchor.center.x - popup.width / 2).coerceIn(margin, maxX)

    val above = anchor.top - gap - popup.height
    val below = anchor.bottom + gap
    val maxY = (window.height - margin - popup.height).coerceAtLeast(margin)

    val y = when {
        above >= margin -> above
        below <= maxY -> below
        // 上下都放不下：比较两侧剩余空间，取大的那边
        anchor.top >= window.height - anchor.bottom -> margin
        else -> maxY
    }
    return IntOffset(x, y.coerceIn(margin, maxY))
}
