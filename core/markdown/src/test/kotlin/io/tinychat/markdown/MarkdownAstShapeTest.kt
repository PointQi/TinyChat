package io.tinychat.markdown

import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Test

/**
 * 只为把「顶层到底有哪些节点」这件事钉死（M2-1/M2-3）。
 * 库会给**每个顶层节点**前面插一个 `Spacer(height = padding.block)`，
 * 如果 AST 里夹着 EOL / TEXT 之类的空节点，那就是一个个看不见的额外空隙。
 */
class MarkdownAstShapeTest {

    private fun topLevel(src: String): List<String> {
        val parser = MarkdownParser(GFMFlavourDescriptor())
        val root = parser.buildMarkdownTreeFromString(src)
        return root.children.map { node ->
            val raw = src.substring(node.startOffset, node.endOffset)
            "${node.type}".substringAfterLast('.') + " [" + raw.replace("\n", "\\n").take(40) + "]"
        }
    }

    @Test
    fun `打印顶层节点`() {
        val src = """
            ## 清单

            段落一。

            ### 小节

            - 项目
            - 项目二

            引用：
            > 引用文字

            | 列 | 列 |
            |---|---|
            | a | b |
        """.trimIndent()
        topLevel(src).forEach(::println)
    }

    @Test
    fun `setext 标题前后`() {
        val src = "段落一。\n\nSetext 标题\n===\n\n结束。"
        topLevel(src).forEach(::println)
    }
}
