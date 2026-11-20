package io.tinychat.feature.chat

import io.tinychat.model.FragmentPayloadCodec
import io.tinychat.model.SearchResult
import io.tinychat.model.ThinkPayload
import io.tinychat.model.ToolOpenPayload
import io.tinychat.model.ToolSearchPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 思维链时间轴（§13）。
 *
 * 这组用例钉的是**结算顺序**：搜索之前的那段思考必须排在搜索行**上面**。
 * 早期版本在 TOOL_SEARCH 分支只结算了「浏览」分组、忘了结算文字缓冲区，
 * 结果整段前置思考被吐到搜索行下面（真机上看起来就是「搜索到 N 个网页」跑到了最前面）。
 */
class TimelineBuilderTest {

    private fun think(text: String) =
        TimelineFragment("THINK", FragmentPayloadCodec.encode(ThinkPayload(content = text)))

    private fun search(vararg sites: String, status: String = "FINISHED") =
        TimelineFragment(
            "TOOL_SEARCH",
            FragmentPayloadCodec.encode(
                ToolSearchPayload(
                    status = status,
                    queries = listOf("今天的新闻"),
                    results = sites.mapIndexed { index, site ->
                        SearchResult(
                            // 真实报文里网址是 ASCII 域名，站名才是中文
                            url = "https://site$index.example.com/$index",
                            title = "$site 的标题",
                            snippet = "$site 的摘要",
                            siteName = site,
                        )
                    },
                ),
            ),
        )

    private fun open(site: String, url: String = "https://$site.example.com/page", status: String = "FINISHED") =
        TimelineFragment(
            "TOOL_OPEN",
            FragmentPayloadCodec.encode(
                ToolOpenPayload(
                    status = status,
                    url = url,
                    title = "$site 的文章",
                    siteName = site,
                ),
            ),
        )

    private fun List<TimelineItem>.shape(): List<String> = map { item ->
        when (item) {
            is TimelineItem.Text -> "text:${item.content}"
            is TimelineItem.Tool -> "tool:${item.step.kind}"
        }
    }

    @Test
    fun `搜索前的思考排在搜索行上面`() {
        val timeline = buildTimeline(
            listOf(
                think("先看看今天发生了什么"),
                search("新浪", "新浪", "网易"),
                think("结果不够，得打开几篇"),
                open("新浪"),
                think("可以开始写了"),
            ),
        )

        assertEquals(
            listOf("text:先看看今天发生了什么", "tool:SEARCH", "text:结果不够，得打开几篇", "tool:OPEN", "text:可以开始写了"),
            timeline.shape(),
        )

        val searchStep = (timeline[1] as TimelineItem.Tool).step
        assertEquals("搜索到 3 个网页", searchStep.title)
        assertEquals(3, searchStep.entries.size)
        assertTrue(searchStep.canOpen)
        assertEquals("搜索结果", searchStep.panelTitle)
    }

    @Test
    fun `连续浏览合并成一行且同一链接只算一页`() {
        val timeline = buildTimeline(
            listOf(
                think("打开这几篇"),
                open("新浪", url = "https://a.example.com/1"),
                open("网易", url = "https://b.example.com/2"),
                open("新浪", url = "https://a.example.com/1"),
                think("看完再说"),
            ),
        )

        assertEquals(listOf("text:打开这几篇", "tool:OPEN", "text:看完再说"), timeline.shape())
        val step = (timeline[1] as TimelineItem.Tool).step
        assertEquals("已浏览 2 个页面", step.title)
        assertEquals("已浏览搜索结果", step.panelTitle)
        assertEquals(2, step.entries.size)
    }

    @Test
    fun `浏览分组在搜索之前结算 - 不会盖住中间那段思考`() {
        val timeline = buildTimeline(
            listOf(
                think("先搜"),
                search("新浪"),
                think("再打开"),
                open("新浪"),
                think("再搜一次"),
                search("网易"),
            ),
        )

        assertEquals(
            listOf("text:先搜", "tool:SEARCH", "text:再打开", "tool:OPEN", "text:再搜一次", "tool:SEARCH"),
            timeline.shape(),
        )
    }

    @Test
    fun `搜索失败显示暂不可用且不可点`() {
        val timeline = buildTimeline(
            listOf(think("搜一下"), search(status = "FAILED")),
        )

        val step = (timeline.last() as TimelineItem.Tool).step
        assertEquals("联网搜索暂不可用", step.title)
        assertTrue(step.failed)
        assertTrue(step.entries.isEmpty())
        assertFalse(step.canOpen)
    }

    @Test
    fun `搜索没有结果时给未搜索到合适内容`() {
        val timeline = buildTimeline(listOf(search()))

        val step = (timeline.single() as TimelineItem.Tool).step
        assertEquals("未搜索到合适内容", step.title)
        assertFalse(step.canOpen)
    }

    @Test
    fun `结尾那一段思考排在最后一个浏览行下面`() {
        val timeline = buildTimeline(
            listOf(
                think("先打开一篇"),
                open("新浪"),
                think("最后再想一下"),
            ),
        )

        assertEquals(listOf("text:先打开一篇", "tool:OPEN", "text:最后再想一下"), timeline.shape())
    }

    @Test
    fun `工具行条目带上图标地址 - 服务端没给时按域名拼图标 CDN`() {
        val timeline = buildTimeline(listOf(search("新浪")))

        val entry = (timeline.single() as TimelineItem.Tool).step.entries.single()
        assertEquals("新浪", entry.site)
        assertEquals("https://cdn.deepseek.com/site-icons/site0.example.com", entry.iconUrl)
        assertEquals("新浪 的摘要", entry.detail)
    }
}
