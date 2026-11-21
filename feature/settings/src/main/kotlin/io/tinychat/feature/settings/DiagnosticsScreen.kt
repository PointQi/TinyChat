package io.tinychat.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.tinychat.designsystem.TcIcons
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcTypography
import io.tinychat.ui.TcButton
import io.tinychat.ui.TcButtonVariant
import io.tinychat.ui.TcIconButton
import io.tinychat.ui.TcListRow
import io.tinychat.ui.TcTopBar

/** 诊断信息列表项：时间与现场由 app 侧从报告里解析后传进来。 */
data class DiagnosticsItem(
    val id: String,
    val time: String,
    val scene: String,
)

/**
 * 诊断信息（M9-2）。
 *
 * 崩溃兜底把报告写进 `filesDir/crashes`，这一页负责让用户看得见、能复制、能删——
 * 不加这个入口，那些文件写进沙盒等于石沉大海。
 *
 * 读写全在 app 侧（`CrashReporter` 在 `:app` 模块），这里只接数据与回调，不认识文件。
 */
@Composable
fun DiagnosticsScreen(
    items: List<DiagnosticsItem>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRead: (String) -> String,
    onDelete: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    val palette = TcTheme.palette
    val context = LocalContext.current
    var opened by remember { mutableStateOf<DiagnosticsItem?>(null) }
    var pendingClear by remember { mutableStateOf<DiagnosticsItem?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { onRefresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg),
    ) {
        val detail = opened
        if (detail != null) {
            val body = remember(detail.id) { onRead(detail.id) }
            TcTopBar(
                title = "诊断信息",
                leading = { TcIconButton(TcIcons.Back, "返回", { opened = null }) },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = detail.time,
                    style = TcTypography.labelMedium,
                    color = palette.labelTertiary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = body,
                    style = TcTypography.bodySmall,
                    color = palette.labelPrimary,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TcButton(
                    text = "复制",
                    onClick = { copyToClipboard(context, body) },
                    modifier = Modifier.weight(1f),
                )
                TcButton(
                    text = "清除",
                    onClick = { pendingClear = detail },
                    modifier = Modifier.weight(1f),
                    variant = TcButtonVariant.Outline,
                )
            }
        } else {
            TcTopBar(
                title = "诊断信息",
                leading = { TcIconButton(TcIcons.Back, "返回", onBack) },
                trailing = {
                    if (items.isNotEmpty()) {
                        TextButton(onClick = { confirmClearAll = true }) {
                            Text("清除", color = palette.labelSecondary, style = TcTypography.labelLarge)
                        }
                    }
                },
            )
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "暂无记录",
                        style = TcTypography.bodyMedium,
                        color = palette.labelTertiary,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { item ->
                        TcListRow(
                            title = item.time,
                            subtitle = item.scene,
                            onClick = { opened = item },
                        )
                    }
                }
            }
        }
    }

    if (pendingClear != null) {
        AlertDialog(
            onDismissRequest = { pendingClear = null },
            title = { Text("清除诊断信息？") },
            text = { Text("清除后无法恢复，不会影响你的聊天记录。") },
            confirmButton = {
                TextButton(onClick = {
                    val target = pendingClear
                    pendingClear = null
                    if (target != null) {
                        onDelete(target.id)
                        opened = null
                        onRefresh()
                    }
                }) { Text("清除", color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = null }) {
                    Text("取消", color = palette.labelSecondary)
                }
            },
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("清除诊断信息？") },
            text = { Text("清除后无法恢复，不会影响你的聊天记录。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearAll = false
                    onClearAll()
                    onRefresh()
                }) { Text("清除", color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text("取消", color = palette.labelSecondary)
                }
            },
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    manager?.setPrimaryClip(ClipData.newPlainText("TinyChat 诊断信息", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}
