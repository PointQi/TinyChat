package io.tinychat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcTypography

/**
 * 按钮（决策 46）。全应用只有这一种按钮形状：**10dp 圆角矩形**；高度只有两档：
 * 页面级 44dp、内容级 32dp（[compact]）。
 *
 * 形状只表达类别，不表达轻重：
 * - **矩形 = 按钮**（会提交、保存、删除、创建）；
 * - **胶囊 = 小控件**（标签、筛选器、开关、快捷填入），见 `TcChip`。
 *
 * 轻重只由变体表达：
 * | 变体 | 用在 | 形态 |
 * |---|---|---|
 * | [Primary] | 一屏只有一个，完成这一屏的目的 | 实心 brand |
 * | [Outline] | 与主操作并列但不主导 | 1dp `borderL2` 描边 |
 * | [Text] | 行内、进入类 | 无底无框、brand 文字 |
 * | [Danger] | 删除、清除 | 无底无框、danger 文字 |
 *
 * 禁用时底色、文字色、可点性一起变——只把字变灰最像“能点但没反应”。
 */
enum class TcButtonVariant { Primary, Outline, Text, Danger }

@Composable
fun TcButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: TcButtonVariant = TcButtonVariant.Primary,
    enabled: Boolean = true,
    fillWidth: Boolean = false,
    /** 内容流里的紧凑按钮（如「继续生成」）：32dp；页面级动作保持 44dp */
    compact: Boolean = false,
) {
    val palette = TcTheme.palette
    val shape = RoundedCornerShape(10.dp)
    val height = if (compact) 32.dp else 44.dp

    val background = when {
        !enabled && variant == TcButtonVariant.Primary -> palette.selector
        variant == TcButtonVariant.Primary -> palette.brand
        else -> Color.Transparent
    }
    val textColor = when {
        !enabled -> palette.labelTertiary
        variant == TcButtonVariant.Primary -> palette.brandOn
        variant == TcButtonVariant.Danger -> palette.danger
        variant == TcButtonVariant.Text -> palette.brand
        else -> palette.labelPrimary
    }

    Box(
        modifier = modifier
            .height(height)
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(shape)
            .background(background)
            .then(
                if (variant == TcButtonVariant.Outline && enabled) {
                    Modifier.border(1.dp, palette.borderL2, shape)
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 14.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            style = TcTypography.bodyMedium,
            fontSize = if (compact) 14.sp else 16.sp,
            maxLines = 1,
        )
    }
}

/**
 * 小控件（决策 46）：标签、筛选器、开关、快捷填入。
 *
 * 与按钮的唯一区别是形状与高度：**胶囊、32dp**。它不承担“提交”语义，
 * 所以永远不出现实心品牌色底（那会读成主按钮）。
 */
@Composable
fun TcChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 打开状态（筛选器、开关类）：品牌色描边 + 品牌色文字 */
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val palette = TcTheme.palette
    val shape = RoundedCornerShape(100.dp)
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .then(
                if (enabled) {
                    Modifier.border(1.dp, if (selected) palette.brand else palette.borderL2, shape)
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TcTypography.bodyMedium,
            fontSize = 14.sp,
            color = when {
                !enabled -> palette.labelTertiary
                selected -> palette.brand
                else -> palette.labelPrimary
            },
            maxLines = 1,
        )
    }
}
