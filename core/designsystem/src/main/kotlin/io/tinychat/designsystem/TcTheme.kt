package io.tinychat.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalTcPalette = staticCompositionLocalOf { LightPalette }

/**
 * 设置里的「字体大小」（M8-4）：1.0–1.3，只作用于**正文类**语义字号。
 * 放在色板旁边而不是塞进 Typography：它只该放大读的东西，
 * 不该把顶栏、按钮、设置列表一起撑开（那才是破版的根源）。
 */
val LocalTcFontScale = staticCompositionLocalOf { 1f }

/** 主题模式。M0 先只跟随系统，M8-4 接到设置里。 */
enum class TcThemeMode { SYSTEM, LIGHT, DARK }

/** 业务代码取色板的唯一入口：`TcTheme.palette.brand`。 */
object TcTheme {
    val palette: TcPalette
        @Composable @ReadOnlyComposable get() = LocalTcPalette.current
}

@Composable
fun TcTheme(
    mode: TcThemeMode = TcThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        TcThemeMode.SYSTEM -> isSystemInDarkTheme()
        TcThemeMode.LIGHT -> false
        TcThemeMode.DARK -> true
    }
    val palette = if (dark) DarkPalette else LightPalette
    MaterialTheme(
        colorScheme = palette.toColorScheme(dark),
        typography = TcTypography,
        shapes = TcShapes,
    ) {
        CompositionLocalProvider(LocalTcPalette provides palette, content = content)
    }
}

private fun TcPalette.toColorScheme(dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = brand,
        onPrimary = brandOn,
        primaryContainer = bubble,
        onPrimaryContainer = labelPrimary,
        background = bg,
        onBackground = labelPrimary,
        surface = bgLayer2,
        onSurface = labelPrimary,
        surfaceVariant = bubble,
        onSurfaceVariant = labelSecondary,
        surfaceContainer = codeBg,
        outline = labelTertiary,
        outlineVariant = borderL2,
        error = danger,
    )
} else {
    lightColorScheme(
        primary = brand,
        onPrimary = brandOn,
        primaryContainer = bubble,
        onPrimaryContainer = labelPrimary,
        background = bg,
        onBackground = labelPrimary,
        surface = bgLayer2,
        onSurface = labelPrimary,
        surfaceVariant = bubble,
        onSurfaceVariant = labelSecondary,
        surfaceContainer = codeBg,
        outline = labelTertiary,
        outlineVariant = borderL2,
        error = danger,
    )
}
