package io.tinychat.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import io.tinychat.model.TtsVoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 设置树（docs/02 §12.5）。M0 只有导航与占位项。 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenProvider: () -> Unit,
    onOpenData: () -> Unit,
    onOpenAbout: () -> Unit,
    /** 是否已配置 Key（M7-6）：决定那一行的副标题 */
    hasKey: Boolean = false,
    currentPromptPreset: PromptPreset = PromptPreset.DEFAULT,
    /** 当前朗读音色（M5-4，只有官方两个） */
    ttsVoice: TtsVoice = TtsVoice.STELLA,
    onSelectTtsVoice: (TtsVoice) -> Unit = {},
    /** 每轮统计条开关（M7-3，默认开） */
    showUsage: Boolean = true,
    onToggleShowUsage: (Boolean) -> Unit = {},
    /** 用户元信息开关（M7-5，默认开） */
    reminderEnabled: Boolean = true,
    onToggleReminder: (Boolean) -> Unit = {},
) {
    val palette = TcTheme.palette
    var voicePicker by remember { mutableStateOf(false) }
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
                subtitle = if (hasKey) "DeepSeek 官方" else "未配置",
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

            SettingsSwitch(title = "显示每轮用量", checked = showUsage, onCheckedChange = onToggleShowUsage)
            SettingsSwitch(title = "用户元信息", checked = reminderEnabled, onCheckedChange = onToggleReminder)

            TcSectionLabel("语音")
            // 只有官方两个音色（决策 3），不做系统 TTS 兜底
            TcListRow(
                title = "朗读音色",
                subtitle = ttsVoice.displayLabel,
                onClick = { voicePicker = true },
            )

            TcSectionLabel("关于")
            TcListRow(title = "版本", subtitle = "0.1.0-m0", onClick = onOpenAbout)
            TcListRow(title = "服务协议与隐私", onClick = {})
            TcListRow(title = "帮助与反馈", onClick = {})
        }
    }

    if (voicePicker) {
        AlertDialog(
            onDismissRequest = { voicePicker = false },
            title = { Text("朗读音色") },
            text = {
                Column {
                    TtsVoice.entries.forEach { voice ->
                        TcListRow(
                            title = voice.displayLabel,
                            subtitle = if (voice == ttsVoice) "当前" else null,
                            onClick = {
                                onSelectTtsVoice(voice)
                                voicePicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { voicePicker = false }) { Text("关闭") }
            },
        )
    }
}

/** 设置里的一行开关：右侧 Switch，点了立刻生效。 */
@Composable
private fun SettingsSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val palette = TcTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = TcTypography.bodyLarge,
            color = palette.labelPrimary,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = palette.brand,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = palette.labelTertiary.copy(alpha = 0.4f),
            ),
        )
    }
}

/** 官方两个音色的中文名（`stella` 海星女声 / `tide` 暗潮男声）。 */
private val TtsVoice.displayLabel: String
    get() = when (this) {
        TtsVoice.STELLA -> "海星（女）"
        TtsVoice.TIDE -> "暗潮（男）"
    }

/**
 * 服务商与 API Key（docs/02 §12.6，M7-6）。
 *
 * 只做 DeepSeek 官方一家（决策 23）：一个 Key、一个状态、三个动作。
 * 多服务商与 OpenAI 兼容留到 M8。
 */
