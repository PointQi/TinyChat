package io.tinychat.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.compose.LocalBulletListHandler
import com.mikepenz.markdown.compose.Markdown
import com.mikepenz.markdown.compose.MarkdownElement
import com.mikepenz.markdown.compose.extendedspans.ExtendedSpans
import com.mikepenz.markdown.model.BulletHandler
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.markdownExtendedSpans
import com.mikepenz.markdown.model.rememberStreamingMarkdownState
import kotlinx.coroutines.delay
import org.intellij.markdown.MarkdownTokenTypes

/** 列表符号：库默认是 `"• "`（带一个空格），宽度随字号变化，和正文的空隙不好控 */
private val TcBulletHandler = BulletHandler { _, _, _, _, _ -> "•" }

/**
 * 助手正文的 Markdown 渲染（M2-1 / M2-2）。
 *
 * **硬性约定（docs/04 §3.4 实测结论，别改）**：
 * 1. 只用 `rememberStreamingMarkdownState()` + 逐片 `append()`；
 *    `Markdown(String)` 与「逐帧换 state」的写法每帧整篇重解析，实测 42–61 次高度回缩 /
 *    2042dp 闪屏 / 2.0–2.3× 耗时；
 * 2. 追加前必须过 [FrameMerger] 的节流（按渲染耗时决定下一帧何时可以出）；
 * 3. 内容被整体替换（版本切换、重新生成）时才重建解析状态，重建走 `key(generation)`。
 *
 * @param content 到目前位置的全部正文（不是增量切片）。
 * @param streaming 还在流式输出中。结束时立刻把剩余内容补上，不再节流。
 */
@Composable
fun TcMarkdown(
    content: String,
    modifier: Modifier = Modifier,
    streaming: Boolean = false,
) {
    var generation by remember { mutableIntStateOf(0) }
    val merger = remember { FrameMerger() }

    key(generation) {
        val state = rememberStreamingMarkdownState()

        LaunchedEffect(state, content, streaming) {
            val appended = state.content.length
            if (appended > content.length || !content.regionMatches(0, state.content, 0, appended)) {
                // 正文被替换（比如重新生成得到更短的答案）：解析状态只能追加，只能重建
                generation += 1
                return@LaunchedEffect
            }
            if (appended == content.length) return@LaunchedEffect

            var cursor = appended
            while (cursor < content.length) {
                if (streaming) {
                    val wait = merger.millisUntilNextUpdate()
                    if (wait > 0) {
                        delay(wait)
                        continue
                    }
                }
                val startMs = nowMs()
                state.append(content.substring(cursor))
                merger.recordCost(nowMs() - startMs)
                cursor = content.length
            }
        }

        CompositionLocalProvider(LocalBulletListHandler provides TcBulletHandler) {
            Markdown(
                streamingMarkdownState = state,
                colors = tcMarkdownColors(),
                typography = tcMarkdownTypography(),
                padding = tcMarkdownPadding(),
                dimens = tcMarkdownDimens(),
                modifier = modifier.fillMaxWidth(),
                components = tcMarkdownComponents(),
                // 行内代码的灰底由自己的画笔绘制：库自带画笔拿「行盒」当矩形，
                // 14sp 的字被 28sp 行高的灰条压住，首行与续行的裁切量还不一样。
                extendedSpans = markdownExtendedSpans {
                    remember { ExtendedSpans(TcInlineCodePainter(fontSize = InlineCodeFontSize)) }
                },
                // 库默认给每段文字挂 animateContentSize：流式时高度动画与帧合并互相打架，
                // 表现为内容「慢慢长出来」并伴随回缩。消息流里必须关掉。
                animations = markdownAnimations { this },
                success = { snapshot, components, mdModifier ->
                    Column(modifier = mdModifier) {
                        val empty = snapshot.stableAst.isEmpty() && snapshot.unstableAstTail.isEmpty()
                        val text = if (empty) "" else state.content.toString()
                        // EOL 在 GFM 的 AST 里成对出现，渲染它们只会多出空 Spacer（还拖慢流式）
                        for (node in snapshot.stableAst) {
                            if (node.type == MarkdownTokenTypes.EOL) continue
                            MarkdownElement(node, components, text, includeSpacer = false)
                        }
                        for (node in snapshot.unstableAstTail) {
                            if (node.type == MarkdownTokenTypes.EOL) continue
                            MarkdownElement(node, components, text, includeSpacer = false)
                        }
                    }
                },
            )
        }
    }
}

private fun nowMs(): Long = System.nanoTime() / 1_000_000
