package io.tinychat.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tinychat.designsystem.TcIcons
import io.tinychat.designsystem.TcTheme
import io.tinychat.model.Usage
import io.tinychat.model.billing.Money
import io.tinychat.model.billing.UsageSummary
import io.tinychat.ui.TcIconButton

/** 说明文案（点标签展开）。集中在一处，做 i18n 时只改这里。 */
object UsageGlossary {

    const val CONTEXT = "context"
    const val CACHE_HIT = "cacheHit"
    const val COST = "cost"
    const val BALANCE = "balance"
    const val USAGE_TOTAL = "usageTotal"

    fun of(key: String): String = when (key) {
        CONTEXT ->
            "上一轮请求实际发送的 token（未缓存输入 + 缓存读取 + 输出）占模型上限的比例。" +
                "超过 80% 后，较早的对话会在发送时被截断，表现是模型“忘了”前面聊过的内容。"
        CACHE_HIT ->
            "服务端复用了上一轮已经算过的上下文前缀，这部分按约 2% 的价格计费，命中率越高越省钱。" +
                "编辑提问、重新生成、切换提示词或模型都会改动历史，命中率会掉一截，属于正常现象。"
        COST ->
            "用服务商价目表在本地估算，含搜索与抓取（每次 0.005 元）。" +
                "高峰时段（工作日 9:00 到 12:00、14:00 到 18:00）单价翻倍，按每轮发生时的时段计算。" +
                "语音不计入这里，与真实账单可能有几分钱误差。"
        BALANCE ->
            "含赠送余额与充值余额，来自服务商的余额接口，仅在打开本抽屉时刷新。只有 DeepSeek 官方提供这个接口。"
        USAGE_TOTAL ->
            "本会话的累计值，包括重新生成和编辑前那些已经被丢弃的版本，因为那部分确实已经计费。"
        else -> ""
    }
}