@Composable
fun ProviderSettingsScreen(
    hasKey: Boolean,
    onBack: () -> Unit,
    /** 返回 `null` = 校验通过，否则是给用户看的一行错误文案（`GET /models`，不计费） */
    onCheck: suspend (String) -> String?,
    onSave: suspend (String) -> Unit,
    onClear: suspend () -> Unit,
) {
    val palette = TcTheme.palette
    val scope = rememberCoroutineScope()
    var field by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    fun checkThenSave() {
        val candidate = field.trim()
        if (candidate.isEmpty() || busy) return
        scope.launch {
            busy = true
            message = null
            failed = false
            val error = onCheck(candidate)
            if (error == null) {
                onSave(candidate)
                field = ""
                message = "已保存"
            } else {
                failed = true
                message = error
            }
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg),
    ) {
        TcTopBar(
            title = "服务商与 API Key",
            leading = { TcIconButton(TcIcons.Back, "返回", onBack) },
        )
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "DeepSeek 官方",
                style = TcTypography.bodyLarge,
                color = palette.labelPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.borderL2, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp)
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = field,
                    onValueChange = {
                        field = it
                        message = null
                        failed = false
                    },
                    singleLine = true,
                    textStyle = TcTypography.bodyLarge.copy(color = palette.labelPrimary),
                    cursorBrush = SolidColor(palette.brand),
                    visualTransformation =
                        if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (reveal) "隐藏" else "显示",
                    style = TcTypography.bodyMedium,
                    color = palette.labelSecondary,
                    modifier = Modifier
                        .clickable { reveal = !reveal }
                        .padding(start = 12.dp),
                )
            }

            Spacer(Modifier.height(10.dp))
            val statusText = message ?: if (hasKey) "已配置" else "未配置"
            val statusColor = when {
                failed -> palette.danger
                message != null -> palette.labelSecondary
                hasKey -> palette.brand
                else -> palette.labelTertiary
            }
            Text(text = statusText, style = TcTypography.bodyMedium, color = statusColor)

            Spacer(Modifier.height(16.dp))
            Row {
                TextButton(
                    onClick = ::checkThenSave,
                    enabled = field.isNotBlank() && !busy,
                ) {
                    Text("保存并验证", color = if (field.isNotBlank() && !busy) palette.brand else palette.labelTertiary)
                }
                TextButton(
                    onClick = ::checkThenSave,
                    enabled = field.isNotBlank() && !busy,
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    Text("测试连接", color = palette.labelSecondary)
                }
                TextButton(
                    onClick = { confirmClear = true },
                    enabled = hasKey && !busy,
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    Text("清除", color = palette.danger)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "只保存在本机，不会上传。",
                style = TcTypography.bodyMedium,
                color = palette.labelTertiary,
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除 API Key？") },
            text = { Text("清除后需要重新填写才能继续对话，历史记录不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        busy = true
                        onClear()
                        message = "已清除"
                        failed = false
                        busy = false
                    }
                }) { Text("清除", color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
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
    onClearAll: () -> Unit,
) {
    val palette = TcTheme.palette
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmImport by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    /**
     * 导入必须从**回调里直接 launch**，不能像之前那样「先存 pendingUri 再 LaunchedEffect」：
     * 那样在 effect 里把 key 置空会让 Compose 立刻取消这个协程，导入刚开始就被掐断，
     * 而取消异常又被 `catch (e: Exception)` 吞成一句「The coroutine scope left the composition」。
     */
    fun startImport(uri: Uri) {
        if (busy) return
        busy = true
        status = "正在导入…"
        scope.launch {
            try {
                val report = archive.import(uri)
                status = "已导入 ${report.sessions} 个会话、${report.messages} 条消息、" +
                    "${report.files} 个附件（丢弃 ${report.droppedFragments} 条提示碎片，" +
                    "用时 ${report.durationMs / 1000}s）"
                onImported()
            } catch (e: CancellationException) {
                // 离开页面时被取消很正常，别当成"导入失败"显示给用户
                throw e
            } catch (e: Exception) {
                status = "导入失败：${e.message ?: e::class.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) startImport(uri) }

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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "导出失败：${e.message ?: e::class.simpleName}"
            } finally {
                busy = false
            }
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

            TcSectionLabel("清理")
            TcListRow(
                title = "清空所有对话",
                subtitle = "连同本机附件一起删除，不可撤销",
                onClick = { if (!busy) confirmClear = true },
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "导入会覆盖本机现有对话；导出不包含提示词预设与本机用量统计。",
                style = TcTypography.bodySmall,
                color = palette.labelTertiary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空所有对话？") },
            text = { Text("本机全部对话与附件原件都会被删除，且不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearAll()
                    status = "已清空本机所有对话"
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
            containerColor = palette.bg,
        )
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
