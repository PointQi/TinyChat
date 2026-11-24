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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.tinychat.data.archive.ChatArchive
import io.tinychat.designsystem.TcIcons
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcTypography
import io.tinychat.ui.TcAlertDialog
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
    /** 当前系统提示词的状态（决策 45）：`官方提示词` / `官方提示词 + 角色扮演` / `不发送` */
    promptName: String = "官方提示词",
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
    /** 外观 / 字号 / 所在地（M8-4） */
    themeMode: String = "system",
    onSelectThemeMode: (String) -> Unit = {},
    fontScale: Float = 1f,
    onSelectFontScale: (Float) -> Unit = {},
    location: String = "",
    onSelectLocation: (String) -> Unit = {},
    webSearchSubtitle: String = "DeepSeek 内置",
    onOpenWebSearch: () -> Unit = {},
) {
    val palette = TcTheme.palette
    var voicePicker by remember { mutableStateOf(false) }
    var themePicker by remember { mutableStateOf(false) }
    var fontPicker by remember { mutableStateOf(false) }
    var locationEdit by remember { mutableStateOf(false) }
    var locationDraft by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.settingsBackground),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 62.dp).padding(horizontal = 14.dp)) {
            Box(
                modifier = Modifier.align(Alignment.CenterStart).size(44.dp).clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(36.dp).background(palette.selector, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(TcIcons.Back, "返回", tint = palette.labelPrimary, modifier = Modifier.size(17.dp))
                }
            }
            Text("设置", style = TcTypography.titleMedium, color = palette.labelPrimary, modifier = Modifier.align(Alignment.Center))
        }
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SettingsGroup("账户") {
                SettingsLink(TcIcons.Account, "模型供应商", providerSubtitle, onOpenProvider)
                SettingsLink(TcIcons.Search, "联网搜索", webSearchSubtitle, onOpenWebSearch)
                SettingsLink(TcIcons.Data, "数据管理", onClick = onOpenData)
            }
            SettingsGroup("应用") {
                SettingsLink(TcIcons.ThemeLight, "外观", themeMode.themeLabel) { themePicker = true }
                SettingsLink(TcIcons.Location, "所在地", location) {
                    locationDraft = location
                    locationEdit = true
                }
                SettingsLink(TcIcons.TypeSize, "字体大小", fontScale.fontLabel) { fontPicker = true }
                SettingsLink(TcIcons.File, "提示词", promptName, onOpenPrompt)
                SettingsSwitch(TcIcons.Account, "用户元信息", reminderEnabled, onToggleReminder)
                SettingsSwitch(TcIcons.Usage, "显示每轮用量", showUsage, onToggleShowUsage)
            }
            SettingsGroup("语音") {
                SettingsLink(TcIcons.Speak, "朗读音色", ttsVoice.displayLabel) { voicePicker = true }
            }
            SettingsGroup("关于") {
                SettingsLink(TcIcons.Info, "软件版本", versionName, onOpenAbout)
            }
        }
    }

    if (voicePicker) {
        TcAlertDialog(
            onDismissRequest = { voicePicker = false },
            title = { Text("朗读音色") },
            text = {
                Column(Modifier.selectableGroup()) {
                    TtsVoice.entries.forEach { voice ->
                        SettingsChoice(voice.displayLabel, selected = voice == ttsVoice) {
                            onSelectTtsVoice(voice)
                            voicePicker = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { voicePicker = false }) { Text("关闭", color = TcTheme.palette.labelSecondary) }
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
        TcAlertDialog(
            onDismissRequest = { locationEdit = false },
            title = { Text("所在地") },
            text = {
                Column {
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
                TextButton(onClick = { locationEdit = false }) { Text("取消", color = TcTheme.palette.labelSecondary) }
            },
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    val palette = TcTheme.palette
    Text(
        text = title,
        style = TcTypography.labelMedium,
        color = palette.labelTertiary,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp),
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(palette.bgLayer2),
    ) { content() }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun SettingsLink(icon: ImageVector, title: String, value: String = "", onClick: () -> Unit) {
    val palette = TcTheme.palette
    SettingsItem(icon, title, modifier = Modifier.clickable(role = Role.Button, onClick = onClick)) {
        if (value.isNotBlank()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Text(
                    text = value,
                    style = TcTypography.bodyMedium,
                    color = palette.labelTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(6.dp))
        }
        Icon(TcIcons.ChevronRight, null, tint = palette.labelTertiary.copy(alpha = 0.63f), modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun SettingsSwitch(icon: ImageVector, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val palette = TcTheme.palette
    SettingsItem(icon, title, modifier = Modifier.toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = palette.brand,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = palette.labelTertiary.copy(alpha = 0.4f),
            ),
        )
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier,
    trailing: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val palette = TcTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .then(modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = palette.labelPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = TcTypography.bodyLarge,
            color = palette.labelPrimary,
            modifier = Modifier.weight(2f),
        )
        trailing()
    }
}

@Composable
private fun SettingsChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = TcTheme.palette
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) palette.selector else Color.Transparent)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 50.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = TcTypography.bodyLarge, color = palette.labelPrimary, modifier = Modifier.weight(1f))
        if (selected) Icon(TcIcons.Check, null, tint = palette.brand, modifier = Modifier.size(18.dp))
    }
}

/** 官方两个音色的中文名（`stella` 海星女声 / `tide` 暗潮男声）。 */
private val TtsVoice.displayLabel: String
    get() = when (this) {
        TtsVoice.STELLA -> "海星（女）"
        TtsVoice.TIDE -> "暗潮（男）"
    }

// 外观 / 字号（M8-4）。取值直接就是落库的字符串/浮点，不做第二套枚举，
// 免得设置页、DataStore、聊天请求三处各有一份映射。
private val THEME_OPTIONS = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")
private val FONT_OPTIONS = listOf(0.9f to "小", 1f to "标准", 1.15f to "大", 1.3f to "特大")

private val String.themeLabel: String
    get() = THEME_OPTIONS.firstOrNull { it.first == this }?.second ?: "跟随系统"

private val Float.fontLabel: String
    get() = FONT_OPTIONS.firstOrNull { kotlin.math.abs(it.first - this) < 0.01f }?.second ?: "标准"

/** 外观和字号共用的单选弹窗。 */
@Composable
private fun <T> PickerDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    TcAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { (value, label) ->
                    val picked = if (value is Float && selected is Float) {
                        kotlin.math.abs(value - selected) < 0.01f
                    } else {
                        value == selected
                    }
                    SettingsChoice(label, picked) { onPick(value) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = TcTheme.palette.labelSecondary) }
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
                onClick = { if (!busy) confirmImport = true },
            )
            TcListRow(
                title = "导出对话",
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
                onClick = { if (!busy) confirmClear = true },
            )

        }
    }

    if (confirmClear) {
        TcAlertDialog(
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
                TextButton(onClick = { confirmClear = false }) { Text("取消", color = TcTheme.palette.labelSecondary) }
            },
            containerColor = palette.bg,
        )
    }

    if (confirmImport) {
        TcAlertDialog(
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
                TextButton(onClick = { confirmImport = false }) { Text("取消", color = TcTheme.palette.labelSecondary) }
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
