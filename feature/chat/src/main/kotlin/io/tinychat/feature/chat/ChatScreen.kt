package io.tinychat.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tinychat.designsystem.BubbleShape
import io.tinychat.designsystem.TcIcons
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcTypography
import io.tinychat.model.KnownProviders
import io.tinychat.model.ThinkingEffort
import io.tinychat.ui.TcIconButton
import io.tinychat.ui.TcInlineHint
import io.tinychat.ui.TcTopBar
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * 会话页 = 应用唯一主界面（决策 16：冷启动直达这里，没有启动页/引导页）。
 *
 * 布局对齐 `02 §12.1/§12.2/§13`。M1 正文仍是朴素 `Text`，Markdown 留给 M2。
 */
@Composable
fun ChatScreen(
    state: ChatUiState,
    hasApiKey: Boolean,
    autoSpeak: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onToggleThinking: () -> Unit,
    onToggleSearch: () -> Unit,
    onSelectModel: (String, ThinkingEffort) -> Unit,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onOpenProviderSettings: () -> Unit,
) {
    val palette = TcTheme.palette

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg),
    ) {
        TcTopBar(
            leading = { TcIconButton(TcIcons.Menu, "会话列表", onOpenDrawer) },
            title = state.title,
            trailing = {
                TcIconButton(
                    icon = TcIcons.Speak,
                    contentDescription = if (autoSpeak) "关闭自动朗读" else "开启自动朗读",
                    onClick = onToggleAutoSpeak,
                )
                TcIconButton(TcIcons.PlusCircle, "新建对话", onNewChat)
            },
        )

        if (state.messages.isEmpty()) {
            Greeting(modifier = Modifier.weight(1f))
        } else {
            MessageList(state = state, modifier = Modifier.weight(1f))
        }

        state.error?.let { message ->
            Text(
                text = message,
                style = TcTypography.bodySmall,
                color = palette.danger,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        if (!hasApiKey) {
            TcInlineHint(
                text = "尚未配置服务商，点击设置 API Key",
                onClick = onOpenProviderSettings,
            )
        }

        InputCard(
            state = state,
            onInputChange = onInputChange,
            onSend = onSend,
            onStop = onStop,
            onToggleThinking = onToggleThinking,
            onToggleSearch = onToggleSearch,
            onSelectModel = onSelectModel,
        )

        if (state.messages.isNotEmpty()) {
            Text(
                text = "回答由 AI 生成，仅供参考",
                style = TcTypography.bodySmall,
                color = palette.labelTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 10.dp),
            )
        } else {
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun Greeting(modifier: Modifier = Modifier) {
    val palette = TcTheme.palette
    val hour = remember { LocalTime.now().hour }
    val greeting = when {
        hour < 12 -> "早上好"
        hour < 18 -> "下午好"
        else -> "晚上好"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$greeting，我能帮什么忙吗？",
            style = TcTypography.headlineSmall,
            color = palette.labelPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MessageList(state: ChatUiState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    // 离底超过一屏就浮出「回到底部」（§12.2）
    val showToBottom by remember {
        derivedStateOf { listState.canScrollForward }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(state.messages, key = { it.id }) { message ->
                MessageRow(message)
            }
        }

        if (showToBottom) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .size(40.dp)
                    .shadow(4.dp, CircleShape)
                    .background(TcTheme.palette.bg, CircleShape)
                    .clickable {
                        scope.launch {
                            listState.animateScrollToItem(state.messages.lastIndex)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TcIcons.ArrowDown,
                    contentDescription = "回到底部",
                    tint = TcTheme.palette.labelSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessage) {
    val palette = TcTheme.palette

    if (message.isUser) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = message.text,
                style = TcTypography.bodyLarge,
                color = palette.labelPrimary,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .background(palette.bubble, BubbleShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (message.hasThinking) {
            ThinkingBlock(
                timeline = message.timeline,
                seconds = message.thinkingSeconds,
                inProgress = message.isThinking,
            )
            Spacer(Modifier.height(8.dp))
        }

        if (message.text.isNotBlank()) {
            Text(
                text = message.text,
                style = TcTypography.bodyLarge,
                color = palette.labelPrimary,
            )
            if (message.citations.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                CitationChips(message.citations)
            }
        }
    }
}

/**
 * 思维链折叠块（§13）：折叠 `已思考（用时 N 秒）›`；展开为左侧竖线 + 圆点时间轴，
 * 中间穿插工具步骤卡片。
 */
@Composable
private fun ThinkingBlock(timeline: List<TimelineItem>, seconds: Double?, inProgress: Boolean) {
    val palette = TcTheme.palette
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(inProgress) { expanded = inProgress }

    val label = if (inProgress) {
        "正在思考"
    } else {
        val secs = seconds?.let { it.toInt().coerceAtLeast(1) }
        if (secs != null) "已思考（用时 $secs 秒）" else "已思考"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = TcIcons.Think,
                contentDescription = null,
                tint = if (inProgress) palette.brand else palette.labelSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(text = label, style = TcTypography.bodyMedium, color = palette.labelSecondary)
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) TcIcons.ChevronDown else TcIcons.ChevronRight,
                contentDescription = if (expanded) "收起思维链" else "展开思维链",
                tint = palette.labelTertiary,
                modifier = Modifier.size(16.dp),
            )
        }

        if (expanded) {
            Row(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .height(IntrinsicSize.Min),
            ) {
                // 左侧 2dp 竖线
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(palette.borderL2, RoundedCornerShape(1.dp)),
                )
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    timeline.forEach { item ->
                        when (item) {
                            is TimelineItem.Text -> TimelineText(item.content)
                            is TimelineItem.Tool -> ToolCard(item.step)
                        }
                    }
                }
            }
        }
    }
}

/** 时间轴上的一段思考：每行一个圆点 */
@Composable
private fun TimelineText(content: String) {
    val palette = TcTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        content.split('\n').filter { it.isNotBlank() }.forEach { line ->
            Row {
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .size(4.dp)
                        .background(palette.labelTertiary, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = line.trim(),
                    style = TcTypography.bodyMedium,
                    color = palette.labelSecondary,
                )
            }
        }
    }
}

/** 工具步骤卡片：`搜索到 N 个网页` + 站点堆叠 + 展开结果列表 */
@Composable
private fun ToolCard(step: ToolStep) {
    val palette = TcTheme.palette
    var expanded by remember { mutableStateOf(false) }

    val icon = when (step.kind) {
        ToolKind.SEARCH -> TcIcons.Search
        ToolKind.OPEN -> TcIcons.Globe
        ToolKind.FIND -> TcIcons.Search
    }

    Column {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = step.items.isNotEmpty()) { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = palette.labelSecondary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = step.title,
                style = TcTypography.bodyMedium,
                color = palette.labelSecondary,
            )
            if (step.items.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                SiteStack(step.items)
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = if (expanded) TcIcons.ChevronDown else TcIcons.ChevronRight,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = palette.labelTertiary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 21.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                step.items.forEachIndexed { index, name ->
                    Text(
                        text = "${index + 1}. $name",
                        style = TcTypography.bodySmall,
                        color = palette.labelTertiary,
                    )
                }
            }
        }
    }
}

