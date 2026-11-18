package io.tinychat.markdown

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownDimens
import com.mikepenz.markdown.model.MarkdownPadding
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding
import io.tinychat.designsystem.TcMd
import io.tinychat.designsystem.TcTheme

/**
 * Markdown 令牌映射（docs/02 §10.2 / §13）。
 *
 * 只在这里把 `TcPalette` / `TcMd` 喂给渲染库，业务代码不直接碰渲染库的类型；
 * 这几个函数是 internal，保证第三方类型不泄漏到模块外。
 */

/**
 * 关掉 `includeFontPadding`。
 *
 * 安卓默认会按字体度量在首末行上下各加一段留白，中文 16sp 正文实测每段多出 ~13px，
 * 标题与正文之间就会出现「空得离谱」的观感。关掉之后行高完全由 lineHeight 决定，
 * 间距才是可控的（真机上对比过：同一份内容，块间距从观感 54dp 回落到 20dp 左右）。
 */
private val NoFontPadding = PlatformTextStyle(includeFontPadding = false)

/** 行内代码字号（官方 0.875em ≈ 14sp）。灰条高度也按它算，两边必须用同一个值。 */
internal val InlineCodeFontSize = 14.sp

private fun TextStyle.tight(): TextStyle = copy(platformStyle = NoFontPadding)

@Composable
internal fun tcMarkdownColors(): MarkdownColors {
    val palette = TcTheme.palette
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return markdownColor(
        text = palette.labelPrimary,
        codeBackground = palette.codeBg,
        inlineCodeBackground = palette.inlineCodeBg,
        dividerColor = palette.borderL2,
        tableBackground = Color.Transparent,
        darkTheme = dark,
        highlightText = palette.labelPrimary,
        highlightBackground = palette.brand.copy(alpha = 0.12f),
    )
}

/**
 * @param fontScale 设置里的「字体大小」（1.0–1.3），只作用于正文类语义字号。
 */
@Composable
internal fun tcMarkdownTypography(fontScale: Float = 1f): MarkdownTypography {
    val palette = TcTheme.palette
    fun scaled(style: TextStyle) = TcMd.scaled(style, fontScale)

    // 标题行高比 TcMd 更紧一档：Token 表里的 24/34、22/32、20/30 用在
    // 有大间距的场合（侧栏、设置页），消息流里会显得上下都空。
    val h1 = scaled(TcMd.h1).copy(lineHeight = scaled(TcMd.h1).fontSize * 1.35f).tight()
    val h2 = scaled(TcMd.h2).copy(lineHeight = scaled(TcMd.h2).fontSize * 1.4f).tight()
    val h3 = scaled(TcMd.h3).copy(lineHeight = scaled(TcMd.h3).fontSize * 1.45f).tight()
    val body = scaled(TcMd.body).tight()

    return markdownTypography(
        h1 = h1,
        h2 = h2,
        h3 = h3,
        // 令牌只到 h3：h4 用 h3 的字号但降权，h5/h6 用正文加粗
        h4 = h3.copy(fontWeight = FontWeight.SemiBold),
        h5 = body.copy(fontWeight = FontWeight.Bold),
        h6 = body.copy(fontWeight = FontWeight.Bold),
        text = body,
        code = TcMd.code,
        inlineCode = body.copy(fontFamily = FontFamily.Monospace, fontSize = InlineCodeFontSize),
        quote = body,
        paragraph = body,
        ordered = body,
        bullet = body,
        list = body,
        // 官方 App 的链接是品牌色、常态无下划线
        textLink = TextLinkStyles(style = SpanStyle(color = palette.brand)),
        table = scaled(TcMd.table).tight(),
        alertTitle = body.copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
internal fun tcMarkdownPadding(): MarkdownPadding = markdownPadding(
    // 库会在**每个顶层节点**前插一个这么高的 Spacer，而 GFM 的 AST 每两块之间还夹着
    // 2 个 EOL 节点 —— 设 8dp 实际会变成 24dp。间距全部交给各组件自己加，这里保持 0。
    block = 0.dp,
    list = 8.dp,
    listItemTop = 3.dp,
    listItemBottom = 3.dp,
    // 嵌套缩进：官方 padding-left 18px 是「列表整体」，嵌套每层只需要 1/2–2/3
    listIndent = 10.dp,
    codeBlock = PaddingValues(12.dp),
    // 引用：左侧竖线 + 14dp 内缩，上下各 8dp
    blockQuote = PaddingValues(start = 14.dp, top = 8.dp, bottom = 8.dp),
    blockQuoteText = PaddingValues(vertical = 2.dp),
    blockQuoteBar = PaddingValues.Absolute(left = 0.dp, top = 1.dp, right = 0.dp, bottom = 1.dp),
)

@Composable
internal fun tcMarkdownDimens(): MarkdownDimens = markdownDimens(
    dividerThickness = 1.dp,
    codeBackgroundCornerSize = 12.dp,
    blockQuoteThickness = 2.dp,
    tableCellWidth = 140.dp,
    tableCellPadding = 10.dp,
    tableCornerSize = 8.dp,
)
