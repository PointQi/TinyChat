package io.tinychat.markdown

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.buildAnnotatedString
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.listDepth
import com.mikepenz.markdown.compose.elements.MarkdownBlockQuote
import com.mikepenz.markdown.compose.elements.MarkdownBulletList
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.compose.elements.MarkdownDivider
import com.mikepenz.markdown.compose.elements.MarkdownOrderedList
import com.mikepenz.markdown.compose.elements.MarkdownParagraph
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownText
import io.tinychat.designsystem.TcTheme
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.getTextInNode

/**
 * Markdown 组件的官方化改造（M2-1 调优）。三件事：
 *
 * 1. **块间距自己控**：库给「每个顶层节点」前面插 `Spacer(padding.block)`，而 GFM 的 AST
 *    在每两个块之间夹着 **2 个 EOL 节点**，等于一次块间距被乘三（实测标题下方 54dp）。
 *    所以 `block` 置 0，改由各组件自己加内边距 —— 间距从此可预测、也不会随 AST 抖动；
 * 2. `blockQuote`：库把竖线和正文都画成正文色（黑），官方是**灰竖线 + 灰字**；
 * 3. `checkbox`：库默认只是画一段等宽文字 `[x]`，不是真的勾选框。
 */
@Composable
internal fun tcMarkdownComponents(): MarkdownComponents = markdownComponents(
    paragraph = { model ->
        TcParagraph(model)
    },
    orderedList = { model ->
        // 列表项里的段落不再额外加块间距，否则每项之间会空出 12dp
        CompositionLocalProvider(LocalBlockSpacing provides 0.dp) {
            MarkdownOrderedList(
                content = model.content,
                node = model.node,
                style = model.typography.ordered,
                depth = model.listDepth,
                markerModifier = listMarkerModifier,
            )
        }
    },
    unorderedList = { model ->
        CompositionLocalProvider(LocalBlockSpacing provides 0.dp) {
            MarkdownBulletList(
                content = model.content,
                node = model.node,
                style = model.typography.bullet,
                depth = model.listDepth,
                markerModifier = listMarkerModifier,
            )
        }
    },
    heading1 = { model -> TcHeading(model, model.typography.h1, MarkdownTokenTypes.ATX_CONTENT, top = 14.dp) },
    heading2 = { model -> TcHeading(model, model.typography.h2, MarkdownTokenTypes.ATX_CONTENT, top = 14.dp) },
    heading3 = { model -> TcHeading(model, model.typography.h3, MarkdownTokenTypes.ATX_CONTENT, top = 12.dp) },
    heading4 = { model -> TcHeading(model, model.typography.h4, MarkdownTokenTypes.ATX_CONTENT, top = 10.dp) },
    heading5 = { model -> TcHeading(model, model.typography.h5, MarkdownTokenTypes.ATX_CONTENT, top = 10.dp) },
    heading6 = { model -> TcHeading(model, model.typography.h6, MarkdownTokenTypes.ATX_CONTENT, top = 10.dp) },
    setextHeading1 = { model -> TcHeading(model, model.typography.h1, MarkdownTokenTypes.SETEXT_CONTENT, top = 14.dp) },
    setextHeading2 = { model -> TcHeading(model, model.typography.h2, MarkdownTokenTypes.SETEXT_CONTENT, top = 14.dp) },
    blockQuote = { model -> TcBlockQuote(model) },
    checkbox = { model -> TcCheckbox(model) },
    codeFence = { model ->
        MarkdownCodeFence(model.content, model.node, model.typography.code) { code, language, style ->
            TcCodeBlock(code = code, language = language, style = style)
        }
    },
    codeBlock = { model ->
        MarkdownCodeBlock(model.content, model.node, model.typography.code) { code, language, style ->
            TcCodeBlock(code = code, language = language, style = style)
        }
    },
    table = { model ->
        Box(modifier = Modifier.padding(vertical = 8.dp)) {
            MarkdownTable(content = model.content, node = model.node, style = model.typography.table)
        }
    },
    horizontalRule = {
        MarkdownDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
        )
    },
)

/**
 * 块与块之间的竖直间距；列表项内部改成 0（列表自己用 listItemTop/Bottom 控节奏）。
 */
