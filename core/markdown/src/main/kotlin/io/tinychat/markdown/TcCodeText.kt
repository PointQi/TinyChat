package io.tinychat.markdown

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.tinychat.designsystem.TcSyntaxColors
import io.tinychat.designsystem.TcTheme

/**
 * 等宽代码区（M2-4）：横向滚动、不换行、按语言着色。
 *
 * 代码块和 Mermaid 的源码视图共用这一个组件，免得两处观感走偏
 * （曾经 Mermaid 的源码视图和代码块是两个独立的 Text，改一处就会漏一处）。
 */
@Composable
internal fun TcCodeText(code: String, language: SyntaxLanguage, style: TextStyle) {
    val palette = TcTheme.palette
    val text = remember(code, language, palette) {
        annotatedCode(code, language, palette.syntax)
    }
    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Text(
            text = text,
            style = style.copy(fontFamily = FontFamily.Monospace),
            color = palette.labelPrimary,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

/**
 * 代码 + 着色片段 -> 带样式的字符序列。纯函数，方便和 [highlight] 分开测。
 *
 * 片段理论上不会重叠也不会越界，这里仍然收一次边界：着色器出错时画面应该退化成
 * 少一点颜色，而不是整段帖子渲染崩掉。
 */
internal fun annotatedCode(
    code: String,
    language: SyntaxLanguage,
    colors: TcSyntaxColors,
): AnnotatedString {
    val tokens = highlight(code, language)
    if (tokens.isEmpty()) return AnnotatedString(code)
    return buildAnnotatedString {
        append(code)
        for (token in tokens) {
            val start = token.start.coerceIn(0, code.length)
            val end = token.end.coerceIn(start, code.length)
            if (start < end) addStyle(SpanStyle(color = colors.forKind(token.kind)), start, end)
        }
    }
}

private fun TcSyntaxColors.forKind(kind: TokenKind): Color = when (kind) {
    TokenKind.KEYWORD -> keyword
    TokenKind.STRING -> string
    TokenKind.NUMBER -> number
    TokenKind.COMMENT -> comment
    TokenKind.PUNCTUATION -> punctuation
    TokenKind.ANNOTATION -> annotation
    TokenKind.PROPERTY -> property
}
