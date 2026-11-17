package io.tinychat.feature.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.tinychat.designsystem.TcIcons
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcTypography
import io.tinychat.ui.TcListRow
import io.tinychat.ui.TcSectionLabel

/**
 * 会话抽屉（M0 占位）。
 * 最终结构（docs/02 §12.3）：新建对话 → 会话列表（按时间分组）→ 余额 → 设置入口。
 * M0 只有假数据，用来验证导航与布局。
 */
@Composable
fun SessionsDrawerContent(
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickSession: (String) -> Unit = {},
) {
    val palette = TcTheme.palette
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(palette.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Text("TinyChat", style = TcTypography.titleLarge, color = palette.labelPrimary)
        }

        TcListRow(
            title = "新建对话",
            leading = TcIcons.NewChat,
            onClick = onNewChat,
        )

        TcSectionLabel("最近")

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            listOf(
                "DeepSeek V4.1 有哪些更新",
                "帮我写一段 Kotlin 协程示例",
                "把这份 PDF 总结成三点",
            ).forEach { title ->
                TcListRow(
                    title = title,
                    subtitle = "M0 占位",
                    onClick = { onPickSession(title) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        TcListRow(
            title = "余额",
            subtitle = "M7 接入 /user/balance",
            onClick = null,
        )
        TcListRow(
            title = "设置",
            leading = TcIcons.Settings,
            onClick = onOpenSettings,
        )
        Spacer(Modifier.height(16.dp))
    }
}