/** 站点图标堆叠：最多 3 个 + 溢出圆（真图待 M2 接 Coil） */
@Composable
private fun SiteStack(items: List<String>) {
    val palette = TcTheme.palette
    Row {
        items.take(3).forEachIndexed { index, name ->
            Box(
                modifier = Modifier
                    // 负 padding 会抛异常，重叠只能用 offset
                    .offset(x = if (index == 0) 0.dp else (-4).dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (index % 2 == 0) palette.bubble else palette.inlineCodeBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.take(1),
                    fontSize = 8.sp,
                    color = palette.labelSecondary,
                )
            }
        }
        if (items.size > 3) {
            Box(
                modifier = Modifier
                    .offset(x = (-4).dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(palette.labelTertiary),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "+", fontSize = 8.sp, color = palette.bg)
            }
        }
    }
}

/** 引用角标（§13）：上标数字，点击留给 M3-3 的来源面板 */
@Composable
private fun CitationChips(citations: List<String>) {
    val palette = TcTheme.palette
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        citations.forEachIndexed { index, title ->
            Box(
                modifier = Modifier
                    .size(width = 20.dp, height = 16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.inlineCodeBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "${index + 1}", fontSize = 10.sp, color = palette.labelSecondary)
            }
        }
    }
}

@Composable
private fun InputCard(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onToggleThinking: () -> Unit,
    onToggleSearch: () -> Unit,
    onSelectModel: (String, ThinkingEffort) -> Unit,
) {
    val palette = TcTheme.palette

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .shadow(3.dp, RoundedCornerShape(24.dp))
            .background(palette.bg, RoundedCornerShape(24.dp))
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 4.dp),
        ) {
            if (state.input.isEmpty()) {
                Text(
                    text = "发消息或按住说话",
                    style = TcTypography.bodyLarge,
                    color = palette.labelTertiary,
                )
            }
            BasicTextField(
                value = state.input,
                onValueChange = onInputChange,
                textStyle = TcTypography.bodyLarge.copy(color = palette.labelPrimary),
                cursorBrush = SolidColor(palette.brand),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 8,
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModelEffortPill(
                modelId = state.modelId,
                effort = state.effort,
                onSelect = onSelectModel,
            )
            Spacer(Modifier.width(8.dp))
            SearchPill(enabled = state.searchEnabled, onClick = onToggleSearch)
            Spacer(Modifier.weight(1f))
            CircleIcon(TcIcons.Attach, "附件", palette.labelSecondary) {}
            Spacer(Modifier.width(4.dp))
            when {
                state.isStreaming -> CircleIcon(TcIcons.Stop, "停止生成", palette.brand, filled = true, onClick = onStop)
                state.input.isNotBlank() -> CircleIcon(TcIcons.Send, "发送", palette.brand, filled = true, onClick = onSend)
                else -> CircleIcon(TcIcons.Mic, "语音输入", palette.labelSecondary) {}
            }
        }
    }
}

