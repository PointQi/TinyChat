package io.tinychat.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tinychat.designsystem.BubbleShape
import io.tinychat.designsystem.TcIcons
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcTypography
import io.tinychat.markdown.CitationAnchor
import io.tinychat.markdown.SourceBadge
import io.tinychat.markdown.TcMarkdown
import io.tinychat.model.ErrorKind
import io.tinychat.model.MessageStatus
import io.tinychat.model.KnownProviders
import io.tinychat.model.ThinkingEffort
import io.tinychat.ui.TcIconButton
import io.tinychat.ui.TcInlineHint
import io.tinychat.ui.TcTopBar
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * 会话页 = 应用唯一主界面（决策 16：冷启动直达这里，没有启动页/引导页）。
 *
 * 布局对齐 `02 §12.1/§12.2/§13`。M1 正文仍是朴素 `Text`，Markdown 留给 M2。
 */
@Composable
fun ChatScreen(
    state: ChatUiState,
    hasApiKey: Boolean,
    autoSpeak: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onToggleThinking: () -> Unit,
    onToggleSearch: () -> Unit,
    onSelectModel: (String, ThinkingEffort) -> Unit,
    onOpenDrawer: () -> Unit,
    onNewChat: () -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onOpenProviderSettings: () -> Unit,
    onEditMessage: (ChatMessage) -> Unit,
    onCancelEdit: () -> Unit,
    conversationText: () -> String,
    onSwitchVersion: (Long, Int) -> Unit,
    onRegenerate: (ChatMessage) -> Unit,
    onContinue: (ChatMessage) -> Unit,
    onRetry: (ChatMessage) -> Unit,
) {
    val palette = TcTheme.palette

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg),
    ) {
        TcTopBar(
            leading = { TcIconButton(TcIcons.Menu, "会话列表", onOpenDrawer) },
            title = state.title,
            trailing = {
                TcIconButton(
                    icon = TcIcons.Speak,
                    contentDescription = if (autoSpeak) "关闭自动朗读" else "开启自动朗读",
                    onClick = onToggleAutoSpeak,
                )
                TcIconButton(TcIcons.PlusCircle, "新建对话", onNewChat)
            },
        )

        if (state.messages.isEmpty()) {
            Greeting(modifier = Modifier.weight(1f))
        } else {
            MessageList(
                state = state,
                modifier = Modifier.weight(1f),
                onEditMessage = onEditMessage,
                conversationText = conversationText,
                onSwitchVersion = onSwitchVersion,
                onRegenerate = onRegenerate,
                onContinue = onContinue,
                onRetry = onRetry,
            )
        }

        state.error?.let { message ->
            val action = if (state.errorKind == ErrorKind.HTTP_401) onOpenProviderSettings else null
            Text(
                text = if (action != null) "$message，点这里去设置" else message,
                style = TcTypography.bodySmall,
                color = palette.danger,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (action != null) Modifier.clickable(onClick = action) else Modifier)
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        if (!hasApiKey) {
            TcInlineHint(
                text = "尚未配置服务商，点击设置 API Key",
                onClick = onOpenProviderSettings,
            )
        }

        InputCard(
            state = state,
            onInputChange = onInputChange,
            onSend = onSend,
            onStop = onStop,
            onToggleThinking = onToggleThinking,
            onToggleSearch = onToggleSearch,
            onSelectModel = onSelectModel,
            onCancelEdit = onCancelEdit,
        )

        if (state.messages.isNotEmpty()) {
            Text(
                text = "回答由 AI 生成，仅供参考",
                style = TcTypography.bodySmall,
                color = palette.labelTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 10.dp),
            )
        } else {
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun Greeting(modifier: Modifier = Modifier) {
    val palette = TcTheme.palette
    val hour = remember { LocalTime.now().hour }
    val greeting = when {
        hour < 12 -> "早上好"
        hour < 18 -> "下午好"
        else -> "晚上好"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$greeting，我能帮什么忙吗？",
            style = TcTypography.headlineSmall,
            color = palette.labelPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MessageList(
    state: ChatUiState,
    modifier: Modifier = Modifier,
    onEditMessage: (ChatMessage) -> Unit,
    conversationText: () -> String,
    onSwitchVersion: (Long, Int) -> Unit,
    onRegenerate: (ChatMessage) -> Unit,
    onContinue: (ChatMessage) -> Unit,
    onRetry: (ChatMessage) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    // 用户自己拖离底部就停止跟随，别把人拽回去；拖回底部后恢复。
    val following = rememberFollowLatest(listState)

    // 上下划消息区时收起输入法（点消息不算拖动，不会误触发）。
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                focus.clearFocus()
                keyboard?.hide()
            }
        }
    }

    // 自动跟随：新增消息、正文变长、思考区展开、键盘弹起让视口变矮，都会重新布局，
    // 因此不再依赖 messages.size —— 流式输出期间也能一直贴着底部。
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }.collect {
            if (following.value && !listState.isScrollInProgress) listState.jumpToBottom()
        }
    }

    // 换了分支（重新生成 / 继续生成 / 新消息）就恢复跟随，否则新答案会停在屏幕外面
    LaunchedEffect(state.messages.lastOrNull()?.id) {
        if (state.messages.isNotEmpty()) following.value = true
    }

    val showToBottom by remember {
        derivedStateOf { !listState.layoutInfo.isAtLatestBottom() }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            // 四边留白用 Modifier.padding 而不是 contentPadding：后者属于滚动内容，
            // 滚到底之后还剩 12dp 要再滚一次 —— 动画会分两段，末尾那一下看着像瞬移。
            // 放在外面，留白照旧，滚到底就是一段顺到底的位移。
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(state.messages, key = { it.id }) { message ->
                val isLatest = message.id == state.messages.lastOrNull()?.id
                MessageRow(
                    message = message,
                    onTap = { onEditMessage(message) },
                    onCopyMessage = { clipboard.setText(AnnotatedString(message.text)) },
                    onCopyConversation = { clipboard.setText(AnnotatedString(conversationText())) },
                    onSwitchVersion = { delta -> onSwitchVersion(message.id, delta) },
                    onRegenerate = if (!message.isUser && isLatest) ({ onRegenerate(message) }) else null,
                    onContinue = if (!message.isUser && isLatest && message.canContinue) ({ onContinue(message) }) else null,
                    onRetry = { onRetry(message) },
                )
            }
        }

        if (showToBottom) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .size(40.dp)
                    .shadow(4.dp, CircleShape)
                    .background(TcTheme.palette.bg, CircleShape)
                    .clickable {
                        // 只滚，不动输入法：收起键盘会让视口在动画中途变大，
                        // 输入框还停在旧位置，看起来像画面撕裂。
                        following.value = true
                        scope.launch { listState.animateToBottom() }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TcIcons.ChevronDown,
                    contentDescription = "回到底部",
                    tint = TcTheme.palette.labelSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * 一条消息。
 * - 用户消息：**短按进入编辑重发**，**长按复制这条消息**；
 * - AI 消息：长按弹出菜单（复制整个会话 / 选择文本）。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MessageRow(
    message: ChatMessage,
    onTap: () -> Unit,
    onCopyMessage: () -> Unit,
    onCopyConversation: () -> Unit,
    onSwitchVersion: (Int) -> Unit,
    onRegenerate: (() -> Unit)?,
    onContinue: (() -> Unit)?,
    onRetry: () -> Unit,
) {
    val palette = TcTheme.palette

    if (message.isUser) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                if (message.retryTargetId != null) {
                    // 官方做法：失败那轮在气泡左侧挂一个橙色实心圆 + 白色重试图标，
                    // 圆钮与气泡**最后一行文字**对齐（气泡内边距 10dp，这里补回来）
                    Box(
                        modifier = Modifier
                            .padding(bottom = 10.dp, end = 8.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(palette.warning)
                            .clickable(onClick = onRetry),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = TcIcons.Regenerate,
                            contentDescription = "重试",
                            tint = palette.brandOn,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Text(
                text = message.text,
                style = TcTypography.bodyLarge,
                color = palette.labelPrimary,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .background(palette.bubble, BubbleShape)
                    .combinedClickable(onClick = onTap, onLongClick = onCopyMessage)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            message.retryHint?.let { hint ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = hint,
                    style = TcTypography.bodySmall,
                    color = palette.labelTertiary,
                )
            }
            if (message.versionCount > 1) {
                VersionSwitcher(message.versionIndex, message.versionCount, onSwitchVersion)
            }
        }
        return
    }

    var menuOpen by remember { mutableStateOf(false) }
    var selectOpen by remember { mutableStateOf(false) }
    var sourcesOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = { menuOpen = true }),
        ) {
            if (message.hasThinking) {
                ThinkingBlock(
                    timeline = message.timeline,
                    seconds = message.thinkingSeconds,
                    inProgress = message.isThinking,
                    stopped = message.status == MessageStatus.INCOMPLETE && message.text.isBlank(),
                )
                Spacer(Modifier.height(8.dp))
            }

            if (message.text.isNotBlank()) {
                // 正文走 Markdown（M2-1/2/3）：流式期间按渲染耗时节流出帧，禁止整篇重解析
                TcMarkdown(
                    content = message.text,
                    streaming = message.status == MessageStatus.WIP,
                    citations = message.sources.map { CitationAnchor(it.anchor, it.index) },
                    onCitationClick = { sourcesOpen = true },
                )
                if (message.sources.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    SourceCapsule(
                        sources = message.sources,
                        onClick = { sourcesOpen = true },
                    )
                }
            }

            // 最后一条回答给「重新生成」与「继续生成」；任一条有兄弟分支时给版本切换
            if (onContinue != null || onRegenerate != null || message.versionCount > 1) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onRegenerate != null) {
                        // 小号灰图标，与版本文字同一行高，不做又大又黑的按钮
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onRegenerate),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = TcIcons.Regenerate,
                                contentDescription = "重新生成",
                                tint = palette.labelTertiary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    if (message.versionCount > 1) {
                        VersionSwitcher(message.versionIndex, message.versionCount, onSwitchVersion)
                    }
                    Spacer(Modifier.weight(1f))
                    if (onContinue != null) {
                        // 灰色胶囊 + 黑字，靠右
                        Text(
                            text = "继续生成",
                            style = TcTypography.bodyMedium,
                            color = palette.labelPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .border(1.dp, palette.labelTertiary.copy(alpha = 0.5f), RoundedCornerShape(100.dp))
                                .clickable(onClick = onContinue)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("复制", style = TcTypography.bodyMedium, color = palette.labelPrimary) },
                onClick = {
                    menuOpen = false
                    onCopyConversation()
                },
            )
            DropdownMenuItem(
                text = { Text("选择文本", style = TcTypography.bodyMedium, color = palette.labelPrimary) },
                onClick = {
                    menuOpen = false
                    selectOpen = true
                },
            )
        }
    }

    if (selectOpen) {
        ModalBottomSheet(
            onDismissRequest = { selectOpen = false },
            sheetState = sheetState,
            containerColor = palette.bg,
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = "选择文本",
                    style = TcTypography.titleMedium,
                    color = palette.labelPrimary,
                )
                Spacer(Modifier.height(10.dp))
                SelectionContainer {
                    Text(
                        text = message.text,
                        style = TcTypography.bodyLarge,
                        color = palette.labelPrimary,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (sourcesOpen) {
        SourceSheet(
            sources = message.sources,
            sheetState = sheetState,
            onDismiss = { sourcesOpen = false },
        )
    }
}

/**
 * 来源列表（M2-8 的临时壳子：点角标/胶囊都落到这里）。
 * M3-3 会换成「搜索结果抽屉」，带日期、摘要与序号定位，这里只保证有落点。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceSheet(
    sources: List<ChatSource>,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
) {
    val palette = TcTheme.palette
    val uriHandler = LocalUriHandler.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.bg,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "参考来源（${sources.size}）",
                style = TcTypography.titleMedium,
                color = palette.labelPrimary,
            )
            Spacer(Modifier.height(12.dp))
            sources.forEach { source ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            source.url?.let { runCatching { uriHandler.openUri(it) } }
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.inlineCodeBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = source.index.toString(),
                            fontSize = 10.sp,
                            color = palette.labelSecondary,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = source.title ?: source.site,
                            style = TcTypography.bodyMedium,
                            color = palette.labelPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = source.site,
                            style = TcTypography.labelSmall,
                            color = palette.labelTertiary,
                        )
                    }
                }
            }
        }
    }
}

/** 正文末尾的来源聚合胶囊：图标堆叠 + `N 个网页`（M2-8） */
@Composable
private fun SourceCapsule(
    sources: List<ChatSource>,
    onClick: () -> Unit,
) {
    val palette = TcTheme.palette
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .border(1.dp, palette.borderL2, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 站点首字母堆叠：同一站点只出现一次，最多 3 个
        sources.distinctBy { it.site }.take(3).forEach { source ->
            Box(modifier = Modifier.size(16.dp)) {
                SourceBadge(letter = source.site.take(1).uppercase())
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${sources.size} 个网页",
            style = TcTypography.labelSmall,
            color = palette.labelSecondary,
        )
    }
}

/**
 * 版本切换 `‹ 2/3 ›`（M1-8）。到头那一侧箭头置灰，点了也不动（不循环）。
 */
@Composable
private fun VersionSwitcher(index: Int, count: Int, onSwitch: (Int) -> Unit) {
    val palette = TcTheme.palette
    val canPrev = index > 1
    val canNext = index < count

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(enabled = canPrev) { onSwitch(-1) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = TcIcons.Back,
                contentDescription = "上一版",
                tint = if (canPrev) palette.labelSecondary else palette.labelTertiary.copy(alpha = 0.35f),
                modifier = Modifier.size(13.dp),
            )
        }
        Text(
            text = "$index / $count",
            style = TcTypography.bodyMedium,
            color = palette.labelSecondary,
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(enabled = canNext) { onSwitch(1) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = TcIcons.ChevronRight,
                contentDescription = "下一版",
                tint = if (canNext) palette.labelSecondary else palette.labelTertiary.copy(alpha = 0.35f),
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

/**
 * 思维链折叠块（§13）：折叠 `已思考（用时 N 秒）›`；展开为左侧竖线 + 圆点时间轴，
 * 中间穿插工具步骤卡片。
 */
@Composable
private fun ThinkingBlock(
    timeline: List<TimelineItem>,
    seconds: Double?,
    inProgress: Boolean,
    stopped: Boolean,
) {
    val palette = TcTheme.palette
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(inProgress) { expanded = inProgress }

    val label = when {
        inProgress -> "正在思考"
        // 思考阶段被掐断：不假装「已思考完」，直接标已停止
        stopped -> "已停止"
        else -> {
            val secs = seconds?.let { it.toInt().coerceAtLeast(1) }
            if (secs != null) "已思考（用时 $secs 秒）" else "已思考"
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = TcIcons.Think,
                contentDescription = null,
                tint = if (inProgress) palette.brand else palette.labelSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            // 文案切换（正在思考 → 已思考（用时 N 秒））用淡入淡出，别硬跳
            Crossfade(
                targetState = label,
                animationSpec = tween(durationMillis = 180),
                label = "thinking-label",
            ) { text ->
                Text(text = text, style = TcTypography.bodyMedium, color = palette.labelSecondary)
            }
            Spacer(Modifier.width(4.dp))
            // 一个图标转 90°：收起是 ›，展开是 ˅（官方就是这个手感，别用两张图硬切）
            val chevronRotation by animateFloatAsState(
                targetValue = if (expanded) 90f else 0f,
                animationSpec = tween(durationMillis = 180),
                label = "thinking-chevron",
            )
            Icon(
                imageVector = TcIcons.ChevronRight,
                contentDescription = if (expanded) "收起思维链" else "展开思维链",
                tint = palette.labelTertiary,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(chevronRotation),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 200),
                expandFrom = Alignment.Top,
            ) + fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 180),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(animationSpec = tween(durationMillis = 120)),
        ) {
            Row(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .height(IntrinsicSize.Min),
            ) {
                // 左侧 2dp 竖线
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(palette.borderL2, RoundedCornerShape(1.dp)),
                )
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    timeline.forEach { item ->
                        when (item) {
                            is TimelineItem.Text -> TimelineText(item.content)
                            is TimelineItem.Tool -> ToolCard(item.step)
                        }
                    }
                }
            }
        }
    }
}

/** 时间轴上的一段思考：每行一个圆点 */
@Composable
private fun TimelineText(content: String) {
    val palette = TcTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        content.split('\n').filter { it.isNotBlank() }.forEach { line ->
            Row {
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .size(4.dp)
                        .background(palette.labelTertiary, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = line.trim(),
                    style = TcTypography.bodyMedium,
                    color = palette.labelSecondary,
                )
            }
        }
    }
}

