package io.tinychat.designsystem

import androidx.compose.ui.graphics.Color

/**
 * 色板（M0-3）。取值来自 chat-demo `src/app/theme.css`（MIT），见 docs/02 §10.2。
 * 浅色/深色成对维护，**不要在业务代码里写死颜色**。
 */
data class TcPalette(
    val brand: Color,
    val brandOn: Color,
    val bubble: Color,
    val bg: Color,
    val bgLayer2: Color,
    val labelPrimary: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val borderL2: Color,
    val codeBg: Color,
    val inlineCodeBg: Color,
    val danger: Color,
)

val LightPalette = TcPalette(
    brand = Color(0xFF3964FE),
    brandOn = Color(0xFFFFFFFF),
    bubble = Color(0xFFEDF3FE),
    bg = Color(0xFFFFFFFF),
    bgLayer2 = Color(0xFFFFFFFF),
    labelPrimary = Color(0xFF0F1115),
    labelSecondary = Color(0xFF61666B),
    labelTertiary = Color(0xFF81858C),
    borderL2 = Color(0x1A000000),
    codeBg = Color(0xFFF9FAFB),
    inlineCodeBg = Color(0xFFEBEEF2),
    danger = Color(0xFFEC1313),
)

val DarkPalette = TcPalette(
    brand = Color(0xFF5686FE),
    brandOn = Color(0xFFFFFFFF),
    bubble = Color(0xFF2C2C2E),
    bg = Color(0xFF151517),
    bgLayer2 = Color(0xFF2C2C2E),
    labelPrimary = Color(0xFFF9FAFB),
    labelSecondary = Color(0xFFCFD3D6),
    labelTertiary = Color(0xFFADB2B8),
    borderL2 = Color(0x1FFFFFFF),
    codeBg = Color(0xFF1B1B1C),
    inlineCodeBg = Color(0xFF2C2C2E),
    danger = Color(0xFFF25A5A),
)
