package io.tinychat.markdown

/**
 * 把「正文行紧跟一行 `---` / `===`」的写法固定成分隔线（M2-3 抖动的第一坑）。
 *
 * **为什么必须处理**：GFM 把
 * ```
 * 一段正文
 * ---
 * ```
 * 解析成 **setext 标题**。流式输出时这行标记是最后才到的 —— 到达的一瞬间，
 * 上面那行正文会从「16sp 段落」变成「24sp 粗体标题」，行高、块间距、甚至整块高度全部重算。
 * 真机实测（Pixel，density 3.0）在这里出现 **-72px 的高度回缩**，也就是画面往上跳一下。
 *
 * **做法**：把标记行原地换成等长的 `***`。`***` 同样是分隔线，但不会被当成 setext 标记，
 * 所以那段正文从头到尾都是段落 —— 高度只增不减。
 * 关键点是**长度不变**：引用角标的偏移是对着原文算的，改长度会让角标全线错位。
 *
 * 这条规则**流式与结束都生效**，否则消息结束的一瞬间又会从「段落 + 分隔线」跳成
 * 「标题 + 分隔线」，又是一次回缩。代价是 model 真写 setext 标题时会被渲染成分隔线 ——
 * 实测模型输出里几乎只用 `#` 系列，这种两行写法基本都表示分隔，取舍见 docs/03。
 */
internal fun stabilizeSetextMarkers(content: String): String {
    if (content.isEmpty()) return content
    // 快速判断：连标记字符都没有就不用逐行扫
    if (!content.contains("---") && !content.contains("===")) return content

    return buildString(content.length) {
        var lineStart = 0
        var inFence = false
        var prevLineBlank = true
        var prevLineIndented = false

        while (lineStart <= content.length) {
            val lineEnd = content.indexOf('\n', lineStart).let { if (it < 0) content.length else it }
            val line = content.substring(lineStart, lineEnd)
            val trimmed = line.trim()
            val indent = line.length - line.trimStart().length
            // ``` 或 ~~~ 围栏开关；围栏内的 `---` 是代码，不能动
            val fenceLine = trimmed.startsWith("```") || trimmed.startsWith("~~~")

            val isMarker = !inFence &&
                trimmed.isNotEmpty() &&
                !prevLineBlank &&
                !prevLineIndented &&
                !fenceLine &&
                // 标记行自己缩进 4 格以上就是代码块，不能动
                indent < 4 &&
                (
                    (trimmed.length >= 3 && trimmed.all { it == '-' }) ||
                        (trimmed.length >= 1 && trimmed.all { it == '=' })
                    )

            if (isMarker) {
                // 等长替换：前后空白照抄，只有标记字符换成 *
                val lead = indent
                val trail = line.length - line.trimEnd().length
                append(line, 0, lead)
                repeat(trimmed.length) { append('*') }
                append(line, line.length - trail, line.length)
            } else {
                append(line)
            }
            if (lineEnd < content.length) append('\n')

            if (fenceLine) inFence = !inFence
            prevLineBlank = trimmed.isEmpty()
            prevLineIndented = indent >= 4
            if (lineEnd >= content.length) break
            lineStart = lineEnd + 1
        }
    }
}
