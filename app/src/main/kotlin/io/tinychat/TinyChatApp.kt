package io.tinychat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
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
import io.tinychat.feature.chat.ChatViewModel
import io.tinychat.feature.sessions.SessionsDrawerContent
import io.tinychat.feature.settings.AboutScreen
import io.tinychat.feature.settings.DataManagementScreen
import io.tinychat.feature.settings.ProviderSettingsScreen
import io.tinychat.feature.settings.SettingsScreen
import io.tinychat.model.PromptPreset
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
    var autoSpeak by rememberSaveable { mutableStateOf(false) }
    val promptPreset = PromptPreset.DEFAULT

    val context = LocalContext.current
    val container = remember { AppContainer(context.applicationContext) }
    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.factory(container.repository, container.apiKey),
    )

    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val closeDrawer = { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            SessionsDrawerContent(
                onNewChat = {
                    chatViewModel.newChat()
                    closeDrawer()
                },
                onOpenSettings = {
                    closeDrawer()
                    navController.navigate(Routes.SETTINGS)
                },
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
                        hasApiKey = chatViewModel.hasApiKey,
                        autoSpeak = autoSpeak,
                        onInputChange = chatViewModel::onInputChange,
                        onSend = chatViewModel::send,
                        onStop = chatViewModel::stop,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNewChat = chatViewModel::newChat,
                        onToggleAutoSpeak = { autoSpeak = !autoSpeak },
                        onOpenProviderSettings = { navController.navigate(Routes.PROVIDER) },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenProvider = { navController.navigate(Routes.PROVIDER) },
                        onOpenData = { navController.navigate(Routes.DATA) },
                        onOpenAbout = { navController.navigate(Routes.ABOUT) },
                        currentPromptPreset = promptPreset,
                    )
                }
                composable(Routes.PROVIDER) {
                    ProviderSettingsScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.DATA) {
                    DataManagementScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.ABOUT) {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
