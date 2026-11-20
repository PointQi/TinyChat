package io.tinychat.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 品牌标记：圆角方块 + 角色图（决策 62）。
 *
 * 三处在用：首页问候语上方（72dp）、关于页头部（72dp）、分享长图页脚（14dp）。
 * 刻意不复用启动器那套 `mipmap-<密度>` 里的 `ic_launcher_foreground`：那份图按 72dp 安全区缩进
 * 2/3、四周留透明，是给启动器遮罩用的；这里是**满幅**贴图，留白会让角色变成一张小邮票。
 *
 * @param size 边长（正方形）
 * @param corner 圆角；默认边长的 25%，和启动图标的形状语言一致
 * @param ring 是否描边。分享图是白底黑块，用不上；界面里靠 [TcPalette.logoRing] 描边
 */
@Composable
fun TcLogoMark(
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = size * 0.25f,
    ring: Boolean = true,
) {
    val palette = TcTheme.palette
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape),
    ) {
        Image(
            painter = painterResource(R.drawable.tc_logo),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .background(LogoBackdrop),
        )
        // 描边必须叠在图片**之上**：Compose 的 border 画在子内容下面，
        // 挂在外层 Box 上会被这张满幅图整个盖掉。
        if (ring && palette.logoRing.alpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(1.dp, palette.logoRing, shape),
            )
        }
    }
}

/** 画面自带的底色（#020101）；只作缩放插值的兜底，正常情况下看不见。 */
private val LogoBackdrop = Color(0xFF020101)
