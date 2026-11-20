package io.tinychat

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.tinychat.data.db.PromptPresetEntity
import io.tinychat.designsystem.LocalTcFontScale
import io.tinychat.designsystem.TcMotion
import io.tinychat.designsystem.TcTheme
import io.tinychat.feature.chat.ChatScreen
import io.tinychat.feature.chat.UsageDrawer
import io.tinychat.feature.chat.ChatViewModel
import io.tinychat.feature.chat.usageDrawerWidth
import io.tinychat.feature.sessions.SessionsDrawerContent
import io.tinychat.feature.settings.AboutScreen
import io.tinychat.feature.settings.DataManagementScreen
import io.tinychat.feature.settings.LicenseScreen
import io.tinychat.feature.settings.PromptEditorScreen
import io.tinychat.feature.settings.PromptSettingsScreen
import io.tinychat.feature.settings.ProviderEditorScreen
import io.tinychat.feature.settings.ProviderListScreen
import io.tinychat.feature.settings.SettingsScreen
import io.tinychat.ui.SidePanel
import io.tinychat.ui.panelTargetOpen
import io.tinychat.ui.swipeLeftToOpenPanel
import kotlinx.coroutines.launch

object Routes {
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val PROVIDER = "settings/provider"
    const val PROVIDER_EDIT = "settings/provider/edit"
    const val DATA = "settings/data"
    const val ABOUT = "settings/about"
    const val LICENSE = "settings/about/licenses"
    const val PROMPT = "settings/prompt"
    const val PROMPT_EDIT = "settings/prompt/edit"
}

/**
 * 应用外壳：唯一主界面 = 会话页（决策 16）。
 *
 * M0 范围内 `hasApiKey` 恒为 false —— 用来验证「未配置 Key」时只出现行内提示条、
 * 不弹窗、不跳转、不阻断主界面。
 */
