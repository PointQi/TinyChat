package io.tinychat.feature.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.tinychat.designsystem.TcIcons
import io.tinychat.model.SessionRow
import io.tinychat.model.groupSessions
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcTypography
import io.tinychat.ui.TcIconButton
import io.tinychat.ui.TcListRow
import io.tinychat.ui.TcSectionLabel

/**
 * 会话抽屉（02 §12.3）：新建对话 → 按时间分组的会话列表 → 余额 → 设置。
 *
 * 数据来自 `ChatViewModel.sessions`（真实库），分组规则在 [groupSessions]。
 */
@Composable
fun SessionsDrawerContent(
    sessions: List<SessionRow>,
    currentSessionId: String?,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickSession: (String) -> Unit = {},
    onRenameSession: (String, String) -> Unit = { _, _ -> },
    onTogglePinSession: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteSession: (String) -> Unit = {},
) {
    val palette = TcTheme.palette
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf<SessionRow?>(null) }
    var deleting by remember { mutableStateOf<SessionRow?>(null) }

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
                .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "TinyChat",
                style = TcTypography.titleLarge,
                color = palette.labelPrimary,
                modifier = Modifier.weight(1f),
            )
        }

        TcListRow(
            title = "新建对话",
            leading = TcIcons.NewChat,
            onClick = onNewChat,
        )

        if (sessions.isEmpty()) {
            Text(
                text = "还没有对话",
                style = TcTypography.bodyMedium,
                color = palette.labelTertiary,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp),
            )
            Spacer(Modifier.weight(1f))
        } else {
            val sections = groupSessions(sessions, System.currentTimeMillis())
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                sections.forEach { section ->
                    item(key = "header-${section.group.name}") {
                        TcSectionLabel(section.group.label)
                    }
                    items(section.rows, key = { it.id }) { row ->
                        SessionItem(
                            row = row,
                            selected = row.id == currentSessionId,
                            onClick = { onPickSession(row.id) },
                            onRename = { renaming = row },
                            onTogglePin = {
                                onTogglePinSession(row.id, !row.pinned)
                                scope.launch { listState.animateScrollToItem(0) }
                            },
                            onDelete = { deleting = row },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        TcListRow(
            title = "余额",
            onClick = null,
        )
        TcListRow(
            title = "设置",
            leading = TcIcons.Settings,
            onClick = onOpenSettings,
        )
        Spacer(Modifier.height(16.dp))
    }

    renaming?.let { row ->
        RenameDialog(
            initial = row.displayTitle,
            onDismiss = { renaming = null },
            onConfirm = { title ->
                renaming = null
                onRenameSession(row.id, title)
            },
        )
    }

    deleting?.let { row ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这个对话？", style = TcTypography.titleMedium, color = palette.labelPrimary) },
            text = {
                Text(
                    text = "「${row.displayTitle}」将从列表移除。",
                    style = TcTypography.bodyMedium,
                    color = palette.labelSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    onDeleteSession(row.id)
                }) {
                    Text("删除", style = TcTypography.bodyMedium, color = palette.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text("取消", style = TcTypography.bodyMedium, color = palette.labelSecondary)
                }
            },
            containerColor = palette.bg,
        )
    }
}

@Composable
private fun SessionItem(
    row: SessionRow,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = TcTheme.palette
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) palette.selector else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.displayTitle,
            style = TcTypography.bodyLarge,
            color = palette.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box {
            TcIconButton(
                icon = TcIcons.Ellipsis,
                contentDescription = "会话操作",
                onClick = { menuOpen = true },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("重命名", style = TcTypography.bodyMedium, color = palette.labelPrimary) },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (row.pinned) "取消置顶" else "置顶",
                            style = TcTypography.bodyMedium,
                            color = palette.labelPrimary,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        onTogglePin()
                    },
                )
                DropdownMenuItem(
                    text = { Text("删除", style = TcTypography.bodyMedium, color = palette.danger) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val palette = TcTheme.palette
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名对话", style = TcTypography.titleMedium, color = palette.labelPrimary) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TcTypography.bodyLarge,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("保存", style = TcTypography.bodyMedium, color = palette.brand)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", style = TcTypography.bodyMedium, color = palette.labelSecondary)
            }
        },
        containerColor = palette.bg,
    )
}