/** 工具步骤卡片：`搜索到 N 个网页` + 站点堆叠 + 展开结果列表 */
@Composable
private fun ToolCard(step: ToolStep) {
    val palette = TcTheme.palette
    var expanded by remember { mutableStateOf(false) }

    val icon = when (step.kind) {
        ToolKind.SEARCH -> TcIcons.Search
        ToolKind.OPEN -> TcIcons.Globe
        ToolKind.FIND -> TcIcons.Search
    }

    Column {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = step.items.isNotEmpty()) { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = palette.labelSecondary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = step.title,
                style = TcTypography.bodyMedium,
                color = palette.labelSecondary,
            )
            if (step.items.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                SiteStack(step.items)
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = if (expanded) TcIcons.ChevronDown else TcIcons.ChevronRight,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = palette.labelTertiary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        if (expanded) {
            Column(
                modifier = Modifier.padding(start = 21.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                step.items.forEachIndexed { index, name ->
                    Text(
                        text = "${index + 1}. $name",
                        style = TcTypography.bodySmall,
                        color = palette.labelTertiary,
                    )
                }
            }
        }
    }
}

/** 站点图标堆叠：最多 3 个 + 溢出圆（真图待 M2 接 Coil） */
@Composable
private fun SiteStack(items: List<String>) {
    val palette = TcTheme.palette
    Row {
        items.take(3).forEachIndexed { index, name ->
            Box(
                modifier = Modifier
                    // 负 padding 会抛异常，重叠只能用 offset
                    .offset(x = if (index == 0) 0.dp else (-4).dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (index % 2 == 0) palette.bubble else palette.inlineCodeBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.take(1),
                    fontSize = 8.sp,
                    color = palette.labelSecondary,
                )
            }
        }
        if (items.size > 3) {
            Box(
                modifier = Modifier
                    .offset(x = (-4).dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(palette.labelTertiary),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "+", fontSize = 8.sp, color = palette.bg)
            }
        }
    }
}

@Composable
private fun InputCard(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onToggleThinking: () -> Unit,
    onToggleSearch: () -> Unit,
    onSelectModel: (String, ThinkingEffort) -> Unit,
    onCancelEdit: () -> Unit,
) {
    val palette = TcTheme.palette
    val dark = isSystemInDarkTheme()
    // 深色下卡片与背景同色会糊在一起，用 bgLayer2 抬一层
    val cardColor = if (dark) palette.bgLayer2 else palette.bg
    val inputFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // 输入框内容由外部（state.input）驱动：从外面写进来时（例如点消息进入编辑）
    // 光标要落在**末尾**，否则会停在开头还要手动挪。
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(state.input, TextRange(state.input.length)))
    }
    LaunchedEffect(state.input) {
        if (fieldValue.text != state.input) {
            fieldValue = TextFieldValue(state.input, TextRange(state.input.length))
        }
    }

    // 点消息进入编辑态时，输入框要直接拿到焦点并弹输入法（否则还要再点一下）
    LaunchedEffect(state.editingMessageId) {
        if (state.editingMessageId != null) {
            inputFocus.requestFocus()
            keyboard?.show()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .shadow(3.dp, RoundedCornerShape(24.dp))
            .background(cardColor, RoundedCornerShape(24.dp))
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
    ) {
        // 编辑态提示条
        if (state.editingMessageId != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "正在编辑消息：",
                    style = TcTypography.bodySmall,
                    color = palette.brand,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "取消",
                    style = TcTypography.bodySmall,
                    color = palette.labelSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onCancelEdit)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 4.dp),
        ) {
            if (state.input.isEmpty()) {
                Text(
                    text = "发消息或按住说话",
                    style = TcTypography.bodyLarge,
                    color = palette.labelTertiary,
                )
            }
            BasicTextField(
                value = fieldValue,
                onValueChange = { newValue ->
                    fieldValue = newValue
                    onInputChange(newValue.text)
                },
                textStyle = TcTypography.bodyLarge.copy(color = palette.labelPrimary),
                cursorBrush = SolidColor(palette.brand),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(inputFocus),
                maxLines = 8,
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModelEffortPill(
                modelId = state.modelId,
                effort = state.effort,
                onSelect = onSelectModel,
            )
            Spacer(Modifier.width(8.dp))
            SearchPill(enabled = state.searchEnabled, onClick = onToggleSearch)
            Spacer(Modifier.weight(1f))
            CircleIcon(TcIcons.Attach, "附件", palette.labelSecondary) {}
            Spacer(Modifier.width(4.dp))
            when {
                state.isStreaming -> CircleIcon(TcIcons.Stop, "停止生成", palette.brand, filled = true, onClick = onStop)
                state.input.isNotBlank() -> CircleIcon(TcIcons.Send, "发送", palette.brand, filled = true, onClick = onSend)
                else -> CircleIcon(TcIcons.Mic, "语音输入", palette.labelSecondary) {}
            }
        }
    }
}

