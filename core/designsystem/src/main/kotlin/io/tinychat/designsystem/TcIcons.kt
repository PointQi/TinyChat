package io.tinychat.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 应用图标入口。
 *
 * **能用的全部来自 chat-demo（MIT）**，见 [ChatDemoIcons]：路径数据一字未改，机械移植，
 * 不再有手绘近似。只有 chat-demo 里确实没有的（分享、向上箭头）才保留手绘。
 *
 * 决策 15 已更新：图标不再"暂缓"，素材这一侧已经齐了。
 */
object TcIcons {

    // —— 直接来自 chat-demo ——

    val Menu: ImageVector get() = ChatDemoIcons.IconMenuOutline20
    val NewChat: ImageVector get() = ChatDemoIcons.IconNewChatOutline20
    val NewChatCircle: ImageVector get() = ChatDemoIcons.NewChatIcon
    val Send: ImageVector get() = ChatDemoIcons.IconSendOutline16
    val Stop: ImageVector get() = ChatDemoIcons.IconStopFill16
    val Mic: ImageVector get() = ChatDemoIcons.IconMicOutline16
    val MicFilled: ImageVector get() = ChatDemoIcons.IconMicFill16
    val Attach: ImageVector get() = ChatDemoIcons.IconPaperclipOutline16
    val Copy: ImageVector get() = ChatDemoIcons.IconCopyOutline16
    val Speak: ImageVector get() = ChatDemoIcons.IconPlayMediaOutline16
    val Regenerate: ImageVector get() = ChatDemoIcons.IconRefreshOutline16
    val Settings: ImageVector get() = ChatDemoIcons.IconSettingsOutline16
    val Think: ImageVector get() = ChatDemoIcons.IconThinkOutline14
    val Globe: ImageVector get() = ChatDemoIcons.IconGlobeLanguageOutline14
    val PlusCircle: ImageVector get() = ChatDemoIcons.NewChatIcon
    val Search: ImageVector get() = ChatDemoIcons.IconSearchOutline16
    val ChevronDown: ImageVector get() = ChatDemoIcons.IconChevronDownOutline14
    val ChevronRight: ImageVector get() = ChatDemoIcons.IconChevronRightOutline14

    // 设置与数据管理会用到
    val Edit: ImageVector get() = ChatDemoIcons.IconEditOutline16
    val Trash: ImageVector get() = ChatDemoIcons.IconTrashOutline16
    val Close: ImageVector get() = ChatDemoIcons.IconCloseOutline16
    val CloseFilled: ImageVector get() = ChatDemoIcons.IconCloseFill14
    val Check: ImageVector get() = ChatDemoIcons.IconCheckOutline16
    val Checked: ImageVector get() = ChatDemoIcons.IconCheckedOutline14
    val Link: ImageVector get() = ChatDemoIcons.IconLinkOutline16
    val LinkSmall: ImageVector get() = ChatDemoIcons.IconLinkOutline14
    val ExternalLink: ImageVector get() = ChatDemoIcons.IconRightUpOutline14
    val Download: ImageVector get() = ChatDemoIcons.IconDownloadOutline16
    val Upload: ImageVector get() = ChatDemoIcons.IconUploadOutline14
    val Ellipsis: ImageVector get() = ChatDemoIcons.IconEllipsisOutline16
    val Browse: ImageVector get() = ChatDemoIcons.IconBrowseOutline16
    val BrowseWarning: ImageVector get() = ChatDemoIcons.IconBrowseWarningOutline16
    val SearchWarning: ImageVector get() = ChatDemoIcons.IconSearchWarningOutline16
    val Warning: ImageVector get() = ChatDemoIcons.IconWarningOutline16
    val ToastError: ImageVector get() = ChatDemoIcons.IconToastError16
    val PhotoError: ImageVector get() = ChatDemoIcons.IconPhotoErrorFill20
    val Data: ImageVector get() = ChatDemoIcons.IconDataOutline16
    val PanelLeft: ImageVector get() = ChatDemoIcons.IconPanelLeftOutline16
    val ThemeDark: ImageVector get() = ChatDemoIcons.IconDarkOutline16
    val ThemeLight: ImageVector get() = ChatDemoIcons.IconLightOutline16
    val ThemeSystem: ImageVector get() = ChatDemoIcons.IconFollowsystemOutline16

    // —— chat-demo 里没有，保留手绘 ——

    /** 分享（chat-demo 无对应素材） */
    val Share: ImageVector by lazy {
        strokeIcon("tc_share", "M12,4 V15 M8,8 L12,4 L16,8 M5,14 V20 H19 V14")
    }

    /** 回到底部用的下箭头（chat-demo 用 ChevronDown 套圆圈，这里保留更直白的箭头） */
    val ArrowDown: ImageVector by lazy { strokeIcon("tc_arrow_down", "M12,5 V18 M7,13 L12,18 L17,13") }

    /** 返回（chat-demo 把 ChevronRight 旋转 180°，这里直接画左箭头） */
    val Back: ImageVector by lazy { strokeIcon("tc_back", "M15,4 L7,12 L15,20") }

    val ChevronUp: ImageVector by lazy { strokeIcon("tc_chevron_up", "M7,14 L12,9 L17,14") }

    /** 多选（chat-demo 无素材）：方框 + 勾 */
    val MultiSelect: ImageVector by lazy {
        strokeIcon("tc_multi_select", "M4,6 H20 V20 H4 Z M8,13 L11,16 L16,10")
    }

    /** 置顶（chat-demo 无素材）：顶部横线 + 向上箭头 */
    val Pin: ImageVector by lazy {
        strokeIcon("tc_pin", "M5,4 H19 M12,20 V8 M7,13 L12,8 L17,13")
    }

    private const val SW = 1.8f

    private fun strokeIcon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = SW,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
}
