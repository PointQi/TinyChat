package io.tinychat.feature.chat

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.tinychat.designsystem.BubbleShape
import io.tinychat.designsystem.TcIcons
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcTypography
import io.tinychat.model.KnownProviders
import io.tinychat.model.ThinkingEffort
import io.tinychat.ui.TcIconButton
import io.tinychat.ui.TcInlineHint
import io.tinychat.ui.TcTopBar
import java.time.LocalTime

/**
 * 会话页 = 应用唯一主界面（决策 16：冷启动直达这里，没有启动页/引导页）。
 *
 * 布局对齐 `02 §12.1/§12.2/§13`：
 * - 顶栏：☰ + 标题（1 行省略）+ 朗读 + 新建；
 * - 正文：用户气泡 + 思维链折叠头 + 无气泡正文；
 * - 输入卡：文本框 + 第二行 `[模型与强度] [搜索] [回形针] [麦克风/发送]`。
 *
 * M1 正文仍是朴素 `Text`，Markdown 留给 M2。
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

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(state.messages, key = { it.id }) { message ->
            MessageRow(message)
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
        message.thinking?.let { thinking ->
            ThinkingBlock(
                thinking = thinking,
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
        }
    }
}

/**
 * 思维链折叠块（§13）：
 * - 折叠：`已思考（用时 N 秒）›`，14sp LabelSecondary；
 * - 展开：左侧 2dp 竖线 + 24dp 缩进，15sp LabelSecondary；
 * - 流式中：`正在思考` + 品牌色图标。
 */
@Composable
private fun ThinkingBlock(thinking: String, seconds: Double?, inProgress: Boolean) {
    val palette = TcTheme.palette
    var expanded by remember { mutableStateOf(false) }

    // 流式中默认展开，结束后收起（与官方一致）
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
            Text(
                text = label,
                style = TcTypography.bodyMedium,
                color = palette.labelSecondary,
            )
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
                Text(
                    text = thinking,
                    style = TcTypography.bodyMedium,
                    color = palette.labelSecondary,
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .animateContentSize(),
                )
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
        // —— 第一行：文本框 ——
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

        // —— 第二行：模型与强度 / 搜索 / 附件 / 语音或发送 ——
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

/** 模型与强度 pill：显示如 `Flash 高`（决策 20：不加中点） */
@Composable
private fun ModelEffortPill(
    modelId: String,
    effort: ThinkingEffort,
    onSelect: (String, ThinkingEffort) -> Unit,
) {
    val palette = TcTheme.palette
    var menuOpen by remember { mutableStateOf(false) }
    val modelLabel = when (modelId) {
        KnownProviders.v4Pro.id -> "V4 Pro"
        else -> "Flash"
    }

    Box {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(palette.brand.copy(alpha = 0.10f))
                .clickable { menuOpen = true }
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

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            listOf(KnownProviders.flash, KnownProviders.v4Pro).forEach { model ->
                ThinkingEffort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            val name = if (model.id == KnownProviders.v4Pro.id) "V4 Pro" else "Flash"
                            Text(
                                text = "$name ${option.displayName}",
                                style = TcTypography.bodyMedium,
                                color = if (model.id == KnownProviders.v4Pro.id) {
                                    palette.labelSecondary
                                } else {
                                    palette.labelPrimary
                                },
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onSelect(model.id, option)
                        },
                    )
                }
            }
        }
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
            .background(if (filled) palette.brand else androidx.compose.ui.graphics.Color.Transparent)
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
