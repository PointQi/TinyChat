package io.tinychat.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcTypography
import io.tinychat.markdown.TcMarkdown
import kotlinx.coroutines.delay
import java.io.File

/**
 * 流式抖动实验台（M2-3 验收工具，只在 debug 构建里）。
 *
 * 把 [STREAM_SAMPLE] 按 6 字/帧喂给 `TcMarkdown`，用 `onSizeChanged` 记录每次量到的高度，
 * 统计**高度回缩次数**（验收标准：0 次）。回缩发生时把内容位置与上下文一起写进
 * `cacheDir/stream.txt`，方便定位是哪一段触发的。
 *
 * ```
 * adb shell am start -n io.tinychat/io.tinychat.debug.StreamingLabActivity
 * adb shell run-as io.tinychat cat cache/stream.txt
 * ```
 */
class StreamingLabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TcTheme { StreamScreen() }
        }
    }
}

@Composable
private fun StreamScreen() {
    val context = LocalContext.current
    var content by remember { mutableStateOf("") }
    var streaming by remember { mutableStateOf(true) }
    var report by remember { mutableStateOf("流式中…") }
    val heights = remember { mutableListOf<Int>() }
    // 高度变化点：内容长度 -> 高度，用来把回缩定位到具体字符
    val marks = remember { mutableListOf<Pair<Int, Int>>() }
    var cursorNow by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (cursorNow < STREAM_SAMPLE.length) {
            val next = (cursorNow + 6).coerceAtMost(STREAM_SAMPLE.length)
            cursorNow = next
            content = STREAM_SAMPLE.substring(0, next)
            delay(16)
        }
        streaming = false
        val drops = heights.zipWithNext().count { (a, b) -> b < a - 1 }
        val biggest = heights.zipWithNext().maxOfOrNull { (a, b) -> a - b } ?: 0
        report = "帧数=" + heights.size + " 回缩=" + drops + " 最大回缩=" + biggest + "px"

        val firstDrop = marks.zipWithNext().indexOfFirst { (a, b) -> b.second < a.second - 1 }
        val detail = if (firstDrop >= 0) {
            val at = marks[firstDrop + 1].first
            val from = (at - 60).coerceAtLeast(0)
            "firstDrop@cursor=" + at + " " + marks[firstDrop].second + "->" + marks[firstDrop + 1].second +
                "\nfragment=" + STREAM_SAMPLE.substring(from, at.coerceAtMost(STREAM_SAMPLE.length))
                .replace("\n", "\\n")
        } else {
            "no drop"
        }
        runCatching {
            File(context.cacheDir, "stream.txt").writeText(
                "frames=" + heights.size + " drops=" + drops + " maxDrop=" + biggest +
                    "\n" + detail + "\nheights=" + heights.joinToString(","),
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TcTheme.palette.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(text = report, style = TcTypography.bodySmall, color = TcTheme.palette.labelTertiary)
        TcMarkdown(
            content = content,
            streaming = streaming,
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged {
                    heights += it.height
                    marks += cursorNow to it.height
                },
        )
    }
}

/**
 * 流式样本（约 2600 字）：专门踩 M2-3 的三个坑。
 *
 * 1. **setext**：正文行紧跟 `---` / `===` → GFM 会把上一行变成标题，字号与间距突变；
 * 2. **未闭合公式**：按 6 字/帧喂入，`$$` 必然有「只来了一半」的中间态；
 * 3. 代码围栏、列表、引用、多段正文交替，检验块结构变化时高度是否只长不缩。
 */
private val STREAM_SAMPLE: String = buildString {
    append("## 结论先行\n\n")
    append("这个问题可以从三方面看：渲染、协议、计费。先说渲染，行内公式 ")
    append("\$E=mc^2\$ 与分式 \$\\frac{a}{b}\$ 都要和正文共用一条基线。\n\n")
    append("接下来这一段是正文，紧随其后的分隔线在流式期间会被当成 setext 标题。\n")
    append("---\n")
    append("上面这两行合起来会变成标题，字号、字重、间距都会跳一下。\n\n")
    append("换成等号线也是一样：\n")
    append("===\n")
    append("这里已经是新的一段了，用来观察 setext 标题生效前后的高度变化。\n\n")
    append("### 列表与代码\n\n")
    append("- 第一项：\$x_{i}^{2}\$ 下标要压到基线下面\n")
    append("- 第二项：\$\\sqrt[3]{x+1}\$ 根号不能被切\n")
    append("- 第三项：\$\\sum_{k=1}^{n} k\$ 上下标最高\n\n")
    append("```kotlin\nfun main() {\n    println(\"hello\")\n}\n```\n\n")
    append("块级公式：\n\n")
    append("\$\$\n\\int_{0}^{\\infty} e^{-x^2}\\,dx = \\frac{\\sqrt{\\pi}}{2}\n\$\$\n\n")
    append("> 引用里的公式 \$a^2+b^2=c^2\$ 也要正常对齐。\n\n")
    repeat(6) { round ->
        append("第 ${round + 1} 段：流式输出时正文是一小片一小片到达的，")
        append("每来一片都可能让段落重新折行，折行次数一变，高度就会跳。")
        append("如果高度只会增加，底部的文字就永远不会被顶回去；")
        append("一旦某次重排让高度变小，画面就会出现「回缩」——这正是要抓的现象。\n\n")
    }
    append("收尾一句：流式结束。\n")
}
