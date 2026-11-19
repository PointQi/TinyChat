package io.tinychat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.tinychat.data.settings.UserSettings
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcThemeMode

/**
 * 唯一 Activity。
 *
 * ⚠️ 决策 16：**没有启动页**。冷启动直接渲染会话页；
 * Android 12+ 的系统 SplashScreen 无法移除，只能最小化（M8-7 用 core-splashscreen 收口）。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // M8-7：系统 SplashScreen 只保留「图标一闪」，不做动画、不人为延长（02 §11.1）
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 外观（M8-4）：读设置里的三档，改完立刻重建配色
            val settings = remember { UserSettings(applicationContext) }
            val mode by settings.themeMode.collectAsState(initial = "system")
            TcTheme(
                mode = when (mode) {
                    "light" -> TcThemeMode.LIGHT
                    "dark" -> TcThemeMode.DARK
                    else -> TcThemeMode.SYSTEM
                },
            ) {
                TinyChatApp()
            }
        }
    }
}
