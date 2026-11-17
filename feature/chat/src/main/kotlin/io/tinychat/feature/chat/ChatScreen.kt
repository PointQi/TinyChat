package io.tinychat.feature.chat

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
 * M0 只做骨架与布局，不接任何网络：消息区恒为空态，输入区不可输入。
 */
@Composable
fun ChatScreen(
    hasApiKey: Boolean,
    autoSpeak: Boolean,
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
            leading = {
                TcIconButton(TcIcons.Menu, "会话列表", onOpenDrawer)
            },
            trailing = {
                TcIconButton(
                    icon = TcIcons.Speak,
                    contentDescription = if (autoSpeak) "关闭自动朗读" else "开启自动朗读",
                    onClick = onToggleAutoSpeak,
                )
                TcIconButton(TcIcons.NewChat, "新建对话", onNewChat)
            },
        )

        // —— 消息区：M0 为空态。决策 17：不放中心图标，只放一句问候语，垂直居中。
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

        // —— 未配置 Key：行内提示条（不弹窗、不跳转、不阻断）
        if (!hasApiKey) {
            TcInlineHint(
                text = "尚未配置服务商，点击设置 API Key",
                onClick = onOpenProviderSettings,
            )
        }

        InputBarPlaceholder()

        Spacer(Modifier.height(8.dp))
    }
}

/** 输入区占位：结构先定死，M1-7 接入真实输入与发送。 */
@Composable
private fun InputBarPlaceholder() {
    val palette = TcTheme.palette
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
            Text(
                text = "给 TinyChat 发送消息",
                style = TcTypography.bodyLarge,
                color = palette.labelTertiary,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 14.dp),
            )
            Icon(TcIcons.Mic, "语音输入", tint = palette.labelSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(palette.inlineCodeBg, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(TcIcons.Send, "发送", tint = palette.labelTertiary, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}