/** 模型与强度 pill：显示如 `Flash 高`（决策 20：不加中点），点开半屏选择器 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelEffortPill(
    modelId: String,
    effort: ThinkingEffort,
    onSelect: (String, ThinkingEffort) -> Unit,
) {
    val palette = TcTheme.palette
    var sheetOpen by remember { mutableStateOf(false) }
    val modelLabel = if (modelId == KnownProviders.v4Pro.id) "V4 Pro" else "Flash"

    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(palette.brand.copy(alpha = 0.10f))
            .clickable { sheetOpen = true }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$modelLabel ${effort.displayName}",
            style = TcTypography.bodySmall,
            color = palette.brand,
            fontWeight = FontWeight.Medium,
        )
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = palette.bg,
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = "模型与强度",
                    style = TcTypography.titleMedium,
                    color = palette.labelPrimary,
                )
                Spacer(Modifier.height(12.dp))

                listOf(KnownProviders.flash, KnownProviders.v4Pro).forEach { model ->
                    val name = if (model.id == KnownProviders.v4Pro.id) "V4 Pro" else "Flash"
                    val usable = model.capabilities.webSearch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
                                style = TcTypography.bodyLarge,
                                color = if (usable) palette.labelPrimary else palette.labelSecondary,
                            )
                            if (!usable) {
                                Text(
                                    text = "不支持联网与文件",
                                    style = TcTypography.bodySmall,
                                    color = palette.labelTertiary,
                                )
                            }
                        }
                        ThinkingEffort.entries.forEach { option ->
                            EffortChip(
                                text = option.displayName,
                                selected = model.id == modelId && option == effort,
                                onClick = {
                                    sheetOpen = false
                                    onSelect(model.id, option)
                                },
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun EffortChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val palette = TcTheme.palette
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(if (selected) palette.brand else palette.bubble)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TcTypography.bodySmall,
            color = if (selected) palette.brandOn else palette.labelSecondary,
        )
    }
}

/** 搜索 pill：只显示图标。开 = 品牌色，关 = 灰（§12.1） */
@Composable
private fun SearchPill(enabled: Boolean, onClick: () -> Unit) {
    val palette = TcTheme.palette
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(if (enabled) palette.brand.copy(alpha = 0.10f) else palette.bubble)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = TcIcons.Globe,
            contentDescription = if (enabled) "关闭智能搜索" else "开启智能搜索",
            tint = if (enabled) palette.brand else palette.labelSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun CircleIcon(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = TcTheme.palette
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (filled) palette.brand else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (filled) palette.brandOn else tint,
            modifier = Modifier.size(if (filled) 18.dp else 22.dp),
        )
    }
}
