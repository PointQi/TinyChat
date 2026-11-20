package io.tinychat.feature.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tinychat.data.db.PromptPresetEntity
import io.tinychat.designsystem.TcIcons
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcTypography
import io.tinychat.model.PromptMode
import io.tinychat.model.prompt.PromptVariables
import io.tinychat.ui.TcIconButton
import io.tinychat.ui.TcListRow
import io.tinychat.ui.TcSectionLabel
import io.tinychat.ui.TcTopBar

/** 组合方式的短名与一句解释（02 §12.7）。 */
private fun PromptMode.title(): String = when (this) {
    PromptMode.REPLACE -> "替换"
    PromptMode.APPEND -> "追加"
    PromptMode.CLEAR -> "清空"
}

private fun PromptMode.explain(): String = when (this) {
    PromptMode.REPLACE -> "仅发送当前自定义内容"
    PromptMode.APPEND -> "先发送官方提示词，再拼接当前内容"
    PromptMode.CLEAR -> "不发送任何系统提示词"
}

/**
 * 预设列表（M7-5-2）。
 *
 * 点一行 = **选中并打开**：会去改提示词的人，几乎总是想改那一条。
 * 选中即落库，之后新建的会话才用它（决策 10）。
 */
@Composable
fun PromptPresetListScreen(
    presets: List<PromptPresetEntity>,
    selectedId: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onRestoreDefault: () -> Unit,
) {
    val palette = TcTheme.palette
    var confirmRestore by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(palette.bg)) {
        TcTopBar(title = "系统提示词", leading = { TcIconButton(TcIcons.Back, "返回", onBack) })
        Column(Modifier.verticalScroll(rememberScrollState())) {
            presets.forEach { preset ->
                TcListRow(
                    title = preset.name,
                    subtitle = if (preset.mode == PromptMode.REPLACE) null else preset.mode.explain(),
                    trailing = {
                        if (preset.id == selectedId) {
                            Icon(
                                imageVector = TcIcons.Checked,
                                contentDescription = "当前",
                                tint = palette.brand,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    onClick = { onOpen(preset.id) },
                )
            }

            TcListRow(
                title = "新建预设",
                leading = TcIcons.PlusCircle,
                onClick = onNew,
            )

            Spacer(Modifier.height(8.dp))
            TcSectionLabel("维护")
            TcListRow(title = "恢复默认", onClick = { confirmRestore = true })
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("恢复默认") },
            text = { Text("默认提示词将恢复为官方原文，你的自定义内容不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    onRestoreDefault()
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("取消") }
            },
        )
    }
}

/**
 * 预设编辑器（M7-5-2）。
 *
 * 内置预设只读：底部只给「复制后编辑」，避免把官方原文改坏；
 * 未知变量当场报错并禁用保存（02 §5.6：只替换白名单）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptEditorScreen(
    title: String,
    initialName: String,
    initialContent: String,
    initialMode: PromptMode,
    builtin: Boolean,
    canDelete: Boolean,
    onBack: () -> Unit,
    onSave: (name: String, content: String, mode: PromptMode) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = TcTheme.palette
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    var body by remember { mutableStateOf(TextFieldValue(initialContent)) }
    var mode by remember { mutableStateOf(initialMode) }
    var confirmDelete by remember { mutableStateOf(false) }

    val unknown = PromptVariables.unknown(body.text)
    val chars = body.text.length
    val tokens = (chars * 2 + 2) / 3
    val invalid = unknown.isNotEmpty()
    val savable = !builtin && name.isNotBlank() &&
        (mode == PromptMode.CLEAR || body.text.isNotBlank()) && !invalid

    Column(Modifier.fillMaxSize().background(palette.bg)) {
        TcTopBar(
            title = title,
            leading = { TcIconButton(TcIcons.Back, "返回", onBack) },
            trailing = {
                if (!builtin) {
                    TextButton(onClick = {
                        if (savable) {
                            onSave(name, body.text, mode)
                            Toast.makeText(context, "已保存，仅对新会话生效", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text(
                            text = "保存",
                            fontSize = 16.sp,
                            color = if (savable) palette.brand else palette.labelTertiary,
                        )
                    }
                }
            },
        )

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            if (builtin) {
                Text(
                    "内置预设不能改，要改请先复制一份。",
                    style = TcTypography.labelMedium,
                    color = palette.labelTertiary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
            EditorLabel("名称")
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(palette.selector, RoundedCornerShape(10.dp))
                    .border(1.dp, palette.borderL2, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { if (!builtin) name = it.take(30) },
                    enabled = !builtin,
                    textStyle = TcTypography.bodyLarge.copy(color = palette.labelPrimary),
                    cursorBrush = SolidColor(palette.brand),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (name.isEmpty()) {
                            Text(
                                "给这个预设起个名字",
                                color = palette.labelTertiary,
                                style = TcTypography.bodyLarge,
                            )
                        }
                        inner()
                    },
                )
            }

            Spacer(Modifier.height(20.dp))
            EditorLabel("组合方式")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PromptMode.entries.forEach { item ->
                    val picked = item == mode
                    Text(
                        text = item.title(),
                        fontSize = 14.sp,
                        color = if (picked) palette.brandOn else palette.labelSecondary,
                        modifier = Modifier
                            .background(
                                if (picked) palette.brand else palette.selector,
                                RoundedCornerShape(8.dp),
                            )
                            .border(
                                1.dp,
                                if (picked) palette.brand else palette.borderL2,
                                RoundedCornerShape(8.dp),
                            )
                            .clickable(enabled = !builtin) { mode = item }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
            Text(
                mode.explain(),
                style = TcTypography.labelMedium,
                color = palette.labelTertiary,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                EditorLabel("内容", modifier = Modifier.weight(1f))
                Text(
                    "$chars 字  约 $tokens tok",
                    style = TcTypography.labelMedium,
                    color = palette.labelTertiary,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp)
                    .background(palette.selector, RoundedCornerShape(10.dp))
                    .border(1.dp, palette.borderL2, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = body,
                    onValueChange = { if (!builtin) body = it },
                    readOnly = builtin,
                    textStyle = TcTypography.bodyLarge.copy(color = palette.labelPrimary),
                    cursorBrush = SolidColor(palette.brand),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    decorationBox = { inner ->
                        if (body.text.isEmpty()) {
                            Text(
                                if (mode == PromptMode.CLEAR) "组合方式为清空时，这里可以留空" else "在这里写提示词",
                                color = palette.labelTertiary,
                                style = TcTypography.bodyLarge,
                            )
                        }
                        inner()
                    },
                )
            }

            if (!builtin) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PromptVariables.names.forEach { variable ->
                        Text(
                            text = "{{$variable}}",
                            style = TcTypography.labelMedium,
                            color = palette.labelSecondary,
                            modifier = Modifier
                                .background(palette.selector, RoundedCornerShape(8.dp))
                                .border(1.dp, palette.borderL2, RoundedCornerShape(8.dp))
                                .clickable { body = insertVariable(body, variable) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            if (invalid) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "不认识的变量：${unknown.joinToString(" ") { "{{$it}}" }}",
                    style = TcTypography.labelMedium,
                    color = palette.danger,
                )
            }

            if (builtin) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "复制后编辑",
                    fontSize = 16.sp,
                    color = palette.brandOn,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.brand, RoundedCornerShape(10.dp))
                        .clickable(onClick = onCopy)
                        .padding(vertical = 12.dp),
                )
            }

            if (canDelete) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "删除这个预设",
                    fontSize = 15.sp,
                    color = palette.danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { confirmDelete = true }
                        .padding(vertical = 10.dp),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除预设") },
            text = { Text("此操作无法恢复。删除后不会影响已用它创建的会话。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun EditorLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = TcTypography.labelMedium,
        color = TcTheme.palette.labelTertiary,
        modifier = modifier.padding(bottom = 6.dp),
    )
}

/** 在光标处插入变量；有选区就替换掉选区。 */
private fun insertVariable(value: TextFieldValue, variable: String): TextFieldValue {
    val token = "{{$variable}}"
    val start = value.selection.min
    val end = value.selection.max
    val text = value.text.replaceRange(start, end, token)
    return value.copy(text = text, selection = TextRange(start + token.length))
}
