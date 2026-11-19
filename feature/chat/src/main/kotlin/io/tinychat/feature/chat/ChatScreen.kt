package io.tinychat.feature.chat

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import io.tinychat.designsystem.BubbleShape
import io.tinychat.designsystem.TcFavicon
import io.tinychat.designsystem.TcIcons
import io.tinychat.designsystem.TcPalette
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcTypography
import io.tinychat.markdown.CitationAnchor
import io.tinychat.markdown.CitationBubbleShape
import io.tinychat.markdown.TcMarkdown
import io.tinychat.model.AttachmentState
import io.tinychat.model.ErrorKind
import io.tinychat.model.MessageStatus
import io.tinychat.model.KnownProviders
import io.tinychat.model.ThinkingEffort
import io.tinychat.ui.TcIconButton
import io.tinychat.ui.TcInlineHint
import io.tinychat.ui.TcTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    /** 选完图片/文件（含拍照）后交给 ViewModel 读元信息 + 生成缩略图（M4-1/2） */
    onAddAttachments: (List<Uri>) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    /** 附件上传失败后重试这一轮 */
    onRetryAttachment: (ChatMessage) -> Unit,
) {
    val palette = TcTheme.palette
    val context = LocalContext.current

    // —— 附件选择器（M4-1/2）——
    // Photo Picker 走系统选择器，不需要存储权限；一次最多 9 张
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(9),
    ) { uris -> onAddAttachments(uris) }
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> onAddAttachments(uris) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { ok ->
        val uri = cameraUri
        cameraUri = null
        if (ok && uri != null) onAddAttachments(listOf(uri))
    }
    // 没有相机 App（或系统不允许查询到相机）就不必给「拍照」入口。
    // ⚠️ Android 11+ 需要 manifest 里的 <queries> 声明，否则这里恒为 null —— 真机上
    // 曾经因为漏了那段，导致「拍照」直接不出现。
    val canTakePhoto = remember(context) {
        runCatching {
            context.packageManager
                .queryIntentActivities(Intent(MediaStore.ACTION_IMAGE_CAPTURE), 0)
                .isNotEmpty()
        }.getOrDefault(false)
    }

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
                onRetryAttachment = onRetryAttachment,
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
            onRemoveAttachment = onRemoveAttachment,
            // 选图/拍照/选文件交给系统选择器，回调统一走 onAddAttachments
            onPickImages = {
                runCatching {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
            },
            onPickFiles = { runCatching { filePicker.launch(arrayOf("*/*")) } },
            canTakePhoto = canTakePhoto,
            onTakePhoto = {
                val uri = createCameraUri(context)
                if (uri != null) {
                    cameraUri = uri
                    runCatching { cameraPicker.launch(uri) }
                }
            },
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
    onRetryAttachment: (ChatMessage) -> Unit,
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
                    onRetryAttachment = { onRetryAttachment(message) },
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
    onRetryAttachment: () -> Unit,
) {
    val palette = TcTheme.palette

    if (message.isUser) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
        ) {
            // 附件（M4-4）：图片走网格、文件走卡片，都在气泡**上方**右贴边
            if (message.attachments.isNotEmpty()) {
                UserAttachments(message.attachments)
                if (message.text.isNotBlank()) Spacer(Modifier.height(6.dp))
            }
            if (message.text.isNotBlank()) {
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
            }
            if (message.hasFailedAttachment) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "附件上传失败，点击重试",
                    style = TcTypography.bodySmall,
                    color = palette.warning,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onRetryAttachment)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
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
    // 复制做一下确认：图标变对勾 1.5s，不做 Toast（官方也没弹东西）
    var copied by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

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
                    citations = message.anchors.map { CitationAnchor(it.anchor, it.index) },
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

            // 操作栏（M2-9）按官方版式固定两行：
            //   第一行：复制/朗读/分享靠左贴边 …… 重新生成靠右贴边
            //   第二行：版本切换靠左贴边 …… 继续生成靠右贴边
            // 可见性规则抽到 resolveFooterState()，单测覆盖（手动停止也要给全三件）
            val footer = resolveFooterState(
                status = message.status,
                hasText = message.text.isNotBlank(),
                versionCount = message.versionCount,
                isLatest = onRegenerate != null,
                canContinue = onContinue != null,
            )

            if (footer.visible) {
                Spacer(Modifier.height(4.dp))
            }
            if (footer.showFirstRow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (footer.showActions) {
                        ActionIcon(
                            icon = if (copied) TcIcons.Check else TcIcons.Copy,
                            description = if (copied) "已复制" else "复制",
                            tint = if (copied) palette.labelSecondary else palette.labelTertiary,
                            onClick = {
                                onCopyMessage()
                                copied = true
                            },
                        )
                        // 朗读占位：官方在 M5-4 才接 TTS，这里先做成点了没反应
                        ActionIcon(
                            icon = TcIcons.Speak,
                            description = "朗读",
                            onClick = {},
                        )
                        ActionIcon(
                            icon = TcIcons.Share,
                            description = "分享",
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, message.text)
                                }
                                context.startActivity(Intent.createChooser(send, "分享回答"))
                            },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    onRegenerate?.takeIf { footer.showRegenerate }?.let { regenerate ->
                        ActionIcon(
                            icon = TcIcons.Regenerate,
                            description = "重新生成",
                            onClick = regenerate,
                        )
                    }
                }
            }
            if (footer.showSecondRow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
 * 来源页（M2-8）：标题居中「搜索结果」，每行 = 圆形 favicon + 站点域名 + 灰色日期
 * + 右贴边角标；下面两行灰色摘要（超出省略）。点击用浏览器打开源网页。
 * M3-3 会把它扩成带「搜索结果」定位的完整抽屉。
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
                text = "搜索结果",
                style = TcTypography.titleMedium,
                color = palette.labelPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))

            sources.forEach { source ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            source.url?.let { runCatching { uriHandler.openUri(it) } }
                        }
                        .padding(vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 左侧一组（图标+站名+日期）吃掉剩余宽度，右侧角标才能真正贴右边
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TcFavicon(
                                url = source.iconUrl,
                                fallbackLetter = source.site,
                                size = 18,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = source.site,
                                style = TcTypography.bodySmall,
                                color = palette.labelPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(8.dp))
                            source.publishedDate?.let { date ->
                                Text(
                                    text = date,
                                    style = TcTypography.labelSmall,
                                    color = palette.labelTertiary,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        // 与正文角标同一个气泡形状，右贴边
                        Box(
                            modifier = Modifier
                                .size(width = 22.dp, height = 16.dp)
                                .clip(CitationBubbleShape)
                                .background(palette.inlineCodeBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = source.index.toString(),
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    lineHeight = 10.sp,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                ),
                                color = palette.labelSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = source.snippet.orEmpty(),
                        style = TcTypography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
                        color = palette.labelTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 正文末尾的来源聚合胶囊：favicon 堆叠（同站去重、最多 3 个）+ `N 个网页` */
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
            .padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        sources.distinctBy { it.site }.take(3).forEach { source ->
            TcFavicon(
                url = source.iconUrl,
                fallbackLetter = source.site,
                size = 20,
            )
            Spacer(Modifier.width(2.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${sources.size} 个网页",
            style = TcTypography.bodySmall,
            color = palette.labelSecondary,
        )
    }
}

/**
 * 操作栏里的单个灰图标按钮（M2-9）。
 * 40dp 触达、18dp 图形、无底色 —— 相邻两个按钮的图形间距正好 22dp，和官方一致。
 */
@Composable
private fun ActionIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = TcTheme.palette.labelTertiary,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(18.dp),
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
                .size(40.dp)
                .clip(CircleShape)
                .clickable(enabled = canPrev) { onSwitch(-1) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = TcIcons.Back,
                contentDescription = "上一版",
                tint = if (canPrev) palette.labelSecondary else palette.labelTertiary.copy(alpha = 0.35f),
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = "$index / $count",
            style = TcTypography.bodyMedium,
            color = palette.labelSecondary,
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(enabled = canNext) { onSwitch(1) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = TcIcons.ChevronRight,
                contentDescription = "下一版",
                tint = if (canNext) palette.labelSecondary else palette.labelTertiary.copy(alpha = 0.35f),
                modifier = Modifier.size(16.dp),
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
    onRemoveAttachment: (String) -> Unit,
    onPickImages: () -> Unit,
    onPickFiles: () -> Unit,
    canTakePhoto: Boolean,
    onTakePhoto: () -> Unit,
) {
    val palette = TcTheme.palette
    val dark = isSystemInDarkTheme()
    // 深色下卡片与背景同色会糊在一起，用 bgLayer2 抬一层
    val cardColor = if (dark) palette.bgLayer2 else palette.bg
    val inputFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var attachSheet by remember { mutableStateOf(false) }
    var previewImage by remember { mutableStateOf<DraftAttachment?>(null) }

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

        // 附件预览行（M4-1）：选中后立刻能删，发送时一起上传
        if (state.attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.attachments.forEach { draft ->
                    DraftChip(
                        draft = draft,
                        onRemove = { onRemoveAttachment(draft.id) },
                        onOpen = { if (draft.isImage) previewImage = draft },
                    )
                }
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
            // 编辑态不给加附件（编辑只改文字；进入编辑时草稿已清空）
            if (state.editingMessageId == null && state.canAttach) {
                CircleIcon(TcIcons.Attach, "附件", palette.labelSecondary) { attachSheet = true }
                Spacer(Modifier.width(4.dp))
            }
            when {
                state.isStreaming -> CircleIcon(TcIcons.Stop, "停止生成", palette.brand, filled = true, onClick = onStop)
                state.input.isNotBlank() || state.attachments.isNotEmpty() ->
                    CircleIcon(TcIcons.Send, "发送", palette.brand, filled = true, onClick = onSend)
                else -> CircleIcon(TcIcons.Mic, "语音输入", palette.labelSecondary) {}
            }
        }
    }

    // 附件来源选择（M4-1/2）：相册 / 拍照 / 文件
    if (attachSheet) {
        AttachmentSheet(
            showCamera = canTakePhoto,
            onDismiss = { attachSheet = false },
            onImages = {
                attachSheet = false
                onPickImages()
            },
            onCamera = {
                attachSheet = false
                onTakePhoto()
            },
            onFiles = {
                attachSheet = false
                onPickFiles()
            },
        )
    }

    // 点缩略图放大看（草稿阶段的图直接按缩略图画，原件还没落盘）
    previewImage?.let { draft ->
        AttachmentPreviewDialog(
            thumbPath = draft.thumbPath,
            title = draft.fileName,
            aspectRatio = draft.width?.takeIf { it > 0 }?.let { w ->
                draft.height?.takeIf { it > 0 }?.let { h -> w.toFloat() / h }
            },
            onDismiss = { previewImage = null },
        )
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

// ——————————————————————————————————————————————————————————————
// 附件（M4）
// ——————————————————————————————————————————————————————————————

/**
 * 消息里的附件（M4-4），版式对齐 chat-demo 的官方实现
 * （`UserMessage.module.css` + `MessageFile.module.css`）：
 *
 * - 一列 `flex-wrap`、右贴边、间距 10dp；
 * - 图片是**正方形缩略图**：只有图片时 120dp，与文档混排时缩到 64dp；
 * - 圆角 16dp、1dp 描边、`object-fit: cover`；上传中压 30% 黑蒙层 + 转圈，失败描红边 + 重试图标。
 *
 * 注意：不是「1 张 1:1 / 2 张 2 列 / 3 张 3 列」的九宫格 —— 那是照截图猜的，
 * 参考实现用的是固定尺寸缩略图自动折行（2026-11-19 按 chat-demo 更正）。
 */
@Composable
private fun UserAttachments(attachments: List<ChatAttachment>) {
    val palette = TcTheme.palette
    val hasDocuments = attachments.any { !it.isImage }
    val size = if (hasDocuments) 64.dp else 120.dp
    val gap = 10.dp
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    val images = attachments.filter { it.isImage }

    // ⚠️ 必须给 BoxWithConstraints 指定 contentAlignment：里面的 Column 是 wrap-content，
    // Column(horizontalAlignment = End) 只对**它自己的宽度**生效，靠不满整行。
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomEnd,
    ) {
        val perRow = (((maxWidth - gap) / (size + gap)).toInt()).coerceAtLeast(1)
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            attachments.chunked(perRow).forEach { rowItems ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    rowItems.forEach { item ->
                        if (item.isImage) {
                            val indexInImages = images.indexOf(item)
                            AttachmentThumb(
                                attachment = item,
                                size = size,
                                onClick = { previewIndex = indexInImages },
                            )
                        } else {
                            FileAttachmentCard(attachment = item, palette = palette)
                        }
                    }
                }
            }
        }
    }

    previewIndex?.let { index ->
        val item = images.getOrNull(index) ?: return@let
        AttachmentPreviewDialog(
            thumbPath = item.imagePath,
            title = item.fileName,
            aspectRatio = item.width?.takeIf { it > 0 }?.let { w ->
                item.height?.takeIf { it > 0 }?.let { h -> w.toFloat() / h }
            },
            onDismiss = { previewIndex = null },
        )
    }
}

@Composable
private fun AttachmentThumb(attachment: ChatAttachment, size: Dp, onClick: () -> Unit) {
    val palette = TcTheme.palette
    val load = rememberBitmapLoad(attachment.imagePath, (size.value * 3).toInt())
    // 只有正在上传才转圈；LOCAL（导入的历史附件）表示原件在本机、无需再传
    val uploading = attachment.state == AttachmentState.UPLOADING
    val failed = attachment.state == AttachmentState.FAILED

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.bubble)
            .border(
                width = 1.dp,
                color = if (failed) palette.danger else palette.bubble,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(enabled = attachment.imagePath != null && !uploading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            load.bitmap != null -> Image(
                bitmap = load.bitmap,
                contentDescription = attachment.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // 解码中：留空，别闪一个占位图标（会被误看成"图没了"）
            load.loading -> Unit
            else -> Icon(
                imageVector = if (attachment.isMissing) TcIcons.PhotoError else TcIcons.Photo,
                contentDescription = attachment.fileName,
                tint = palette.labelSecondary,
                modifier = Modifier.size(28.dp),
            )
        }

        // 上传中：30% 黑蒙层 + 转圈（官方同款）
        if (uploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            }
        } else if (failed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TcIcons.Regenerate,
                    contentDescription = "附件上传失败",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** 消息内的文件卡：40dp 图标 + 名 + 「扩展名 大小」（官方 FileCard 版式） */
@Composable
private fun FileAttachmentCard(attachment: ChatAttachment, palette: TcPalette) {
    Row(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.bubble)
            .border(
                width = 1.dp,
                color = if (attachment.isFailed) palette.danger else palette.bubble,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.brand.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(TcIcons.File, null, tint = palette.brand, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = attachment.fileName,
                style = TcTypography.bodySmall,
                color = palette.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    attachment.isMissing -> "原件未导入"
                    attachment.state == AttachmentState.UPLOADING -> "上传中"
                    attachment.isFailed -> "上传失败"
                    else -> attachment.fileDescription
                },
                style = TcTypography.bodySmall,
                color = if (attachment.isFailed || attachment.isMissing) {
                    palette.danger.takeIf { attachment.isFailed } ?: palette.labelTertiary
                } else {
                    palette.labelTertiary
                },
            )
        }
    }
}

/** 输入框上方的草稿缩略图（64dp，右上角删除） */
@Composable
private fun DraftChip(draft: DraftAttachment, onRemove: () -> Unit, onOpen: () -> Unit) {
    val palette = TcTheme.palette
    val load = rememberBitmapLoad(draft.thumbPath, 192)
    Box(modifier = Modifier.size(56.dp)) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.bubble)
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            if (draft.isImage && load.bitmap != null) {
                Image(
                    bitmap = load.bitmap,
                    contentDescription = draft.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(TcIcons.File, null, tint = palette.labelSecondary, modifier = Modifier.size(20.dp))
                    Text(
                        text = draft.fileName.substringAfterLast('.').uppercase().take(4),
                        style = TcTypography.bodySmall,
                        color = palette.labelTertiary,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(palette.labelSecondary)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = TcIcons.Close,
                contentDescription = "移除附件",
                tint = palette.bg,
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

/** 附件来源面板（相册 / 拍照 / 文件） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentSheet(
    showCamera: Boolean,
    onDismiss: () -> Unit,
    onImages: () -> Unit,
    onCamera: () -> Unit,
    onFiles: () -> Unit,
) {
    val palette = TcTheme.palette
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.bg,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SheetAction(TcIcons.Photo, "从相册选择", onImages)
            if (showCamera) SheetAction(TcIcons.Camera, "拍照", onCamera)
            SheetAction(TcIcons.Folder, "选择文件", onFiles)
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val palette = TcTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = palette.labelSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(text = label, style = TcTypography.bodyLarge, color = palette.labelPrimary)
    }
}

/**
 * 放大看一张图（草稿与消息共用）。黑框就是最终承载图片的容器。
 *
 * ⚠️ 两个坑：
 * 1. 「还在解码」和「真的没有」必须分开：解码是异步的，第一帧必然是空，
 *    早先把空当成"图没了"，于是先闪一句「图片已不在本机」再出图；
 * 2. 容器**第一帧就要有最终尺寸**（按附件宽高算好的 `aspectRatio`）：
 *    否则加载态是被内容撑出来的"又宽又矮的黑条"，出图后盒子突然变高、
 *    弹窗重新居中，看起来就是"图先偏上、再往上跳一下"。
 */
@Composable
private fun AttachmentPreviewDialog(
    thumbPath: String?,
    title: String,
    aspectRatio: Float?,
    onDismiss: () -> Unit,
) {
    if (thumbPath == null) return
    val load = rememberBitmapLoad(thumbPath, 1080)
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (aspectRatio != null && aspectRatio > 0f) {
                        Modifier.aspectRatio(aspectRatio)
                    } else {
                        // 老数据可能没带宽高：至少别把盒子压成一条
                        Modifier.heightIn(min = 160.dp)
                    },
                )
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            when {
                load.bitmap != null -> Image(
                    bitmap = load.bitmap,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                // 还在解码：同一个盒子里转圈，尺寸不变
                load.loading -> CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                // 解码真的失败了（文件被删/损坏）才说这一句
                else -> Text(
                    text = "图片已不在本机",
                    style = TcTypography.bodySmall,
                    color = Color.White,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
    }
}

/** 一次异步解码的三种状态：还在读 / 读出来了 / 读不出来 */
private data class BitmapLoad(val bitmap: ImageBitmap? = null, val loading: Boolean = true)

@Composable
private fun rememberBitmapLoad(path: String?, targetPx: Int): BitmapLoad {
    val state by produceState(BitmapLoad(), path, targetPx) {
        value = BitmapLoad(loading = true)
        val bitmap = if (path.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching { decodeSampled(path, targetPx) }.getOrNull()
        }
        value = BitmapLoad(bitmap = bitmap, loading = false)
    }
    return state
}

/** 按目标像素降采样解码，避免把 4000px 的原图整张读进内存 */
private fun decodeSampled(path: String, targetPx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) {
        sample *= 2
    }
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?.asImageBitmap()
}

/** 按目标像素降采样解码，避免把 4000px 的原图整张读进内存 */
@Composable
private fun rememberAttachmentBitmap(path: String?, targetPx: Int): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, targetPx) {
        value = if (path.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { decodeSampled(path, targetPx) }.getOrNull()
            }
        }
    }
    return bitmap
}

/** 拍照的落点：cache/camera/ 下，用 FileProvider 授权给相机 App */
private fun createCameraUri(context: Context): Uri? {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "capture-${System.currentTimeMillis()}.jpg")
    return runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()
}
