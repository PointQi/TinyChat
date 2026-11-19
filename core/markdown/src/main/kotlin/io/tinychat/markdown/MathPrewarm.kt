package io.tinychat.markdown

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * 分享长图前的公式预热（M8-2）。
 *
 * 为什么需要：分享卡片是一棵**新建的** Compose 树，公式要重新过一遍
 * `TcMath` 的 LaunchedEffect -- WebView。位图没出来前卡片里是「公式渲染中」占位块，
 * 而占位块高度固定，靠"等高度稳定"根本判不出来（实测拍出来全是占位块）。
 *
 * 这里先把每个块级公式按**和 TcMath 完全相同的参数**渲染一遍，写进 `TcRenderHost` 的缓存；
 * 卡片真正组合时直接命中缓存，一次成图。
 */
object MathPrewarm {

    private val FENCE = Regex("```(?:math|latex)[ \\t]*\\r?\\n([\\s\\S]*?)```", RegexOption.IGNORE_CASE)

    /**
     * 预热 [markdown] 里所有块级公式（`$$…$$`、`\\[…\\]`、```math 围栏）。
     *
     * @param fontPx 与 TcMath 一致：17.sp 在该渲染密度下的像素值
     * @param widthPx 与 TcMath 一致：卡片正文区的可用宽度（像素）
     * @return 成功出图的张数（失败的不抛异常，交给卡片自己降级显示源码）
     */
    suspend fun renderBlocks(
        context: Context,
        markdown: String,
        fontPx: Int,
        widthPx: Int,
        textColor: Color,
        background: Color,
    ): Int {
        val latexList = buildList {
            addAll(findBlockMath(markdown).map { it.latex })
            addAll(FENCE.findAll(markdown).map { it.groupValues[1].trim() })
        }.filter { it.isNotEmpty() }.distinct()
        if (latexList.isEmpty()) return 0

        var rendered = 0
        latexList.forEach { latex ->
            val request = TcRenderHost.Request(
                kind = TcRenderHost.Kind.MATH,
                source = latex,
                display = true,
                fontPx = fontPx.coerceAtLeast(8),
                widthPx = widthPx.coerceAtLeast(1),
                colorHex = "#%06X".format(textColor.toArgb() and 0xFFFFFF),
                dark = background.luminance() < 0.5f,
                naturalWidth = true,
            )
            if (TcRenderHost.render(context, request).bitmap != null) rendered++
        }
        return rendered
    }
}
