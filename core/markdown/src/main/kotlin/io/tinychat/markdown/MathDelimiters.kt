package io.tinychat.markdown

/**
 * 把 `\(…\)` 归一成 `$…$`（M2-3 之后补的兼容层）。
 *
 * **为什么必须做**：模型（尤其 DeepSeek）给的 LaTeX 常用 `\(…\)` 行内、`\[…\]` 块级，
 * 而 GFM 会把 `\(` 当成「转义左括号」直接吃掉反斜杠 —— 等到构建 AnnotatedString 时，
 * 定界符已经变成普通括号，就再也认不出这是公式了（真机上表现为「公式原样糊在正文里」）。
 * 所以在**交给解析器之前**先把行内定界符换成 `$`，后续的行内公式链路就能照常工作。
 *
 * 块级 `\[…\]` 不需要这一步：块级识别走的是**原始文本**，反斜杠还在（见 [findBlockMath]）。
 *
 * 换定界符会让长度变短，而引用角标的偏移是对着原文算的 —— 所以这里同时给出
 * [NormalizedMathText.mapOffset]，调用方把锚点一起平移。
 */
internal class NormalizedMathText(val text: String, private val edits: List<IntArray>) {

    /** 原文坐标 → 归一后坐标；落在被替换区间内的一律折到替换起点 */
    fun mapOffset(offset: Int): Int {
        var shift = 0
        for (edit in edits) {
            val start = edit[0]
            val end = edit[1]
            val delta = edit[2]
            if (offset <= start) break
            if (offset >= end) {
                shift += delta
            } else {
                return (start + shift).coerceAtLeast(0)
            }
        }
        return (offset + shift).coerceAtLeast(0)
    }
}

/**
 * @return 归一后的文本 + 偏移映射；没有 `\(` 时原样返回（零拷贝）。
 */
internal fun normalizeInlineMathDelimiters(content: String): NormalizedMathText {
    if (!content.contains("\\(")) return NormalizedMathText(content, emptyList())

    val out = StringBuilder(content.length)
    val edits = mutableListOf<IntArray>()
    var i = 0
    var inFence = false
    var inCode = false
    var atLineStart = true

    while (i < content.length) {
        val c = content[i]

        if (atLineStart) {
            val lineEnd = content.indexOf('\n', i).let { if (it < 0) content.length else it }
            val head = content.substring(i, lineEnd).trimStart()
            if (head.startsWith("```") || head.startsWith("~~~")) inFence = !inFence
            atLineStart = false
        }
        if (c == '\n') {
            atLineStart = true
            inCode = false
            out.append(c)
            i++
            continue
        }
        // 行内代码里的 `\(` 是字面量，不能换
        if (c == '`' && !inFence) {
            inCode = !inCode
            out.append(c)
            i++
            continue
        }
        val escapedOpen = c == '\\' && i + 1 < content.length && content[i + 1] == '(' &&
            (i == 0 || content[i - 1] != '\\')
        if (!inFence && !inCode && escapedOpen) {
            val close = findCloseParen(content, i + 2)
            if (close > 0) {
                val inner = content.substring(i + 2, close).trim()
                val oldLen = close + 2 - i
                val startOut = out.length
                out.append('$').append(inner).append('$')
                edits += intArrayOf(i, close + 2, out.length - startOut - oldLen)
                i = close + 2
                continue
            }
        }
        out.append(c)
        i++
    }
    return NormalizedMathText(out.toString(), edits)
}

/** 找配对的 `\)`：不跨行、不跨行内代码；空内容视为没配对上 */
private fun findCloseParen(text: String, from: Int): Int {
    var j = from
    while (j < text.length) {
        val c = text[j]
        if (c == '\n' || c == '`') return -1
        if (c == '\\' && j + 1 < text.length && text[j + 1] == ')' && text[j - 1] != '\\') {
            return if (text.substring(from, j).isBlank()) -1 else j
        }
        j++
    }
    return -1
}
