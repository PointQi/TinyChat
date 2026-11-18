package io.tinychat.markdown

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.extendedspans.ExtendedSpanPainter
import com.mikepenz.markdown.compose.extendedspans.SpanDrawInstructions

/**
 * 行内代码的底色画笔（M2-1 调优）。
 *
 * 为什么不用库自带的 `RoundedCornerSpanPainter`：它把文字排版出来的**行盒**当作矩形，
 * 行盒高度 = lineHeight（16sp 正文用的是 28sp 行高），于是 28sp 高的灰条压在 16sp 的字上，
 * 再靠 topMargin/bottomMargin 硬裁 —— 首行/续行裁得不一样，就出现「首行太矮把 l 切了、
 * 续行太高吃了下面的字」。
 *
 * 这里的做法：**高度只跟字号挂钩**，以这一行的基线为锚点：
 * - 高 = 字号 × [heightRatio]（默认 1.35），底边在基线下方 [descentRatio] × 字号处；
 * - 圆角、左右内边距按 dp 给，与行高、换行位置、字体缩放都无关；
 * - 跨行时每行各画一段，只有首段的左边、末段的右边有圆角，两段严丝合缝拼成一条。
 */
internal class TcInlineCodePainter(
    private val cornerRadius: Dp = 4.dp,
    /**
     * 左右内边距。等宽字形的自身留白（side bearing）已经占掉半个字符宽，
     * 再补 dp 就会显得松，所以默认贴着字形前进宽度画，不再额外撑开。
     */
    private val horizontalPadding: Dp = 0.dp,
    /** 灰条高度 = 字号 × 这个比例；不跟随行高，换行与首末行都一致 */
    private val heightRatio: Float = 1.35f,
    /** 底边落在基线下方 = 字号 × 这个比例 */
    private val descentRatio: Float = 0.24f,
    /** 参与计算的字号；默认跟 [`TcMd.code`] 的行内代码字号一致 */
    private val fontSize: TextUnit = 15.sp,
) : ExtendedSpanPainter() {

    override fun decorate(
        span: SpanStyle,
        start: Int,
        end: Int,
        text: AnnotatedString,
        builder: AnnotatedString.Builder,
    ): SpanStyle {
        val background = span.background
        if (background.isUnspecified) return span
        builder.addStringAnnotation(TAG, background.toArgb().toString(), start, end)
        return span.copy(background = Color.Unspecified)
    }

    override fun decorate(
        linkAnnotation: LinkAnnotation,
        start: Int,
        end: Int,
        text: AnnotatedString,
        builder: AnnotatedString.Builder,
    ): LinkAnnotation = linkAnnotation

    override fun drawInstructionsFor(layoutResult: TextLayoutResult, color: Color?): SpanDrawInstructions {
        val text = layoutResult.layoutInput.text
        val annotations = text.getStringAnnotations(TAG, 0, text.length)
        val chipFontSize = fontSize

        return SpanDrawInstructions {
            if (annotations.isEmpty()) return@SpanDrawInstructions
            val fontPx = chipFontSize.toPx()
            // 圆角按 dp；高度和基线偏移按字号，保证任意行高下形状一致
            val radius = cornerRadius.toPx()
            val horizontalPad = horizontalPadding.toPx()
            val boxHeight = fontPx * heightRatio
            val belowBaseline = fontPx * descentRatio
            val path = Path()

            annotations.forEach { annotation ->
                if (annotation.end <= annotation.start) return@forEach
                val boxes = layoutResult.getBoundingBoxes(annotation.start, annotation.end)
                if (boxes.isEmpty()) return@forEach
                val background = Color(annotation.item.toInt())

                // getBoundingBoxes 是按行顺序返回的，所以第 i 个盒子的行号 = 起始行 + i
                val firstLine = layoutResult.getLineForOffset(annotation.start)
                boxes.forEachIndexed { index, box ->
                    val baseline = layoutResult.getLineBaseline(firstLine + index)
                    val top = baseline + belowBaseline - boxHeight
                    val bottom = baseline + belowBaseline
                    val left = box.left - horizontalPad
                    val right = box.right + horizontalPad
                    val first = index == 0
                    val last = index == boxes.lastIndex
                    path.rewind()
                    path.addRoundRect(
                        RoundRect(
                            rect = Rect(left = left, top = top, right = right, bottom = bottom),
                            topLeft = if (first) CornerRadius(radius) else CornerRadius.Zero,
                            bottomLeft = if (first) CornerRadius(radius) else CornerRadius.Zero,
                            topRight = if (last) CornerRadius(radius) else CornerRadius.Zero,
                            bottomRight = if (last) CornerRadius(radius) else CornerRadius.Zero,
                        )
                    )
                    drawPath(path = path, color = background, style = Fill)
                }
            }
        }
    }

    private companion object {
        const val TAG = "tinychat_inline_code"
    }
}
