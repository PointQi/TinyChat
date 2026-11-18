package io.tinychat.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.model.MarkdownInlineContent
import com.mikepenz.markdown.model.markdownInlineContent
import io.tinychat.designsystem.TcTheme

/**
 * 正文里的一个引用锚点：在全部正文的第 [offset] 个字符处插一个上标角标，编号 [index]（1 基）。
 *
 * 为什么用「偏移量」而不是在正文里塞 `[citation:N]` 标记：
 * 服务端的引用走 `citations_delta`，**不写进正文**；但它必然紧跟在被引用的那段文字之后，
 * 且与该段文字属于同一个 RESPONSE 碎片，所以拼正文时按碎片末尾记下偏移就是精确位置，
 * 既不用改协议、也不用给导出格式加字段。
 *
 * 落位规则：**贴在所属段落的末尾**（角标表示「这一段的出处」）。真实数据里锚点几乎都落在
 * 段落边界上，按文本节点插会让它们整批丢失（实测过）。
 */
data class CitationAnchor(val offset: Int, val index: Int)

internal const val CitationTagPrefix = "tc_citation_"

/** 引用角标外形：三圆一直角（官方 `border-radius: 6px 6px 6px 0`） */
private val CitationChipShape = RoundedCornerShape(
    topStartPercent = 50,
    topEndPercent = 50,
    bottomEndPercent = 50,
    bottomStartPercent = 0,
)

/** 当前正文的引用锚点，供段落组件取用 */
internal val LocalCitationAnchors = staticCompositionLocalOf<List<CitationAnchor>> { emptyList() }

/** 属于 [start, end) 这个段落的锚点（纯函数，便于单测） */
internal fun anchorsIn(anchors: List<CitationAnchor>, start: Int, end: Int): List<CitationAnchor> =
    if (anchors.isEmpty()) {
        emptyList()
    } else {
        anchors.filter { it.offset > start && it.offset <= end }
    }

/** 角标本体：10sp 数字、圆角、`InlineCodeBg` 底色，上标对齐 */
@Composable
internal fun citationInlineContent(
    anchors: List<CitationAnchor>,
    onClick: ((Int) -> Unit)?,
): MarkdownInlineContent {
    if (anchors.isEmpty()) return markdownInlineContent()
    val palette = TcTheme.palette
    val content = anchors.associate { anchor ->
        (CitationTagPrefix + anchor.index) to InlineTextContent(
            Placeholder(
                width = 15.sp,
                height = 15.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline,
            ),
        ) {
            Box(
                modifier = Modifier
                    // 撑满占位符，否则底色只有数字那么大，看着像裸数字
                    .fillMaxSize()
                    // 官方样式是 `border-radius: 6px 6px 6px 0`：三个角圆、左下角是直角，
                    // 看起来像一个左下角带尖的气泡
                    .clip(CitationChipShape)
                    .background(palette.inlineCodeBg)
                    .then(if (onClick != null) Modifier.clickable { onClick(anchor.index) } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = anchor.index.toString(),
                    fontSize = 9.5.sp,
                    lineHeight = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.labelSecondary,
                )
            }
        }
    }
    return markdownInlineContent(content)
}

/** 来源聚合胶囊上的一枚站点图标（首字母色块，真 favicon 要等 M3 接 Coil） */
@Composable
fun SourceBadge(letter: String, size: Int = 16) {
    val palette = TcTheme.palette
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(palette.bgLayer2)
            .then(Modifier.size(size.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            fontSize = (size * 0.55f).sp,
            lineHeight = (size * 0.55f).sp,
            color = palette.labelSecondary,
            fontWeight = FontWeight.Medium,
        )
    }
}