/** 圆环：18dp / 描边 2dp，`<40%` 品牌色、`≥40%` 琥珀、`≥80%` 红。点开用量抽屉。 */
@Composable
fun ContextRing(
    percent: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = TcTheme.palette
    val color = when {
        percent >= 0.8f -> palette.danger
        percent >= 0.4f -> palette.warning
        else -> palette.brand
    }
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val stroke = 2.dp.toPx()
            val radius = (size.minDimension - stroke) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(color = palette.borderL2, radius = radius, center = center, style = Stroke(stroke))
            val sweep = 360f * percent.coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/** 每轮回答下面那一行（M7-3）：`4,096 命中  9,078 未命中  1,686 输出  ￥0.0259` */
@Composable
fun UsageStatsLine(usage: Usage, costMicros: Long) {
    val palette = TcTheme.palette
    val line = buildString {
        append("${Money.tokens(usage.cacheHitTokens.toLong())} 命中  ")
        append("${Money.tokens(usage.promptTokens.toLong())} 未命中  ")
        append("${Money.tokens(usage.completionTokens.toLong())} 输出  ")
        append(Money.format(costMicros))
    }
    Text(
        text = line,
        style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
        color = palette.labelTertiary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/**
 * 右侧「用量与费用」抽屉（M7-2，决策 27）。
 *
 * 只放会话级汇总：上下文占比 + 进度条、token 五项、账户余额。
 * 不出现本轮明细（在消息下方）、不出现脚注小字、不放刷新按钮。
 */
@Composable
fun UsageDrawer(
    visible: Boolean,
    summary: UsageSummary,
    contextUsed: Int,
    contextWindow: Int,
    balanceText: String?,
    onDismiss: () -> Unit,
) {
    val palette = TcTheme.palette
    val widthDp = (LocalConfiguration.current.screenWidthDp * 0.82f).coerceAtMost(320f).dp
    var expanded by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInHorizontally(tween(200)) { it },
                exit = slideOutHorizontally(tween(200)) { it },
            ) {
                Column(
                    modifier = Modifier
                        .width(widthDp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp))
                        .background(palette.bg)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "用量与费用",
                            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                            color = palette.labelPrimary,
                        )
                        Spacer(Modifier.weight(1f))
                        TcIconButton(TcIcons.Close, "关闭", onDismiss)
                    }

                    Spacer(Modifier.height(16.dp))

                    // 上下文占比
                    val percent = if (contextWindow <= 0) 0f else (contextUsed.toFloat() / contextWindow).coerceIn(0f, 1f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExplainableLabel("上下文已用", UsageGlossary.CONTEXT, expanded) { expanded = it }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "${(percent * 100).toInt()}%",
                            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                            color = palette.labelPrimary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "~${compactTokens(contextUsed)} / ${compactTokens(contextWindow)}",
                            style = TextStyle(fontSize = 12.sp),
                            color = palette.labelTertiary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(palette.inlineCodeBg),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(percent)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when {
                                        percent >= 0.8f -> palette.danger
                                        percent >= 0.4f -> palette.warning
                                        else -> palette.brand
                                    },
                                ),
                        )
                    }
                    if (expanded == UsageGlossary.CONTEXT) {
                        GlossaryText(UsageGlossary.CONTEXT)
                    }

                    Spacer(Modifier.height(28.dp))
                    ExplainableLabel("Token 用量", UsageGlossary.USAGE_TOTAL, expanded) { expanded = it }
                    if (expanded == UsageGlossary.USAGE_TOTAL) {
                        GlossaryText(UsageGlossary.USAGE_TOTAL)
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            ExplainableLabel("费用", UsageGlossary.COST, expanded) { expanded = it }
                            Text(
                                text = if (summary.costMicros == 0L) "--" else Money.format(summary.costMicros),
                                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                                color = palette.labelPrimary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            ExplainableLabel("缓存命中", UsageGlossary.CACHE_HIT, expanded) { expanded = it }
                            Text(
                                text = summary.cacheHitRate?.let { "%.1f%%".format(it * 100) } ?: "--",
                                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                                color = palette.labelPrimary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    if (expanded == UsageGlossary.COST) GlossaryText(UsageGlossary.COST)
                    if (expanded == UsageGlossary.CACHE_HIT) GlossaryText(UsageGlossary.CACHE_HIT)

                    val hasData = !summary.isEmpty
                    MetricBlock("未缓存输入", summary.usage.promptTokens, hasData)
                    MetricBlock("缓存读取", summary.usage.cacheHitTokens, hasData)
                    MetricBlock("输出", summary.usage.completionTokens, hasData)

                    Spacer(Modifier.height(20.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(palette.borderL2),
                    )
                    Spacer(Modifier.height(20.dp))

                    ExplainableLabel("账户", UsageGlossary.BALANCE, expanded) { expanded = it }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "余额",
                        style = TextStyle(fontSize = 13.sp),
                        color = palette.labelSecondary,
                    )
                    Text(
                        text = balanceText ?: "--",
                        style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                        color = palette.labelPrimary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (expanded == UsageGlossary.BALANCE) GlossaryText(UsageGlossary.BALANCE)
                }
            }
        }
    }
}

/** 可点的灰色标签：点一下展开/收起说明。不加下划线、不加问号（决策 27）。 */
@Composable
private fun ExplainableLabel(
    text: String,
    key: String,
    expanded: String?,
    onToggle: (String?) -> Unit,
) {
    val palette = TcTheme.palette
    Text(
        text = text,
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
        color = palette.labelTertiary,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
            onToggle(if (expanded == key) null else key)
        },
    )
}

@Composable
private fun GlossaryText(key: String) {
    val palette = TcTheme.palette
    Text(
        text = UsageGlossary.of(key),
        style = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
        color = palette.labelSecondary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ColumnScope.MetricBlock(label: String, value: Int, hasData: Boolean) {
    val palette = TcTheme.palette
    Spacer(Modifier.height(16.dp))
    Text(
        text = label,
        style = TextStyle(fontSize = 13.sp),
        color = palette.labelSecondary,
    )
    Text(
        text = if (!hasData) "--" else "${Money.tokens(value.toLong())} tok",
        style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        color = palette.labelPrimary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** `120000` → `120K`；`623400` → `623.4K`；`1000000` → `1M`。 */
internal fun compactTokens(value: Int): String {
    val v = value.coerceAtLeast(0)
    return when {
        v < 1_000 -> v.toString()
        v < 1_000_000 -> trimZero(v / 1_000.0) + "K"
        else -> trimZero(v / 1_000_000.0) + "M"
    }
}

private fun trimZero(value: Double): String {
    val text = "%.1f".format(value)
    return if (text.endsWith(".0")) text.dropLast(2) else text
}
