package io.tinychat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.tinychat.designsystem.CardShape
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcTypography

/**
 * 自定义顶栏：不用 Material3 的 TopAppBar，避免实验性 API 与默认高度差异。
 * 52dp 高，左右各 8dp 内边距，标题居中偏左（对齐官方截图）。
 */
@Composable
fun TcTopBar(
    title: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val palette = TcTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Spacer(Modifier.width(4.dp))
        Text(
            text = title.orEmpty(),
            style = TcTypography.titleMedium,
            color = palette.labelPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/** 顶栏图标按钮（48dp 触达区，24dp 图标）。 */
@Composable
fun TcIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val palette = TcTheme.palette
    Row(
        modifier = Modifier
            .size(44.dp)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) palette.labelPrimary else palette.labelTertiary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 设置项 / 会话项通用行。 */
@Composable
fun TcListRow(
    title: String,
    subtitle: String? = null,
    leading: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val palette = TcTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, null, tint = palette.labelSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = TcTypography.bodyLarge, color = palette.labelPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = TcTypography.labelMedium, color = palette.labelTertiary, maxLines = 1)
            }
        }
        trailing?.invoke()
    }
}

/** 分组小标题。 */
@Composable
fun TcSectionLabel(text: String) {
    Text(
        text = text,
        style = TcTypography.labelMedium,
        color = TcTheme.palette.labelTertiary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

/** 行内提示条：用于「未配置服务商」等非阻断提示（决策 16，不做引导页/弹窗）。 */
@Composable
fun TcInlineHint(text: String, onClick: () -> Unit) {
    val palette = TcTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(palette.bubble, RoundedCornerShape(CardShape.topStart))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = TcTypography.labelMedium,
            color = palette.labelSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}
