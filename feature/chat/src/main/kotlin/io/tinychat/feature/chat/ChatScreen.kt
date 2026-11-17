package io.tinychat.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.tinychat.designsystem.BubbleShape
import io.tinychat.designsystem.InputShape
import io.tinychat.designsystem.TcIcons
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcTypography
import io.tinychat.ui.TcIconButton
import io.tinychat.ui.TcInlineHint
import io.tinychat.ui.TcTopBar

/**
 * 会话页 = 应用唯一主界面（决策 16：冷启动直达这里，没有启动页/引导页）。
 *
 * M1 范围内正文用朴素 `Text` 渲染，Markdown 留给 M2 —— 先把协议跑通，别让渲染问题盖住数据问题。
 */
@Composable
fun ChatScreen(
    state: ChatUiState,
    hasApiKey: Boolean,
    autoSpeak: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
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
            trailing = {
                TcIconButton(
                    icon = TcIcons.Speak,
                    contentDescription = if (autoSpeak) "关闭自动朗读" else "开启自动朗读",
                    onClick = onToggleAutoSpeak,
                )
                TcIconButton(TcIcons.NewChat, "新建对话", onNewChat)
            },
        )

        if (state.messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "有什么可以帮你的吗？",
                    style = TcTypography.headlineSmall,
                    color = palette.labelPrimary,
                    textAlign = TextAlign.Center,
                )
            }
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

        InputBar(
            input = state.input,
            isStreaming = state.isStreaming,
            hasApiKey = hasApiKey,
            onInputChange = onInputChange,
            onSend = onSend,
            onStop = onStop,
        )

        Spacer(Modifier.height(8.dp))
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            message.thinking?.let { thinking ->
                Text(
                    text = thinking,
                    style = TcTypography.bodySmall,
                    color = palette.labelTertiary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text(
                text = message.text,
                style = TcTypography.bodyLarge,
                color = palette.labelPrimary,
            )
        }
    }
}

@Composable
private fun InputBar(
    input: String,
    isStreaming: Boolean,
    hasApiKey: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val palette = TcTheme.palette
    val canSend = hasApiKey && input.isNotBlank() && !isStreaming

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .background(palette.bubble, InputShape)
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(TcIcons.Attach, "附件", tint = palette.labelSecondary, modifier = Modifier.size(20.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
            ) {
                if (input.isEmpty()) {
                    Text(
                        text = "给 TinyChat 发送消息",
                        style = TcTypography.bodyLarge,
                        color = palette.labelTertiary,
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    textStyle = TcTypography.bodyLarge.copy(color = palette.labelPrimary),
                    cursorBrush = SolidColor(palette.brand),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 6,
                )
            }

            Icon(TcIcons.Mic, "语音输入", tint = palette.labelSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(2.dp))

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        if (canSend || isStreaming) palette.brand else palette.inlineCodeBg,
                        RoundedCornerShape(50),
                    )
                    .clickable(enabled = canSend || isStreaming) {
                        if (isStreaming) onStop() else onSend()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isStreaming) TcIcons.Stop else TcIcons.Send,
                    contentDescription = if (isStreaming) "停止生成" else "发送",
                    tint = if (canSend || isStreaming) palette.brandOn else palette.labelTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}
