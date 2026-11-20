package io.tinychat.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
    /** 服务商那一行的副标题（M8-6）：`未配置` / 当前服务商名 / `N 个服务商` */
    providerSubtitle: String = "未配置",
    /** 当前安装包的版本名（M9）：显示在「关于」那一行的副标题 */
    versionName: String = "",
    /** 当前系统提示词的名字（M7.5）：显示为「当前预设：默认」 */
    promptName: String = "默认",
    onOpenPrompt: () -> Unit = {},
    /** 当前朗读音色（M5-4，只有官方两个） */
    ttsVoice: TtsVoice = TtsVoice.STELLA,
    onSelectTtsVoice: (TtsVoice) -> Unit = {},
    /** 每轮统计条开关（M7-3，默认开） */
    showUsage: Boolean = true,
    onToggleShowUsage: (Boolean) -> Unit = {},
    /** 用户元信息开关（M7-5，默认开） */
    reminderEnabled: Boolean = true,
    onToggleReminder: (Boolean) -> Unit = {},
    /** 语言 / 外观 / 字号 / 所在地（M8-4） */
    language: String = "中文",
    onSelectLanguage: (String) -> Unit = {},
    themeMode: String = "system",
    onSelectThemeMode: (String) -> Unit = {},
    fontScale: Float = 1f,
    onSelectFontScale: (Float) -> Unit = {},
    location: String = "",
    onSelectLocation: (String) -> Unit = {},
) {
    val palette = TcTheme.palette
    var voicePicker by remember { mutableStateOf(false) }
    var languagePicker by remember { mutableStateOf(false) }
    var themePicker by remember { mutableStateOf(false) }
    var fontPicker by remember { mutableStateOf(false) }
    var locationEdit by remember { mutableStateOf(false) }
    var locationDraft by remember { mutableStateOf("") }
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
                subtitle = providerSubtitle,
                onClick = onOpenProvider,
            )
            TcListRow(
                title = "数据管理",
                subtitle = "导入 / 导出（兼容 DeepSeek 客户端格式）",
                onClick = onOpenData,
            )

            TcSectionLabel("应用")
            TcListRow(title = "语言", subtitle = language, onClick = { languagePicker = true })
            TcListRow(title = "外观", subtitle = themeMode.themeLabel, onClick = { themePicker = true })
            TcListRow(title = "字体大小", subtitle = fontScale.fontLabel, onClick = { fontPicker = true })
            TcListRow(
                title = "所在地",
                subtitle = location.ifBlank { "未设置" },
                onClick = {
                    locationDraft = location
                    locationEdit = true
                },
            )
            TcListRow(
                title = "系统提示词",
                subtitle = "当前预设：$promptName",
                onClick = onOpenPrompt,
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
            TcListRow(
                title = "关于",
                subtitle = if (versionName.isBlank()) "版本未知" else "版本 $versionName",
                onClick = onOpenAbout,
            )
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

    if (languagePicker) {
        PickerDialog(
            title = "语言",
            options = LANGUAGE_OPTIONS,
            selected = language,
            onDismiss = { languagePicker = false },
            onPick = {
                languagePicker = false
                onSelectLanguage(it)
            },
        )
    }

    if (themePicker) {
        PickerDialog(
            title = "外观",
            options = THEME_OPTIONS,
            selected = themeMode,
            onDismiss = { themePicker = false },
            onPick = {
                themePicker = false
                onSelectThemeMode(it)
            },
        )
    }

    if (fontPicker) {
        PickerDialog(
            title = "字体大小",
            options = FONT_OPTIONS,
            selected = fontScale,
            onDismiss = { fontPicker = false },
            onPick = {
                fontPicker = false
                onSelectFontScale(it)
            },
        )
    }

    if (locationEdit) {
        AlertDialog(
            onDismissRequest = { locationEdit = false },
            title = { Text("所在地") },
            text = {
                Column {
                    Text(
                        "填写所在地，让回答更贴合你的本地场景。\n可以留空。",
                        style = TcTypography.labelMedium,
                        color = palette.labelTertiary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(palette.selector, RoundedCornerShape(10.dp))
                            .border(1.dp, palette.borderL2, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        BasicTextField(
                            value = locationDraft,
                            onValueChange = { locationDraft = it.take(20) },
                            textStyle = TcTypography.bodyLarge.copy(color = palette.labelPrimary),
                            cursorBrush = SolidColor(palette.brand),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (locationDraft.isEmpty()) {
                                    Text(
                                        "例如：浙江",
                                        style = TcTypography.bodyLarge,
                                        color = palette.labelTertiary,
                                    )
                                }
                                inner()
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    locationEdit = false
                    onSelectLocation(locationDraft)
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { locationEdit = false }) { Text("取消") }
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

// 语言 / 外观 / 字号（M8-4）。取值直接就是落库的字符串/浮点，不做第二套枚举，
// 免得设置页、DataStore、聊天请求三处各有一份映射。
private val LANGUAGE_OPTIONS = listOf("中文" to "中文", "English" to "English")
private val THEME_OPTIONS = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")
private val FONT_OPTIONS = listOf(0.9f to "小", 1f to "标准", 1.15f to "大", 1.3f to "特大")

private val String.themeLabel: String
    get() = THEME_OPTIONS.firstOrNull { it.first == this }?.second ?: "跟随系统"

private val Float.fontLabel: String
    get() = FONT_OPTIONS.firstOrNull { kotlin.math.abs(it.first - this) < 0.01f }?.second ?: "标准"

/** 单选弹窗：外观/字号/语言共用一套，避免三份几乎一样的 AlertDialog。 */
@Composable
private fun <T> PickerDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    val picked = if (value is Float && selected is Float) {
                        kotlin.math.abs(value - selected) < 0.01f
                    } else {
                        value == selected
                    }
                    TcListRow(
                        title = label,
                        subtitle = if (picked) "当前" else null,
                        onClick = { onPick(value) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
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
                subtitle = if (busy) "正在处理，请稍候…" else "导入从 DeepSeek 客户端导出的 .zip 对话记录",
                onClick = { if (!busy) confirmImport = true },
            )
            TcListRow(
                title = "导出对话",
                subtitle = if (busy) "正在处理，请稍候…" else "导出为兼容 DeepSeek 客户端的 .zip 对话记录",
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
                subtitle = "删除全部对话和附件，无法撤销。",
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
            text = { Text("当前设备上的全部对话和附件都会被替换成导入的内容，且无法撤销。") },
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
