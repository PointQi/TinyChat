package io.tinychat.markdown

import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Test
import kotlin.system.measureNanoTime

/**
 * 探针：不同长度的 Markdown 解析一次要多久、产生多少节点。
 *
 * 不是断言测试——只把数字打到 test-results 的 system-out 里，用来判断
 * 「滚动时每条消息解析一次」到底值不值得优化、以及要不要上缓存。
 * 桌面 JVM 上的数值比真机快 2–3×，看相对量。
 */
class MarkdownPerfProbeTest {

    private val parser = MarkdownParser(GFMFlavourDescriptor())

    private fun doc(repeat: Int): String = buildString {
        repeat(repeat) { i ->
            append("## 第 $i 节\n\n")
            append("这是一段用来压测的正文，包含 **加粗**、*斜体*、`inline code` 与[链接](https://example.com)，")
            append("长度接近真实回答的段落，再补几个字凑够两行显示。\n\n")
            append("- 列表项一\n- 列表项二\n  - 嵌套项\n\n")
            append("> 引用一行\n\n")
            append("| 列 A | 列 B |\n|---|---|\n| $i | 值 |\n\n")
            append("```kotlin\nfun f$i() = $i\n```\n\n")
        }
    }

    private fun countNodes(node: ASTNode): Int = 1 + node.children.sumOf { countNodes(it) }

    private fun report(name: String, src: String) {
        // 预热 3 次，避免 JIT 冷启动污染
        repeat(3) { parser.buildMarkdownTreeFromString(src) }
        var totalNs = 0L
        val runs = 20
        val nodes = countNodes(parser.buildMarkdownTreeFromString(src))
        repeat(runs) {
            totalNs += measureNanoTime { parser.buildMarkdownTreeFromString(src) }
        }
        val avgUs = totalNs / runs / 1000.0
        println(
            "PERF $name chars=${src.length} nodes=$nodes 平均解析=%.2fms".format(avgUs / 1000.0)
        )
    }

    @Test
    fun `打印解析成本`() {
        report("1KB", doc(2))
        report("3KB", doc(5))
        report("8KB", doc(14))
    }
}
