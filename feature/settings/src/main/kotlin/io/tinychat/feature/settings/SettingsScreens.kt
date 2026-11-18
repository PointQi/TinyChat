package io.tinychat.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.tinychat.data.archive.ChatArchive
import io.tinychat.designsystem.TcIcons
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcTypography
import io.tinychat.ui.TcIconButton
import io.tinychat.ui.TcListRow
import io.tinychat.ui.TcSectionLabel
import io.tinychat.ui.TcTopBar
import io.tinychat.model.PromptPreset
import kotlinx.coroutines.launch

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

/**
 * 数据管理（docs/02 §12.8，M6-5）。
 *
 * 导入 = **整包替换**（官方导出的语义就是"恢复备份"），所以先弹确认；
 * 导出 = 直接流式写 SAF 指定的位置。
 */
@Composable
fun DataManagementScreen(
    archive: ChatArchive,
    onBack: () -> Unit,
    onImported: () -> Unit,
) {
    val palette = TcTheme.palette
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmImport by remember { mutableStateOf(false) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingUri = uri }

    val exportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        status = "正在导出…"
        scope.launch {
            try {
                val report = archive.export(uri) { done, total ->
                    val value = "正在导出 $done/$total"
                    scope.launch { status = value }
                }
                status = "已导出 ${report.sessions} 个会话、${report.messages} 条消息，" +
                    "${formatSize(report.bytes)}（用时 ${report.durationMs / 1000}s）"
            } catch (e: Exception) {
                status = "导出失败：${e.message ?: e::class.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(pendingUri) {
        val uri = pendingUri ?: return@LaunchedEffect
        pendingUri = null
        busy = true
        status = "正在导入…"
        try {
            val report = archive.import(uri)
            status = "已导入 ${report.sessions} 个会话、${report.messages} 条消息、" +
                "${report.files} 个附件（丢弃 ${report.droppedFragments} 条提示碎片，" +
                "用时 ${report.durationMs / 1000}s）"
            onImported()
        } catch (e: Exception) {
            status = "导入失败：${e.message ?: e::class.simpleName}"
        } finally {
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg),
    ) {
        TcTopBar(
            title = "数据管理",
            leading = { TcIconButton(TcIcons.Back, "返回", onBack) },
        )
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            TcSectionLabel("备份")
            TcListRow(
                title = "导入对话",
                subtitle = if (busy) "正在处理，请稍候…" else "DeepSeek 导出包（v3）",
                onClick = { if (!busy) confirmImport = true },
            )
            TcListRow(
                title = "导出对话",
                subtitle = if (busy) "正在处理，请稍候…" else "兼容模式（纯 v3，不含提示词与用量）",
                onClick = {
                    if (!busy) {
                        exportPicker.launch("tinychat-backup-${System.currentTimeMillis()}.zip")
                    }
                },
            )

            status?.let { text ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = text,
                    style = TcTypography.bodySmall,
                    color = palette.labelSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "导入会覆盖本机现有对话；导出不包含提示词预设与本机用量统计。",
                style = TcTypography.bodySmall,
                color = palette.labelTertiary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("导入会覆盖现有对话？") },
            text = { Text("当前设备上的全部对话与附件都会被替换成包里内容，且不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    importPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                }) { Text("选择文件") }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false }) { Text("取消") }
            },
            containerColor = palette.bg,
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
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