@Composable
fun TinyChatApp() {
    val palette = TcTheme.palette

    val context = LocalContext.current
    // 关于页/设置页显示安装包版本（M9）：不在代码里写死
    @Suppress("DEPRECATION")
    val versionName = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    val container = remember { AppContainer(context.applicationContext) }
    // 服务商目录与凭据（M8-6）：设置页、服务商页、模型选择器都读它
    val providers by container.providers.collectAsState()
    val keyedProviders by container.keyedProviders.collectAsState()
    val activeProviderId by container.activeProviderId.collectAsState()
    // 字体大小（M8-4）：只放大正文，不动顶栏/设置列表
    val fontScale by container.userSettings.fontScale.collectAsState(initial = 1f)
    val language by container.userSettings.promptLanguage.collectAsState(initial = "中文")
    val themeMode by container.userSettings.themeMode.collectAsState(initial = "system")
    val userLocation by container.userSettings.promptLocation.collectAsState(initial = "")
    val providerSubtitle = when {
        keyedProviders.isEmpty() -> "未配置"
        else -> providers.firstOrNull { it.id == activeProviderId }?.displayName
            ?: "${keyedProviders.size} 个服务商"
    }
    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.factory(
            repository = container.repository,
            apiKey = container.apiKey,
            providers = container.providers,
            keyedProviders = container.keyedProviders,
            activeProviderId = container.activeProviderId,
            lastModelFor = { id -> container.lastModelFor(id) },
            attachmentStore = container.attachmentStore,
            voice = container.voiceController,
            settings = container.userSettings,
            account = container.account,
            quotaFor = { provider -> container.quotaFor(provider) },
            providerKeyFor = { id -> container.apiKeyFor(id) },
        ),
    )

    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    // 两侧面板同一时刻只能有一个（P-028）：所有权收在一个变量里，互斥是结构性的。
    // 之前左抽屉归 M3、右抽屉只是一个布尔叠加层，两者互不知道，于是能同时开着。
    var sidePanel by remember { mutableStateOf(SidePanel.None) }
    val scope = rememberCoroutineScope()
    val closeDrawer = { sidePanel = SidePanel.None }

    // 左抽屉能被手势直接拉出来，M3 不会通知任何人：这边跟着它走。
    // 反向由 gesturesEnabled 兜住 —— 右抽屉开着时左抽屉的手势根本不接收事件。
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen && sidePanel != SidePanel.Sessions) {
            sidePanel = SidePanel.Sessions
        } else if (!drawerState.isOpen && sidePanel == SidePanel.Sessions) {
            sidePanel = SidePanel.None
        }
    }
    LaunchedEffect(sidePanel) {
        if (sidePanel == SidePanel.Sessions) drawerState.open() else drawerState.close()
    }

    // 右抽屉的展开进度（P-028）：拖拽时由手指写，松手与开关由这段动画补完。
    // 只有一个动画源，所以拖到一半松手不会跳一下。
    var panelProgress by remember { mutableFloatStateOf(0f) }
    var panelDragging by remember { mutableStateOf(false) }
    LaunchedEffect(sidePanel, panelDragging) {
        if (panelDragging) return@LaunchedEffect
        val target = if (sidePanel == SidePanel.Usage) 1f else 0f
        if (panelProgress == target) return@LaunchedEffect
        animate(
            initialValue = panelProgress,
            targetValue = target,
            animationSpec = tween(
                durationMillis = if (target > 0f) TcMotion.Panel else TcMotion.Base,
                easing = if (target > 0f) TcMotion.Standard else TcMotion.Decelerate,
            ),
        ) { value, _ -> panelProgress = value }
    }
    val panelWidthPx = with(LocalDensity.current) {
        usageDrawerWidth(LocalConfiguration.current.screenWidthDp).toPx()
    }
    // 两个入口（整页左滑、面板上右滑）共用一套位移折算：dx 向左为负，进度就往大走
    val onPanelDragStart = { panelDragging = true }
    val onPanelDrag: (Float) -> Unit = { dx ->
        panelProgress = (panelProgress - dx / panelWidthPx).coerceIn(0f, 1f)
    }
    val onPanelDragEnd: (Float, Boolean) -> Unit = { velocityDp, currentlyOpen ->
        val open = panelTargetOpen(panelProgress, velocityDp, currentlyOpen)
        panelDragging = false
        sidePanel = if (open) SidePanel.Usage else SidePanel.None
    }
    // 右抽屉打开就刷一次用量，滑开的和点开的都走这里
    LaunchedEffect(sidePanel) {
        if (sidePanel == SidePanel.Usage) chatViewModel.refreshUsageNow()
    }
    // 左滑手势只挂在会话页：设置那几页也有整块内容，不该一滑就出用量面板
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    // 系统提示词（决策 45）：开关 + 用户预设。进入页面时读库，改完后就地刷新
    var promptPresets by remember { mutableStateOf<List<PromptPresetEntity>>(emptyList()) }
    var promptSelectedId by remember { mutableStateOf<String?>(null) }
    var promptIncludeOfficial by remember { mutableStateOf(true) }
    var editingPromptId by remember { mutableStateOf<String?>(null) }
    var creatingPrompt by remember { mutableStateOf(false) }
    var promptReloadKey by remember { mutableStateOf(0) }
    // 服务商编辑（M8-6）：null = 新增
    var editingProviderId by remember { mutableStateOf<String?>(null) }
    var addingProvider by remember { mutableStateOf(false) }
    LaunchedEffect(promptReloadKey) {
        promptPresets = container.promptStore.all()
        val state = container.promptStore.state()
        promptSelectedId = state.selected?.id
        promptIncludeOfficial = state.includeOfficial
    }
    val promptName = when {
        promptSelectedId != null && promptIncludeOfficial ->
            "官方提示词 + ${promptPresets.firstOrNull { it.id == promptSelectedId }?.name.orEmpty()}"

        promptSelectedId != null -> promptPresets.firstOrNull { it.id == promptSelectedId }?.name.orEmpty()
        promptIncludeOfficial -> "官方提示词"
        else -> "不发送"
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // 会话页向右滑就能拉出抽屉（不必每次点左上角）；抽屉打开后向左滑收起。
        // 右抽屉开着时整块手势关掉：否则在它的遮罩上一拖就能把左抽屉也拉出来。
        gesturesEnabled = sidePanel != SidePanel.Usage,
        drawerContent = {
            SessionsDrawerContent(
                state = chatViewModel.sessionsUi,
                currentSessionId = chatViewModel.sessionId,
                isOpen = drawerState.isOpen,
                onOpenSettings = {
                    closeDrawer()
                    navController.navigate(Routes.SETTINGS)
                },
                onPickSession = { id ->
                    chatViewModel.openSession(id)
                    closeDrawer()
                },
                onRenameSession = chatViewModel::renameSession,
                onTogglePinSession = chatViewModel::togglePinnedSession,
                onDeleteSession = chatViewModel::deleteSession,
                onOpenSearch = chatViewModel::openSearch,
                onCloseSearch = chatViewModel::closeSearch,
                onSearchQueryChange = chatViewModel::onSearchQueryChange,
                onPickSearchResult = { id ->
                    chatViewModel.pickSearchResult(id)
                    closeDrawer()
                },
                onEnterSelection = chatViewModel::enterSelection,
                onExitSelection = chatViewModel::exitSelection,
                onToggleSelected = chatViewModel::toggleSelected,
                onPinSelected = chatViewModel::pinSelected,
                onDeleteSelected = chatViewModel::deleteSelected,
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.bg)
                .safeDrawingPadding()
                // 整页左滑拉开右抽屉（P-028）：面板开着时不接收。
                // 注意别把「正在拖」也写进这个条件 —— 拖到一半把 pointerInput 摘掉会中断这次手势，
                // 松手事件就永远等不到，进度会卡在半路。
                .swipeLeftToOpenPanel(
                    enabled = currentRoute == Routes.CHAT && sidePanel == SidePanel.None,
                    onDragStart = onPanelDragStart,
                    onDragDelta = onPanelDrag,
                    onDragEnd = { velocityDp -> onPanelDragEnd(velocityDp, false) },
                ),
        ) {
            CompositionLocalProvider(LocalTcFontScale provides fontScale) {
                NavHost(navController = navController, startDestination = Routes.CHAT) {
                composable(Routes.CHAT) {
                    ChatScreen(
                        state = chatViewModel.state,
                        hasApiKey = chatViewModel.state.hasApiKey,
                        autoSpeak = chatViewModel.state.autoSpeak,
                        onInputChange = chatViewModel::onInputChange,
                        onSend = chatViewModel::send,
                        onStop = chatViewModel::stop,
                        onToggleThinking = chatViewModel::toggleThinking,
                        onToggleSearch = chatViewModel::toggleSearch,
                        onSelectModel = chatViewModel::selectModel,
                        onOpenDrawer = {
                            if (sidePanel == SidePanel.None) sidePanel = SidePanel.Sessions
                            chatViewModel.refreshBalance()
                        },
                        onNewChat = chatViewModel::newChat,
                        onToggleAutoSpeak = {
                            chatViewModel.setAutoSpeak(!chatViewModel.state.autoSpeak)
                        },
                        onOpenProviderSettings = { navController.navigate(Routes.PROVIDER) },
                        onEditMessage = chatViewModel::startEdit,
                        onCancelEdit = chatViewModel::cancelEdit,
                        onSwitchVersion = chatViewModel::switchVersion,
                        onRegenerate = chatViewModel::regenerate,
                        onContinue = chatViewModel::continueGeneration,
                        onRetry = chatViewModel::retryFailed,
                        onAddAttachments = chatViewModel::addAttachments,
                        onRemoveAttachment = chatViewModel::removeAttachment,
                        onRetryAttachment = chatViewModel::retryAttachment,
                        onQuickSend = chatViewModel::quickSend,
                        spectrum = chatViewModel.spectrum,
                        onToggleVoiceMode = chatViewModel::toggleVoiceMode,
                        onVoicePress = chatViewModel::startVoiceRecording,
                        onVoiceRelease = { cancelled ->
                            if (cancelled) {
                                chatViewModel.cancelVoiceRecording()
                            } else {
                                chatViewModel.finishVoiceRecording()
                            }
                        },
                        onReadAloud = chatViewModel::toggleSpeak,
                        onClearHint = chatViewModel::clearHint,
                        onOpenUsage = {
                            if (sidePanel == SidePanel.None) sidePanel = SidePanel.Usage
                        },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenProvider = { navController.navigate(Routes.PROVIDER) },
                        onOpenData = { navController.navigate(Routes.DATA) },
                        onOpenAbout = { navController.navigate(Routes.ABOUT) },
                        providerSubtitle = providerSubtitle,
                        versionName = versionName,
                        promptName = promptName,
                        onOpenPrompt = { navController.navigate(Routes.PROMPT) },
                        ttsVoice = chatViewModel.state.ttsVoice,
                        onSelectTtsVoice = chatViewModel::setTtsVoice,
                        showUsage = chatViewModel.state.showUsage,
                        onToggleShowUsage = chatViewModel::setShowUsage,
                        reminderEnabled = chatViewModel.state.reminderEnabled,
                        onToggleReminder = chatViewModel::setReminderEnabled,
                        language = language,
                        onSelectLanguage = { scope.launch { container.userSettings.setPromptLanguage(it) } },
                        themeMode = themeMode,
                        onSelectThemeMode = { scope.launch { container.userSettings.setThemeMode(it) } },
                        fontScale = fontScale,
                        onSelectFontScale = { scope.launch { container.userSettings.setFontScale(it) } },
                        location = userLocation,
                        onSelectLocation = { scope.launch { container.userSettings.setPromptLocation(it) } },
                    )
                }
                composable(Routes.PROVIDER) {
                    ProviderListScreen(
                        providers = providers,
                        keyedProviders = keyedProviders,
                        onBack = { navController.popBackStack() },
                        onAdd = {
                            addingProvider = true
                            editingProviderId = null
                            navController.navigate(Routes.PROVIDER_EDIT)
                        },
                        onEdit = { provider ->
                            addingProvider = false
                            editingProviderId = provider.id
                            navController.navigate(Routes.PROVIDER_EDIT)
                        },
                        onDelete = { provider ->
                            scope.launch {
                                container.deleteProvider(provider.id)
                                if (provider.id == activeProviderId) {
                                    // 删掉的正是新会话的默认项：顺位选一个还有 Key 的，
                                    // 别让下一轮新对话停在「已移除」上（决策 48 起任何行都能删）
                                    val next = container.providers.value
                                        .firstOrNull { it.id in container.keyedProviders.value }
                                        ?: container.providers.value.firstOrNull()
                                    next?.let { container.userSettings.setActiveProviderId(it.id) }
                                }
                            }
                        },
                    )
                }
                composable(Routes.PROVIDER_EDIT) {
                    val provider = if (addingProvider) {
                        null
                    } else {
                        providers.firstOrNull { it.id == editingProviderId }
                    }
                    key(editingProviderId, addingProvider) {
                        ProviderEditorScreen(
                            provider = provider,
                            existing = providers,
                            hasKey = provider?.id?.let { it in keyedProviders } == true,
                            onBack = { navController.popBackStack() },
                            onSave = { edited, key ->
                                container.saveProvider(edited, key)
                                container.checkProvider(edited, key)
                            },
                            onCheck = { edited, key -> container.checkProvider(edited, key) },
                            onClearKey = { id -> container.clearProviderKey(id) },
                            onFetchModels = { edited, key -> container.fetchModels(edited, key) },
                        )
                    }
                }
                composable(Routes.DATA) {
                    DataManagementScreen(
                        archive = container.archive,
                        onBack = { navController.popBackStack() },
                        // 导入是整包替换：当前打开的会话可能已经不存在，回到新对话
                        onImported = { chatViewModel.newChat() },
                        onClearAll = { chatViewModel.clearAllConversations() },
                    )
                }
                composable(Routes.PROMPT) {
                    PromptSettingsScreen(
                        includeOfficial = promptIncludeOfficial,
                        presets = promptPresets,
                        selectedId = promptSelectedId,
                        onBack = { navController.popBackStack() },
                        onToggleOfficial = { enabled ->
                            scope.launch {
                                container.promptStore.setIncludeOfficial(enabled)
                                promptReloadKey++
                            }
                        },
                        onSelect = { id ->
                            scope.launch {
                                container.promptStore.select(id)
                                promptReloadKey++
                            }
                        },
                        onEdit = { id ->
                            editingPromptId = id
                            creatingPrompt = false
                            navController.navigate(Routes.PROMPT_EDIT)
                        },
                        onNew = {
                            editingPromptId = null
                            creatingPrompt = true
                            navController.navigate(Routes.PROMPT_EDIT)
                        },
                    )
                }
                composable(Routes.PROMPT_EDIT) {
                    val preset = promptPresets.firstOrNull { it.id == editingPromptId }
                    key(editingPromptId, creatingPrompt, promptReloadKey) {
                        PromptEditorScreen(
                            initialName = if (creatingPrompt) "" else preset?.name.orEmpty(),
                            initialContent = if (creatingPrompt) "" else preset?.content.orEmpty(),
                            isNew = creatingPrompt || preset == null,
                            canDelete = !creatingPrompt && preset != null,
                            existingNames = promptPresets.map { it.name },
                            onBack = { navController.popBackStack() },
                            onSave = { name, content, asNew ->
                                scope.launch {
                                    val id = container.promptStore.save(
                                        id = editingPromptId.takeIf { !asNew },
                                        name = name,
                                        content = content,
                                    )
                                    container.promptStore.select(id)
                                    promptReloadKey++
                                    navController.popBackStack()
                                }
                            },
                            onDelete = {
                                val id = editingPromptId ?: return@PromptEditorScreen
                                scope.launch {
                                    container.promptStore.delete(id)
                                    promptReloadKey++
                                    navController.popBackStack()
                                }
                            },
                        )
                    }
                }
                composable(Routes.ABOUT) {
                    AboutScreen(
                        versionName = versionName,
                        onBack = { navController.popBackStack() },
                        onOpenLicenses = { navController.navigate(Routes.LICENSE) },
                    )
                }
                composable(Routes.LICENSE) {
                    LicenseScreen(onBack = { navController.popBackStack() })
                }
                }
            }

            UsageDrawer(
                progress = panelProgress,
                summary = chatViewModel.state.usageSummary,
                contextUsed = chatViewModel.state.contextUsed,
                contextWindow = chatViewModel.state.contextWindow,
                balanceText = chatViewModel.state.balanceText,
                quota = chatViewModel.state.quota,
                onDismiss = { sidePanel = SidePanel.None },
                onDragStart = onPanelDragStart,
                onDragDelta = onPanelDrag,
                onDragEnd = { velocityDp -> onPanelDragEnd(velocityDp, true) },
            )
        }
    }
}
