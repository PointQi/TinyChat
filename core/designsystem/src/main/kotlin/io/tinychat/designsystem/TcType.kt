package io.tinychat.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字体令牌（docs/02 §10.2）。
 * - [TcTypography] 给 Material3 组件用；
 * - [TcMd] 是 Markdown 语义字号，供 core:markdown 渲染时逐级取用。
 */
val TcTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 25.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
)

/** Markdown 各语义级别的样式。字号缩放（设置里的「字体大小」）只作用于这里。 */
object TcMd {
    val h1 = TextStyle(fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
    val h2 = TextStyle(fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
    val h3 = TextStyle(fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
    val body = TextStyle(fontSize = 16.sp, lineHeight = 28.sp)
    val table = TextStyle(fontSize = 15.sp, lineHeight = 25.sp)
    val small = TextStyle(fontSize = 14.sp, lineHeight = 24.sp)
    val code = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, fontFamily = FontFamily.Monospace)

    /** 按 1.0–1.3 的缩放系数整体放大（上限 1.3×，避免破版）。 */
    fun scaled(base: TextStyle, factor: Float): TextStyle {
        val f = factor.coerceIn(1.0f, 1.3f)
        return base.copy(fontSize = base.fontSize * f, lineHeight = base.lineHeight * f)
    }
}
