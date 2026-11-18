package io.tinychat.markdown

/**
 * 位图裁剪决策（M2-5 收尾）。
 *
 * 出图之后要按「真正有墨迹的矩形」裁掉四周空白。看起来很琐碎，但有两条容易写错、
 * 错了就直接看不出问题的规则：
 *
 * 1. **块级公式横向必须保留整宽**：KaTeX 的 display 模式靠 `text-align:center` 居中，
 *    如果按墨迹裁窄，公式就会贴到左边；
 * 2. **空图不能给 0 尺寸**：Compose 的 `Image` 拿到 0 会抛异常，给 1x1 的透明图。
 */
internal fun cropRect(display: Boolean, ink: IntArray?, viewW: Int, viewH: Int): IntArray {
    if (ink == null || ink.size < 4 || ink[2] <= 0 || ink[3] <= 0) {
        return intArrayOf(0, 0, 1, 1)
    }
    val x = ink[0].coerceIn(0, (viewW - 1).coerceAtLeast(0))
    val y = ink[1].coerceIn(0, (viewH - 1).coerceAtLeast(0))
    return if (display) {
        // 横向整宽（保住居中），纵向按墨迹收紧
        intArrayOf(0, y, viewW.coerceAtLeast(1), ink[3].coerceIn(1, viewH - y))
    } else {
        intArrayOf(
            x,
            y,
            ink[2].coerceIn(1, viewW - x),
            ink[3].coerceIn(1, viewH - y),
        )
    }
}
