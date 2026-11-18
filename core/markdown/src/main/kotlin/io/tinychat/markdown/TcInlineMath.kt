package io.tinychat.markdown

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tinychat.designsystem.TcTheme

/**
 * 行内公式（M2-5 的第二段，A2 的「结束后升级」）。
 *
 * 流式期间：只吞掉 `$` 定界符，宽度稳定、零异步（见 [applyInlineMath]）；
 * 消息结束：把这段里所有公式**一次性**渲成位图，替换成内联内容 —— 只重排一次。
 * 之所以要等结束：内联内容的占位尺寸必须在排版前确定，而位图尺寸是异步才知道的；
 * 边流边换 = 每换一次重排一段，官方 App 长文卡顿就是这么来的。
 *
 * 任何一条公式渲染失败就整段退回源码文字，**不显示半张图**。
 */
@Composable
internal fun InlineMathParagraph(
    built: AnnotatedString,
    formulas: List<InlineMath>,
    streaming: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val palette = TcTheme.palette
    val screenPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }.toInt()
    var images by remember(formulas, streaming) { mutableStateOf<Map<Int, InlineMathImage>?>(null) }

    LaunchedEffect(formulas, streaming) {
        if (streaming || formulas.isEmpty()) {
            images = null
            return@LaunchedEffect
        }
        val fontPx = with(density) { style.fontSize.toPx() }.toInt().coerceAtLeast(10)
        val rendered = LinkedHashMap<Int, InlineMathImage>()
        formulas.forEachIndexed { index, formula ->
            val request = TcRenderHost.Request(
                kind = TcRenderHost.Kind.MATH,
                source = formula.latex,
                display = false,
                fontPx = fontPx,
                widthPx = screenPx.coerceAtLeast(1),
                colorHex = palette.labelPrimary.toHex(),
                dark = palette.bg.luminance() < 0.5f,
            )
            val result = TcRenderHost.render(context, request)
            val bitmap = result.bitmap ?: run {
                images = null
                return@LaunchedEffect
            }
            rendered[index] = InlineMathImage(bitmap, result.widthPx, result.heightPx)
        }
        images = rendered
    }

    val ready = images
    if (ready == null || ready.size != formulas.size) {
        Text(
            text = remember(formulas) { built.applyInlineMath(formulas, replace = false) },
            style = style,
            modifier = modifier,
        )
        return
    }

    val content = remember(ready) {
        formulas.mapIndexed { index, formula ->
            val image = ready.getValue(index)
            inlineMathTag(index) to InlineTextContent(
                Placeholder(
                    width = with(density) { image.widthPx.toDp() }.value.sp,
                    height = with(density) { image.heightPx.toDp() }.value.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline,
                ),
            ) {
                Image(
                    bitmap = image.bitmap,
                    contentDescription = formula.latex,
                    modifier = Modifier.size(
                        width = with(density) { image.widthPx.toDp() },
                        height = with(density) { image.heightPx.toDp() },
                    ),
                )
            }
        }.toMap()
    }
    Text(
        text = remember(formulas) { built.applyInlineMath(formulas, replace = true) },
        style = style,
        inlineContent = content,
        modifier = modifier,
    )
}

internal data class InlineMathImage(val bitmap: androidx.compose.ui.graphics.ImageBitmap, val widthPx: Int, val heightPx: Int)

private fun androidx.compose.ui.graphics.Color.toHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)
