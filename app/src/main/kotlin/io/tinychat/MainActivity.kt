package io.tinychat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.tinychat.designsystem.TcTheme

/**
 * 唯一 Activity。
 *
 * ⚠️ 决策 16：**没有启动页**。冷启动直接渲染会话页；
 * Android 12+ 的系统 SplashScreen 无法移除，只能最小化（M8-7 用 core-splashscreen 收口）。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TcTheme {
                TinyChatApp()
            }
        }
    }
}