/** 模型与强度 pill：显示如 `Flash 高`（决策 20：不加中点），点开半屏选择器 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelEffortPill(
    modelId: String,
    effort: ThinkingEffort,
    onSelect: (String, ThinkingEffort) -> Unit,
) {
    val palette = TcTheme.palette
    var sheetOpen by remember { mutableStateOf(false) }
    val modelLabel = if (modelId == KnownProviders.v4Pro.id) "V4 Pro" else "Flash"

    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(palette.brand.copy(alpha = 0.10f))
            .clickable { sheetOpen = true }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$modelLabel ${effort.displayName}",
            style = TcTypography.bodySmall,
            color = palette.brand,
            fontWeight = FontWeight.Medium,
        )
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = palette.bg,
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = "模型与强度",
                    style = TcTypography.titleMedium,
                    color = palette.labelPrimary,
                )
                Spacer(Modifier.height(12.dp))

                listOf(KnownProviders.flash, KnownProviders.v4Pro).forEach { model ->
                    val name = if (model.id == KnownProviders.v4Pro.id) "V4 Pro" else "Flash"
                    val usable = model.capabilities.webSearch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
                                style = TcTypography.bodyLarge,
                                color = if (usable) palette.labelPrimary else palette.labelSecondary,
                            )
                            if (!usable) {
                                Text(
                                    text = "不支持联网与文件",
                                    style = TcTypography.bodySmall,
                                    color = palette.labelTertiary,
                                )
                            }
                        }
                        ThinkingEffort.entries.forEach { option ->
                            EffortChip(
                                text = option.displayName,
                                selected = model.id == modelId && option == effort,
                                onClick = {
                                    sheetOpen = false
                                    onSelect(model.id, option)
                                },
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun EffortChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val palette = TcTheme.palette
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(if (selected) palette.brand else palette.bubble)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TcTypography.bodySmall,
            color = if (selected) palette.brandOn else palette.labelSecondary,
        )
    }
}

/** 搜索 pill：只显示图标。开 = 品牌色，关 = 灰（§12.1） */
@Composable
private fun SearchPill(enabled: Boolean, onClick: () -> Unit) {
    val palette = TcTheme.palette
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(if (enabled) palette.brand.copy(alpha = 0.10f) else palette.bubble)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = TcIcons.Globe,
            contentDescription = if (enabled) "关闭智能搜索" else "开启智能搜索",
            tint = if (enabled) palette.brand else palette.labelSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun CircleIcon(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = TcTheme.palette
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (filled) palette.brand else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (filled) palette.brandOn else tint,
            modifier = Modifier.size(if (filled) 18.dp else 22.dp),
        )
    }
}
