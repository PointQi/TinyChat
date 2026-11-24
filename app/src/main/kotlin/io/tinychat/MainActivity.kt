package io.tinychat

import android.animation.ValueAnimator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import io.tinychat.data.settings.UserSettings
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcThemeMode

/** 冷启动直接渲染会话页，将系统开屏图标交接给首页标记。 */
class MainActivity : ComponentActivity() {
    private var splashLanding: SplashLanding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val landing = if (savedInstanceState == null && ValueAnimator.areAnimatorsEnabled()) {
            SplashLanding().also { splash.setOnExitAnimationListener(it::start) }
        } else {
            null
        }
        splashLanding = landing
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
                val lightBackground = TcTheme.palette.bg.luminance() > 0.5f
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = lightBackground
                        isAppearanceLightNavigationBars = lightBackground
                    }
                }
                TinyChatApp(greetingLogoModifier = landing?.logoModifier ?: Modifier)
            }
        }
    }

    override fun onDestroy() {
        splashLanding?.dispose()
        super.onDestroy()
    }
}
