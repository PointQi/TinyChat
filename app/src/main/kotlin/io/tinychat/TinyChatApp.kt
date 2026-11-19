package io.tinychat

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.tinychat.data.db.PromptPresetEntity
import io.tinychat.designsystem.LocalTcFontScale
import io.tinychat.designsystem.TcTheme
import io.tinychat.feature.chat.ChatScreen
import io.tinychat.feature.chat.UsageDrawer
import io.tinychat.feature.chat.ChatViewModel
import io.tinychat.feature.sessions.SessionsDrawerContent
import io.tinychat.feature.settings.AboutScreen
import io.tinychat.feature.settings.DataManagementScreen
import io.tinychat.feature.settings.PromptEditorScreen
import io.tinychat.feature.settings.PromptPresetListScreen
import io.tinychat.feature.settings.ProviderSettingsScreen
import io.tinychat.feature.settings.SettingsScreen
import io.tinychat.model.PromptMode
import io.tinychat.net.account.KeyCheck
import kotlinx.coroutines.launch

object Routes {
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val PROVIDER = "settings/provider"
    const val DATA = "settings/data"
    const val ABOUT = "settings/about"
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
    val container = remember { AppContainer(context.applicationContext) }
    val apiKey by container.apiKey.collectAsState()
    val hasKey = apiKey.isNotBlank()
    // 字体大小（M8-4）：只放大正文，不动顶栏/设置列表
    val fontScale by container.userSettings.fontScale.collectAsState(initial = 1f)
    val language by container.userSettings.promptLanguage.collectAsState(initial = "中文")
    val themeMode by container.userSettings.themeMode.collectAsState(initial = "system")
    val userLocation by container.userSettings.promptLocation.collectAsState(initial = "")
    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.factory(
            repository = container.repository,
            apiKey = container.apiKey,
            attachmentStore = container.attachmentStore,
            voice = container.voiceController,
            settings = container.userSettings,
            account = container.account,
        ),
    )

    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var usageOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val closeDrawer = { scope.launch { drawerState.close() } }

    // 系统提示词（M7.5）：列表与当前选中项在进入页面时读库，改完后就地刷新
    var promptPresets by remember { mutableStateOf<List<PromptPresetEntity>>(emptyList()) }
    var promptSelectedId by remember { mutableStateOf("default") }
    var editingPromptId by remember { mutableStateOf<String?>(null) }
    var creatingPrompt by remember { mutableStateOf(false) }
    var promptReloadKey by remember { mutableStateOf(0) }
    LaunchedEffect(promptReloadKey) {
        promptPresets = container.promptStore.all()
        promptSelectedId = container.promptStore.current().id
    }
    val promptName = promptPresets.firstOrNull { it.id == promptSelectedId }?.name ?: "默认"

    ModalNavigationDrawer(
        drawerState = drawerState,
        // 会话页向右滑就能拉出抽屉（不必每次点左上角）；抽屉打开后向左滑收起。
        gesturesEnabled = true,
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
                balanceText = chatViewModel.state.balanceText,
                onRefreshBalance = { chatViewModel.refreshBalance(force = true) },
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.bg)
                .safeDrawingPadding(),
        ) {
            CompositionLocalProvider(LocalTcFontScale provides fontScale) {
                NavHost(navController = navController, startDestination = Routes.CHAT) {
                composable(Routes.CHAT) {
                    ChatScreen(
                        state = chatViewModel.state,
                        hasApiKey = hasKey,
                        autoSpeak = chatViewModel.state.autoSpeak,
                        onInputChange = chatViewModel::onInputChange,
                        onSend = chatViewModel::send,
                        onStop = chatViewModel::stop,
                        onToggleThinking = chatViewModel::toggleThinking,
                        onToggleSearch = chatViewModel::toggleSearch,
                        onSelectModel = chatViewModel::selectModel,
                        onOpenDrawer = {
                            scope.launch { drawerState.open() }
                            chatViewModel.refreshBalance()
                        },
                        onNewChat = chatViewModel::newChat,
                        onToggleAutoSpeak = {
                            chatViewModel.setAutoSpeak(!chatViewModel.state.autoSpeak)
                        },
                        onOpenProviderSettings = { navController.navigate(Routes.PROVIDER) },
                        onEditMessage = chatViewModel::startEdit,
                        onCancelEdit = chatViewModel::cancelEdit,
                        conversationText = chatViewModel::conversationText,
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
                            usageOpen = true
                            chatViewModel.refreshUsageNow()
                        },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenProvider = { navController.navigate(Routes.PROVIDER) },
                        onOpenData = { navController.navigate(Routes.DATA) },
                        onOpenAbout = { navController.navigate(Routes.ABOUT) },
                        hasKey = hasKey,
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
                    ProviderSettingsScreen(
                        hasKey = hasKey,
                        onBack = { navController.popBackStack() },
                        onCheck = { candidate ->
                            when (val result = container.account.checkKey(candidate)) {
                                KeyCheck.Ok -> null
                                KeyCheck.Invalid -> "Key 无效"
                                is KeyCheck.Failed -> result.message
                            }
                        },
                        onTestStored = { container.checkStoredKey() },
                        onSave = { container.saveApiKey(it) },
                        onClear = { container.clearApiKey() },
                    )
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
                    PromptPresetListScreen(
                        presets = promptPresets,
                        selectedId = promptSelectedId,
                        onBack = { navController.popBackStack() },
                        onOpen = { id ->
                            editingPromptId = id
                            creatingPrompt = false
                            scope.launch {
                                container.promptStore.select(id)
                                promptSelectedId = id
                            }
                            navController.navigate(Routes.PROMPT_EDIT)
                        },
                        onNew = {
                            editingPromptId = null
                            creatingPrompt = true
                            navController.navigate(Routes.PROMPT_EDIT)
                        },
                        onRestoreDefault = {
                            scope.launch {
                                container.promptStore.restoreDefault()
                                promptPresets = container.promptStore.all()
                                promptSelectedId = container.promptStore.current().id
                                promptReloadKey++
                            }
                        },
                    )
                }
                composable(Routes.PROMPT_EDIT) {
                    val preset = promptPresets.firstOrNull { it.id == editingPromptId }
                    val builtin = !creatingPrompt && preset?.builtin == true
                    key(editingPromptId, creatingPrompt, promptReloadKey) {
                        PromptEditorScreen(
                            title = if (creatingPrompt) "新建预设" else "编辑预设",
                            initialName = if (creatingPrompt) "" else preset?.name.orEmpty(),
                            initialContent = if (creatingPrompt) "" else preset?.content.orEmpty(),
                            initialMode = if (creatingPrompt) {
                                PromptMode.REPLACE
                            } else {
                                preset?.mode ?: PromptMode.REPLACE
                            },
                            builtin = builtin,
                            canDelete = !creatingPrompt && preset != null && !builtin,
                            onBack = { navController.popBackStack() },
                            onSave = { name, content, mode ->
                                scope.launch {
                                    val id = container.promptStore.save(
                                        id = editingPromptId.takeIf { !creatingPrompt },
                                        name = name,
                                        content = content,
                                        mode = mode,
                                    )
                                    container.promptStore.select(id)
                                    promptSelectedId = id
                                    promptPresets = container.promptStore.all()
                                    creatingPrompt = false
                                    editingPromptId = id
                                    navController.popBackStack()
                                }
                            },
                            onCopy = {
                                val source = editingPromptId ?: return@PromptEditorScreen
                                scope.launch {
                                    val copied = container.promptStore.copyBuiltin(source) ?: return@launch
                                    promptPresets = container.promptStore.all()
                                    editingPromptId = copied
                                    creatingPrompt = false
                                }
                            },
                            onDelete = {
                                val id = editingPromptId ?: return@PromptEditorScreen
                                scope.launch {
                                    container.promptStore.deleteCustom(id)
                                    promptPresets = container.promptStore.all()
                                    promptSelectedId = container.promptStore.current().id
                                    promptReloadKey++
                                    navController.popBackStack()
                                }
                            },
                        )
                    }
                }
                composable(Routes.ABOUT) {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
                }
            }

            UsageDrawer(
                visible = usageOpen,
                summary = chatViewModel.state.usageSummary,
                contextUsed = chatViewModel.state.contextUsed,
                contextWindow = chatViewModel.state.contextWindow,
                balanceText = chatViewModel.state.balanceText,
                onDismiss = { usageOpen = false },
            )
        }
    }
}
