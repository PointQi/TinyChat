package io.tinychat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import io.tinychat.designsystem.TcTheme
import io.tinychat.feature.chat.ChatScreen
import io.tinychat.feature.chat.UsageDrawer
import io.tinychat.feature.chat.ChatViewModel
import io.tinychat.feature.sessions.SessionsDrawerContent
import io.tinychat.feature.settings.AboutScreen
import io.tinychat.feature.settings.DataManagementScreen
import io.tinychat.feature.settings.ProviderSettingsScreen
import io.tinychat.feature.settings.SettingsScreen
import io.tinychat.model.PromptPreset
import io.tinychat.net.account.KeyCheck
import kotlinx.coroutines.launch

object Routes {
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val PROVIDER = "settings/provider"
    const val DATA = "settings/data"
    const val ABOUT = "settings/about"
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
    val promptPreset = PromptPreset.DEFAULT

    val context = LocalContext.current
    val container = remember { AppContainer(context.applicationContext) }
    val apiKey by container.apiKey.collectAsState()
    val hasKey = apiKey.isNotBlank()
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
                        currentPromptPreset = promptPreset,
                        ttsVoice = chatViewModel.state.ttsVoice,
                        onSelectTtsVoice = chatViewModel::setTtsVoice,
                        showUsage = chatViewModel.state.showUsage,
                        onToggleShowUsage = chatViewModel::setShowUsage,
                        reminderEnabled = chatViewModel.state.reminderEnabled,
                        onToggleReminder = chatViewModel::setReminderEnabled,
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
                composable(Routes.ABOUT) {
                    AboutScreen(onBack = { navController.popBackStack() })
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