internal val LocalBlockSpacing = staticCompositionLocalOf { 6.dp }

/** 列表符号与正文之间的空隙：比库自带的 `"• "` 后面的空格更紧凑可控 */
private val listMarkerModifier: RowScope.() -> Modifier = { Modifier.padding(end = 6.dp) }

/**
 * 段落：库默认只把文字交给 MarkdownParagraph；这里多一步 —— 把落在本段范围内的引用锚点
 * 以**内联占位符**贴到段末（`appendInlineContent` 不带 altText，插入的是占位符而不是数字）。
 */
@Composable
private fun TcParagraph(model: MarkdownComponentModel) {
    val anchors = LocalCitationAnchors.current
    val mine = anchorsIn(anchors, model.node.startOffset, model.node.endOffset)
    val settings = annotatorSettings()
    val text = buildAnnotatedString {
        pushStyle(model.typography.paragraph.toSpanStyle())
        buildMarkdownAnnotatedString(
            content = model.content,
            node = model.node,
            annotatorSettings = settings,
        )
        pop()
        mine.forEach { anchor -> appendInlineContent(CitationTagPrefix + anchor.index) }
    }

    MarkdownText(
        content = text,
        node = model.node,
        modifier = Modifier.padding(vertical = LocalBlockSpacing.current),
        style = model.typography.paragraph,
    )
}

@Composable
private fun TcHeading(
    model: MarkdownComponentModel,
    style: TextStyle,
    contentChildType: IElementType,
    top: Dp,
) {
    MarkdownText(
        content = model.content,
        node = model.node,
        style = style,
        modifier = Modifier
            .semantics { heading() }
            .padding(top = top, bottom = 6.dp),
        contentChildType = contentChildType,
    )
}

@Composable
private fun TcBlockQuote(model: MarkdownComponentModel) {
    val palette = TcTheme.palette
    val base = LocalMarkdownTypography.current
    // 引用里的正文、列表、标题一律走三级灰；代码块保持原样
    val muted = base.paragraph.copy(color = palette.labelTertiary)
    val nested = defaultTypographyWith(
        base = base,
        text = muted,
        heading = muted,
        table = muted,
    )
    CompositionLocalProvider(LocalMarkdownTypography provides nested) {
        MarkdownBlockQuote(model.content, model.node, style = muted)
    }
}

private fun defaultTypographyWith(
    base: com.mikepenz.markdown.model.MarkdownTypography,
    text: TextStyle,
    heading: TextStyle,
    table: TextStyle,
): com.mikepenz.markdown.model.MarkdownTypography = com.mikepenz.markdown.model.DefaultMarkdownTypography(
    h1 = heading, h2 = heading, h3 = heading, h4 = heading, h5 = heading, h6 = heading,
    text = text,
    code = base.code,
    inlineCode = base.inlineCode,
    quote = text,
    paragraph = text,
    ordered = text,
    bullet = text,
    list = text,
    textLink = base.textLink,
    table = table,
    alertTitle = text.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
)

@Composable
private fun TcCheckbox(model: MarkdownComponentModel) {
    val checked = model.node.getTextInNode(model.content).contains("[x]")
    val palette = TcTheme.palette
    val boxColor = if (checked) palette.brand else palette.labelTertiary
    val tickColor = palette.brandOn

    Box(modifier = Modifier.padding(top = 5.dp, end = 8.dp)) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val radius = CornerRadius(4.dp.toPx())
            val strokeWidth = 1.5.dp.toPx()
            if (checked) {
                drawRoundRect(color = boxColor, cornerRadius = radius)
                val tick = Path().apply {
                    moveTo(size.width * 0.26f, size.height * 0.52f)
                    lineTo(size.width * 0.43f, size.height * 0.69f)
                    lineTo(size.width * 0.75f, size.height * 0.33f)
                }
                drawPath(
                    path = tick,
                    color = tickColor,
                    style = Stroke(width = strokeWidth * 1.25f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            } else {
                drawRoundRect(color = boxColor, cornerRadius = radius, style = Stroke(width = strokeWidth))
            }
        }
    }
}
