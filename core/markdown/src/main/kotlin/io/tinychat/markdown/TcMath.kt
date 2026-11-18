package io.tinychat.markdown

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcTypography

/**
 * 公式的显示（M2-5）：块级（`$$…$$`）与行内（`$…$`）共用。
 *
 * 渲染交给 [TcRenderHost]（隐藏 WebView 出位图），这里只做三件事：
 * 占位要**尺寸稳定**、位图就绪后一次性换上去、失败时退回等宽源码。
 */
@Composable
internal fun TcMath(
    latex: String,
    display: Boolean,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = if (display) 17.sp else 15.sp,
) {
    if (display) {
        TcMathDisplay(latex = latex, modifier = modifier, fontSize = fontSize)
    } else {
        TcMathInline(latex = latex, fontSize = fontSize, modifier = modifier)
    }
}

@Composable
private fun TcMathDisplay(latex: String, modifier: Modifier, fontSize: TextUnit) {
    val palette = TcTheme.palette
    val density = LocalDensity.current
    val context = LocalContext.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        val widthPx = with(density) { maxWidth.toPx().toInt() }.coerceAtLeast(1)
        val fontPx = with(density) { fontSize.toPx().toInt() }.coerceAtLeast(8)
        val request = remember(latex, fontPx, widthPx, palette.labelPrimary) {
            mathRequest(latex, display = true, fontPx, widthPx, palette.labelPrimary, palette.bg)
        }
        var result by remember(request) { mutableStateOf(TcRenderHost.cached(request)) }
        LaunchedEffect(request) {
            if (result?.bitmap == null) result = TcRenderHost.render(context, request)
        }

        val bitmap = result?.bitmap
        when {
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = latex,
                modifier = Modifier.size(
                    width = with(density) { bitmap.width.toDp() },
                    height = with(density) { bitmap.height.toDp() },
                ),
            )

            result?.error != null -> MathSource(latex, display = true, error = true)
            else -> MathPlaceholder()
        }
    }
}

@Composable
private fun TcMathInline(latex: String, fontSize: TextUnit, modifier: Modifier) {
    val palette = TcTheme.palette
    val density = LocalDensity.current
    val context = LocalContext.current
    // 行内公式在内联内容里拿不到「段落宽」，KaTeX 自己按内容量宽，给屏幕宽兜底即可
    val screenWidthPx = with(density) {
        LocalConfiguration.current.screenWidthDp.dp.toPx().toInt()
    }.coerceAtLeast(1)

    val fontPx = with(density) { fontSize.toPx().toInt() }.coerceAtLeast(8)
    val request = remember(latex, fontPx, screenWidthPx, palette.labelPrimary) {
        mathRequest(latex, display = false, fontPx, screenWidthPx, palette.labelPrimary, palette.bg)
    }
    var result by remember(request) { mutableStateOf(TcRenderHost.cached(request)) }
    LaunchedEffect(request) {
        if (result?.bitmap == null) result = TcRenderHost.render(context, request)
    }

    val bitmap = result?.bitmap
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = latex,
            modifier = modifier.size(
                width = with(density) { bitmap.width.toDp() },
                height = with(density) { bitmap.height.toDp() },
            ),
        )
    } else {
        MathSource(latex, display = false, error = result?.error != null)
    }
}

private fun mathRequest(
    latex: String,
    display: Boolean,
    fontPx: Int,
    widthPx: Int,
    textColor: Color,
    background: Color,
) = TcRenderHost.Request(
    kind = TcRenderHost.Kind.MATH,
    source = latex,
    display = display,
    fontPx = fontPx,
    widthPx = widthPx,
    colorHex = textColor.toHex(),
    dark = background.luminance() < 0.5f,
)

/** 位图出来之前的兜底：等宽源码，宽度与行高都稳定，不会把下面顶来顶去 */
@Composable
private fun MathSource(latex: String, display: Boolean, error: Boolean) {
    val palette = TcTheme.palette
    Text(
        text = latex,
        fontFamily = FontFamily.Monospace,
        fontSize = if (display) 15.sp else 14.sp,
        color = if (error) palette.labelTertiary else palette.labelSecondary,
        modifier = Modifier
            .then(if (display) Modifier.fillMaxWidth() else Modifier)
            .padding(vertical = if (display) 4.dp else 0.dp),
        textAlign = if (display) TextAlign.Center else TextAlign.Start,
    )
}

/** 块级公式底板：固定高度，避免「先矮后高」把下方内容顶一下 */
@Composable
private fun MathPlaceholder() {
    val palette = TcTheme.palette
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(palette.inlineCodeBg.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "公式渲染中",
            style = TcTypography.bodySmall,
            color = palette.labelTertiary,
        )
    }
}

private fun Color.toHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)
