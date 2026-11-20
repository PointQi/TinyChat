package io.tinychat.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs

/**
 * 屏幕两侧的面板（P-028）。同一时刻至多一个 —— 用一个变量表示，而不是两个布尔，
 * 互斥因此是结构性的，不依赖任何一段记得清理另一边的代码。
 *
 * - [Sessions] 左抽屉，归 Material3 的 `ModalNavigationDrawer` 管（它可以被手势直接拉出来）；
 * - [Usage] 右抽屉「用量与费用」，自绘，跟手滑入滑出。
 */
enum class SidePanel { None, Sessions, Usage }

/** 松手时走了多少算数：35% 宽度（02 §16 底部抽屉的同一口径）。 */
const val PANEL_SETTLE_FRACTION = 0.35f

/** 甩动的判定速度，dp/s（同上，与底部抽屉一致）。 */
const val PANEL_FLING_DP = 800f

/**
 * 结算速度的上限，单位是「面板宽度/秒」。松手速度来自 `VelocityTracker`，偶尔会有一个尖峰，
 * 限一下免得面板被一记假甩甩飞。
 */
const val PANEL_MAX_SETTLE_VELOCITY = 8f

/** 左滑必须明显偏横才接管，否则竖向翻聊天会被当成开面板。 */
private const val PANEL_SWIPE_RATIO = 1.5f

/**
 * 松手速度（dp/s，向右为正）换算成「进度速度」（1/s，向开为正），并限幅。
 *
 * 进度 1 正好对应一整块面板宽度，所以除以宽度就够；符号要取反：向左甩是打开。
 * 弹簧拿它当初始速度，接缝处才不会从静止重新起步。
 */
fun panelSettleVelocity(velocityDp: Float, panelWidthDp: Float): Float {
    if (panelWidthDp <= 0f) return 0f
    return (-velocityDp / panelWidthDp)
        .coerceIn(-PANEL_MAX_SETTLE_VELOCITY, PANEL_MAX_SETTLE_VELOCITY)
}

/**
 * 松手时该开还是该关（纯函数，可单测）。
 *
 * [progress] 是当前展开进度（0 收起、1 展开），[velocityDp] 是手指的横向速度 dp/s
 * （正 = 向右 = 收起方向），[currentlyOpen] 是松手前那一侧的状态 —— 展开状态下的阈值
 * 是「往回走了 35%」，收起状态下是「往前走了 35%」，两者都用 [PANEL_SETTLE_FRACTION]。
 */
fun panelTargetOpen(
    progress: Float,
    velocityDp: Float,
    currentlyOpen: Boolean,
    fraction: Float = PANEL_SETTLE_FRACTION,
    flingDp: Float = PANEL_FLING_DP,
): Boolean = when {
    velocityDp <= -flingDp -> true
    velocityDp >= flingDp -> false
    currentlyOpen -> progress > 1f - fraction
    else -> progress >= fraction
}

/**
 * 整页左滑拉开右侧面板（P-028）：和左抽屉的整面手势对称。
 *
 * 为什么不是「贴着右边缘起手」：返回手势的触区就在两侧边缘（AOSP 原文为 inward swipe
 * from either edge），从那里起手会被系统当成返回 —— 聊天页是栈底，表现就是直接退出应用。
 * 面板打开时也不检测横向滑动：我们那两块面板都是 `ModalBottomSheet`，它们是真正的 Dialog
 * 窗口，输入根本到不了这一层，所以不需要额外开关。
 *
 * 为什么只会赢在「子级先消费」上：M3 左抽屉的拖拽检测器挂在祖先层，Main 通道里子级先收到
 * 事件；本检测器在同一个 touchSlop 上消费掉左滑，它看到已消费就自行放弃，右滑不消费则原样
 * 交给它。这就是不套第二个 `ModalNavigationDrawer` 的替代做法。
 *
 * @param onDragDelta 相对上一次的水平位移，px（向左为负）
 * @param onDragEnd 松手时的横向速度，dp/s（向右为正）——已换算好，调用方不必再管密度
 */
fun Modifier.swipeLeftToOpenPanel(
    enabled: Boolean,
    onDragStart: () -> Unit,
    onDragDelta: (dxPx: Float) -> Unit,
    onDragEnd: (velocityDp: Float) -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    this.pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startX = down.position.x
            val startY = down.position.y
            var lastX = startX
            var claimed = false
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                // 竖向滚动、自己以外的拖拽已经把事件吃掉了：不是我们的事
                if (change.isConsumed || !change.pressed) break

                if (!claimed) {
                    val dx = change.position.x - startX
                    if (abs(dx) < slop) {
                        tracker.addPosition(change.uptimeMillis, change.position)
                        continue
                    }
                    // 向右是左抽屉的，斜着来的是竖向滚动的
                    if (dx >= 0f || abs(dx) < abs(change.position.y - startY) * PANEL_SWIPE_RATIO) break
                    claimed = true
                    onDragStart()
                    lastX = change.position.x
                    change.consume()
                    continue
                }

                tracker.addPosition(change.uptimeMillis, change.position)
                onDragDelta(change.position.x - lastX)
                lastX = change.position.x
                change.consume()
            }

            if (claimed) onDragEnd(tracker.calculateVelocity().x / density)
        }
    }
}
