package io.tinychat.markdown

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString

/**
 * 行内公式 `$…$`（M2-5）。
 *
 * 为什么行内公式这么绕：KaTeX 只能跑在 WebView 里，而行内公式夹在文字流中间。
 * 每出现一个就异步换成位图 = 每换一次宽度都让整段重排一次（官方 App 长文里卡成那样，
 * 主要就是这笔账）。所以这里分两步走：
 *
 * - **流式中**：只把定界符去掉，内容原地显示 —— 宽度稳定、零异步、零重排；
 * - **流式结束**：把每个公式换成内联位图（KaTeX 真像素），一次批量替换，只重排一次。
 *
 * 本文件只放**纯逻辑**（找范围、替换文字、迁移 span），真正的图在 [TcMath] 里出。
 */
internal data class InlineMath(
    /** 在源串里的范围，左闭右开，含两个 `$` */
    val start: Int,
    val end: Int,
    val latex: String,
)

/** 替换后的结果：文本 + 公式列表（顺序即占位符 id 的后缀） */
internal data class InlineMathText(
    val text: AnnotatedString,
    val formulas: List<InlineMath>,
)

internal fun inlineMathTag(index: Int) = "tc_math_$index"

/** 段落里的一段块级公式（`$$…$$`），坐标相对段落文本 */
internal data class BlockMath(val start: Int, val end: Int, val latex: String)

/**
 * 找段落内的块级公式。两种定界符都认：
 * - `$$…$$`（GFM/常见写法）
 * - `\[…\]`（LaTeX 写法，**DeepSeek 的模型主要用这个**）
 *
 * 允许跨行（模型经常把长公式折成多行），但不允许嵌套；空内容跳过。
 * 注意这里吃的是**原始文本**（`getTextInNode` 的结果），反斜杠还在 —— 行内 `\(…\)`
 * 却必须先归一成 `$…$`，因为 markdown 会吃掉反斜杠，见 [normalizeInlineMathDelimiters]。
 */
internal fun findBlockMath(text: String): List<BlockMath> {
    val result = mutableListOf<BlockMath>()
    var cursor = 0
    while (true) {
        val dollar = text.indexOf("$$", cursor)
        val bracket = text.indexOf("\\[", cursor)
        if (dollar < 0 && bracket < 0) break
        val useBracket = bracket >= 0 && (dollar < 0 || bracket < dollar)
        val open = if (useBracket) bracket else dollar
        val closeToken = if (useBracket) "\\]" else "$$"
        val openLen = 2
        val close = text.indexOf(closeToken, open + openLen)
        if (close < 0) {
            // 没闭合（流式中间态）：跳过这个开口继续往后找
            cursor = open + openLen
            continue
        }
        val latex = text.substring(open + openLen, close).trim()
        if (latex.isNotEmpty()) {
            result += BlockMath(open, close + closeToken.length, latex)
        }
        cursor = close + closeToken.length
    }
    return result
}

/**
 * 扫描 `$…$`。规则按实际使用习惯收紧，避免把美元金额吃掉：
 * - 开头 `$` 后不能是空白；结尾 `$` 前不能是空白；
 * - 结尾 `$` 后面不能紧跟字母/数字（`$5`、`US$`）；
 * - 内容不跨空行，且不能含反引号（那是行内代码）。
 */
internal fun findInlineMath(text: String): List<InlineMath> {
    val result = mutableListOf<InlineMath>()
    var i = 0
    while (i < text.length) {
        val open = text.indexOf('$', i)
        if (open < 0) break
        // `\$` 是转义的美元符号
        if (open > 0 && text[open - 1] == '\\') { i = open + 1; continue }
        val close = findClosing(text, open)
        if (close < 0) { i = open + 1; continue }
        result += InlineMath(open, close + 1, text.substring(open + 1, close))
        i = close + 1
    }
    return result
}

private fun findClosing(text: String, open: Int): Int {
    var j = open + 1
    while (j < text.length) {
        val c = text[j]
        if (c == '\\') { j += 2; continue }
        if (c == '`') return -1
        if (c == '\n' && (j + 1 >= text.length || text[j + 1] == '\n')) return -1
        if (c == '$') {
            val inner = text.substring(open + 1, j)
            if (inner.isEmpty() || inner.first().isWhitespace() || inner.last().isWhitespace()) return -1
            if (j + 1 < text.length && text[j + 1].isLetterOrDigit()) return -1
            return if (inner.all { it.isDigit() || it == ',' || it == '.' }) -1 else j
        }
        j++
    }
    return -1
}

/**
 * 把段落里的公式换成占位符（[replace] = true）或只吞掉定界符（[replace] = false），
 * 同时把原有 span 迁移到新坐标上。
 */
internal fun AnnotatedString.applyInlineMath(
    formulas: List<InlineMath>,
    replace: Boolean,
): AnnotatedString {
    if (formulas.isEmpty()) return this

    val builder = AnnotatedString.Builder()
    val map = IntArray(text.length + 1)   // 老坐标 → 新坐标
    var cursor = 0

    fun appendPlain(from: Int, to: Int) {
        for (k in from until to) {
            builder.append(text[k])
            map[k + 1] = builder.length
        }
    }

    formulas.forEachIndexed { index, formula ->
        appendPlain(cursor, formula.start)
        val tag = inlineMathTag(index)
        if (replace) {
            builder.appendInlineContent(tag)
            // 被吞掉的原文位置都映射到占位符之后，span 端点落进来也不会越界
            for (k in formula.start..formula.end) map[k] = builder.length
        } else {
            builder.append(formula.latex)
            map[formula.start] = builder.length - formula.latex.length
            for (k in formula.start + 1..formula.end) map[k] = builder.length
        }
        cursor = formula.end
    }
    appendPlain(cursor, text.length)
    map[0] = 0

    fun mapped(start: Int, end: Int): Pair<Int, Int> {
        val s = map[start.coerceIn(0, text.length)]
        val e = map[end.coerceIn(0, text.length)].coerceAtLeast(s)
        return s to e
    }

    spanStyles.forEach { span ->
        val (s, e) = mapped(span.start, span.end)
        if (e > s) builder.addStyle(span.item, s, e)
    }
    paragraphStyles.forEach { style ->
        val (s, e) = mapped(style.start, style.end)
        if (e > s) builder.addStyle(style.item, s, e)
    }
    // 链接注解不迁移：含公式的段落里链接失效的概率很低，而 LinkAnnotation 的
    // 子类结构在 Compose 各版本间变过好几次，宁可不碰 —— 真出问题再按版本来补。
    return builder.toAnnotatedString()
}
