package io.tinychat.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 图标占位（M0-4）。
 *
 * ⚠️ 这 12 个是**自绘占位图形**，只为把导航与按钮跑通；最终视觉在 M2/M8 统一收口。
 * 决策 15：核心 12 个里 9 个将从 chat-demo（MIT）移植，3 个取自 Material Symbols。
 * 用法：`Icon(TcIcons.Send, contentDescription = "发送")` —— 颜色由 `tint` 决定，与本文件的黑色无关。
 */
object TcIcons {

    private const val SW = 1.8f // stroke width

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

    private fun fillIcon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = SolidColor(Color.Black),
        ).build()

    // —— 核心 12 个 ——
    val Menu: ImageVector by lazy { strokeIcon("tc_menu", "M4,7 H20 M4,12 H20 M4,17 H20") }
    val NewChat: ImageVector by lazy { strokeIcon("tc_new_chat", "M12,5 V19 M5,12 H19") }
    val Send: ImageVector by lazy { fillIcon("tc_send", "M12,4 L19,11.6 H14.6 V20 H9.4 V11.6 H5 Z") }
    val Stop: ImageVector by lazy { fillIcon("tc_stop", "M7,7 H17 V17 H7 Z") }
    val Mic: ImageVector by lazy {
        strokeIcon(
            "tc_mic",
            "M12,4 a2.5,2.5 0 0 1 2.5,2.5 v5 a2.5,2.5 0 0 1 -5,0 v-5 A2.5,2.5 0 0 1 12,4 Z " +
                "M7,11 a5,5 0 0 0 10,0 M12,16 V20",
        )
    }
    val Attach: ImageVector by lazy {
        strokeIcon(
            "tc_attach",
            "M15.5,7 L9,13.5 a2.5,2.5 0 0 0 3.5,3.5 L19,10.5 a4,4 0 0 0 -5.6,-5.6 " +
                "L6.4,11.9 a5.5,5.5 0 0 0 7.8,7.8 L18.5,15.4",
        )
    }
    val Copy: ImageVector by lazy { strokeIcon("tc_copy", "M8.5,8.5 H18 V20 H8.5 Z M5.5,15.5 V4 H15") }
    val Speak: ImageVector by lazy {
        strokeIcon("tc_speak", "M4,10 H7 L11,7 V17 L7,14 H4 Z M15,9 a4,4 0 0 1 0,6")
    }
    val Share: ImageVector by lazy {
        strokeIcon("tc_share", "M12,4 V15 M8,8 L12,4 L16,8 M5,14 V20 H19 V14")
    }
    val Regenerate: ImageVector by lazy {
        strokeIcon("tc_regenerate", "M19.5,12 a7.5,7.5 0 1 1 -2.2,-5.3 M19.5,3.5 V8 H15")
    }
    val ArrowDown: ImageVector by lazy { strokeIcon("tc_arrow_down", "M12,5 V18 M7,13 L12,18 L17,13") }
    val Back: ImageVector by lazy { strokeIcon("tc_back", "M15,4 L7,12 L15,20") }
    val Settings: ImageVector by lazy {
        strokeIcon(
            "tc_settings",
            "M12,9 a3,3 0 1 0 0,6 a3,3 0 1 0 0,-6 Z " +
                "M12,3 V6 M12,18 V21 M3,12 H6 M18,12 H21 " +
                "M5.6,5.6 L7.8,7.8 M16.2,16.2 L18.4,18.4 M18.4,5.6 L16.2,7.8 M7.8,16.2 L5.6,18.4",
        )
    }

    // —— M1 补：底栏与思维链要用的几个 ——

    /**
     * 思维链 / 深度思考：**原子图标**，移植自 chat-demo（MIT），见 [AtomIcon]。
     * 之前手画的双圈占位已废弃。
     */
    val Think: ImageVector get() = AtomIcon

    /** 智能搜索：地球 */
    val Globe: ImageVector by lazy {
        strokeIcon(
            "tc_globe",
            "M12,4 a8,8 0 1 0 0,16 a8,8 0 1 0 0,-16 Z M4,12 H20 " +
                "M12,4 c3.2,2.6 3.2,13.4 0,16 M12,4 c-3.2,2.6 -3.2,13.4 0,16",
        )
    }

    /** 新建对话 / 附件：圆圈加号 */
    val PlusCircle: ImageVector by lazy {
        strokeIcon("tc_plus_circle", "M12,4 a8,8 0 1 0 0,16 a8,8 0 1 0 0,-16 Z M12,8.5 V15.5 M8.5,12 H15.5")
    }

    val ChevronDown: ImageVector by lazy { strokeIcon("tc_chevron_down", "M7,10 L12,15 L17,10") }

    val ChevronUp: ImageVector by lazy { strokeIcon("tc_chevron_up", "M7,14 L12,9 L17,14") }

    val ChevronRight: ImageVector by lazy { strokeIcon("tc_chevron_right", "M10,7 L15,12 L10,17") }

    /** 搜索步骤卡片用 */
    val Search: ImageVector by lazy {
        strokeIcon("tc_search", "M11,4 a7,7 0 1 0 0,14 a7,7 0 1 0 0,-14 Z M16,16 L20.5,20.5")
    }
}
