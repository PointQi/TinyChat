package io.tinychat.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.tinychat.designsystem.TcIcons
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcTypography
import io.tinychat.ui.TcIconButton
import io.tinychat.ui.TcListRow
import io.tinychat.ui.TcSectionLabel
import io.tinychat.ui.TcTopBar
import io.tinychat.model.PromptPreset

/** 设置树（docs/02 §12.5）。M0 只有导航与占位项。 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenProvider: () -> Unit,
    onOpenData: () -> Unit,
    onOpenAbout: () -> Unit,
    currentPromptPreset: PromptPreset = PromptPreset.DEFAULT,
) {
    val palette = TcTheme.palette
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg),
    ) {
        TcTopBar(
            title = "设置",
            leading = { TcIconButton(TcIcons.Back, "返回", onBack) },
        )
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            TcSectionLabel("账户")
            TcListRow(
                title = "服务商与 API Key",
                subtitle = "默认 DeepSeek 官方",
                onClick = onOpenProvider,
            )
            TcListRow(
                title = "数据管理",
                subtitle = "导入 / 导出（兼容 v3 格式）",
                onClick = onOpenData,
            )

            TcSectionLabel("应用")
            TcListRow(title = "语言", subtitle = "简体中文", onClick = {})
            TcListRow(title = "外观", subtitle = "跟随系统", onClick = {})
            TcListRow(title = "字体大小", subtitle = "标准", onClick = {})
            TcListRow(
                title = "系统提示词",
                subtitle = "当前预设：${currentPromptPreset.displayName}",
                onClick = {},
            )

            TcSectionLabel("语音")
            TcListRow(title = "朗读音色", subtitle = "Stella", onClick = {})

            TcSectionLabel("关于")
            TcListRow(title = "版本", subtitle = "0.1.0-m0", onClick = onOpenAbout)
            TcListRow(title = "服务协议与隐私", onClick = {})
            TcListRow(title = "帮助与反馈", onClick = {})
        }
    }
}

/** 服务商与 API Key（docs/02 §12.6）。M0 只有壳。 */
@Composable
fun ProviderSettingsScreen(onBack: () -> Unit) {
    PlaceholderPage(
        title = "服务商与 API Key",
        onBack = onBack,
        lines = listOf(
            "DeepSeek 官方（默认）",
            "base URL：https://api.deepseek.com",
            "模型：deepseek-flash / deepseek-v4-pro",
            "",
            "通用 OpenAI 兼容（降级链路）",
            "搜索、读链接、文件、图片全部置灰",
            "",
            "M8-6 实现：Key 校验走 GET /models，不计费",
        ),
    )
}

/** 数据管理（docs/02 §12.8）。M0 只有壳。 */
@Composable
fun DataManagementScreen(onBack: () -> Unit) {
    PlaceholderPage(
        title = "数据管理",
        onBack = onBack,
        lines = listOf(
            "导入：DeepSeek 导出包（export_format_version = 3）",
            "  任何未识别 ZIP 条目 → 整包失败并回滚",
            "  session JSON 内未知字段 → 静默丢弃",
            "",
            "导出：兼容模式（纯 v3，不含提示词与用量）",
            "  不新增 ZIP 条目，不新增会话字段",
            "",
            "M6 实现：3340 会话样本 100% 往返",
        ),
    )
}

/** 关于（docs/02 §12.5 末）。 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    PlaceholderPage(
        title = "关于",
        onBack = onBack,
        lines = listOf(
            "TinyChat 0.1.0-m0（M0 工程骨架）",
            "",
            "使用你自己的 API Key 访问模型服务，",
            "本项目与深度求索公司无隶属关系。",
            "",
            "界面与协议实现参考 deepseek-ai/chat-demo（MIT）",
            "图标与 Markdown 渲染器遵循各自开源协议",
        ),
    )
}

@Composable
private fun PlaceholderPage(title: String, onBack: () -> Unit, lines: List<String>) {
    val palette = TcTheme.palette
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg),
    ) {
        TcTopBar(
            title = title,
            leading = { TcIconButton(TcIcons.Back, "返回", onBack) },
        )
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            lines.forEach { line ->
                Text(
                    text = line.ifEmpty { " " },
                    style = TcTypography.bodyMedium,
                    color = if (line.startsWith("  ")) palette.labelTertiary else palette.labelSecondary,
                )
            }
        }
    }
}
