package io.tinychat.markdown

import kotlin.math.max

/**
 * 行内公式的排版度量（M2-5 收尾）。
 *
 * 分两步：先把「位图像素」折算成排版单位，再按这些度量算段落行高。
 * 之所以要有这一层纯函数：这两个换算都容易写错，而错一点点就是「公式高出半行」
 * 或「分母被行高切掉」，只能靠单测钉死。
 *
 * **单位口径**：Compose 的 `Placeholder` 用 sp 描述占位尺寸，渲染时会再乘一次
 * `fontScale`（系统字体大小）。所以这里的「sp 数值」= px / (density × fontScale)。
 */

/**
 * 行内公式的放大系数。
 *
 * KaTeX 行内按 1em 排，数学字体的 x-height 天生比中文正文字体小，1:1 出图会显得
 * 「小一号」。**放大字号重排**（不是把位图缩放）——缩放会让数学字体糊掉。
 */
internal const val InlineMathFontScale = 1.16f

/** 一张行内公式位图的排版度量，全部是 sp 数值 */
internal data class InlineMathMetrics(
    val widthSp: Float,
    val heightSp: Float,
    /** 基线以上的高度；等于 [heightSp] 表示整张图都在基线之上（没量到基线时的退化态） */
    val aboveBaselineSp: Float,
) {
    /** 基线以下的下伸高度 */
    val belowBaselineSp: Float get() = (heightSp - aboveBaselineSp).coerceAtLeast(0f)
}

/**
 * @param baselinePx 基线在位图中的 y（物理 px）；0 或越界表示没量到
 * @param pxPerSp 1 sp 等于多少物理 px（= density × fontScale）
 */
internal fun inlineMathMetrics(
    widthPx: Int,
    heightPx: Int,
    baselinePx: Int,
    pxPerSp: Float,
): InlineMathMetrics {
    val perSp = if (pxPerSp > 0f) pxPerSp else 1f
    val h = heightPx.coerceAtLeast(1) / perSp
    val above = if (baselinePx in 1 until heightPx) baselinePx / perSp else h
    return InlineMathMetrics(
        widthSp = widthPx.coerceAtLeast(1) / perSp,
        heightSp = h,
        aboveBaselineSp = above.coerceIn(0f, h),
    )
}

/**
 * 段落行高：含行内公式时按「最高的上半 + 最深的下伸 + 一点余量」撑开。
 *
 * 为什么要撑：Compose 的行高由 `TextStyle.lineHeight` 定死，内联内容再高也不会把行撑开，
 * 高公式（分式、矩阵、求和号）就会压到上一行、或被下一行盖住。
 * **只撑含公式的段落** —— 普通段落仍是原行高，否则整页都会变松。
 */
internal fun inlineMathLineHeight(
    baseLineHeightSp: Float,
    metrics: Collection<InlineMathMetrics>,
    gapSp: Float,
): Float {
    if (metrics.isEmpty()) return baseLineHeightSp
    val above = metrics.maxOf { it.aboveBaselineSp }
    val below = metrics.maxOf { it.belowBaselineSp }
    return max(baseLineHeightSp, above + below + gapSp)
}
