行内元素的密度是排版里最容易被忽略的一项：一句正常的话里塞进三四个带底色、带颜色的片段，行高就会开始抖。

先是一句带行内代码的：把 `tween(180)` 换成 `tween(TcMotion.Base)`，行为不变，但之后调整节奏只需要改一处。

再是同一条链接反复出现的情况：参数含义见 https://api-docs.deepseek.com/zh-cn/guides/web_tools ，流式事件顺序见 https://api-docs.deepseek.com/zh-cn/guides/build_a_chatbot ，两条都很长，长到必须在气泡里折行。超长且没有空格的地址也放一条：https://example.com/very/long/path/without/any/spaces/at/all?query=1234567890&token=abcdefghijklmnopqrstuvwxyz

行内公式与行内代码相邻：当 $n \to \infty$ 时，复杂度是 $O(n\log n)$，实现里对应 `n * log2(n)` 这个表达式。

粗体、斜体与删除线的组合：**重点**、*强调*、~~删掉的~~，以及三者连写 **粗体里的 `代码` 与 $x^2$**。

行内代码紧贴标点：`foo()`，`bar()`；`baz()`。还有中英混排的 `RecyclerView.Adapter#notifyItemChanged`。

一句没有行内元素的长句作为对照，用来确认上面那些带底色的片段没有把整段的行高撑高，也没有让行与行之间出现不均匀的空隙，如果看起来一行紧一行松，那就是行内元素的绘制高度与行高没有对齐。
