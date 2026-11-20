package io.tinychat.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.tinychat.designsystem.TcIcons
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcTypography
import io.tinychat.ui.TcIconButton
import io.tinychat.ui.TcListRow
import io.tinychat.ui.TcSectionLabel
import io.tinychat.ui.TcTopBar

/** 项目仓库地址（M9 前占位，仓库公开后即为正式地址）。 */
const val REPOSITORY_URL = "https://github.com/Takeoff0518/TinyChat"

/**
 * 关于（docs/02 §12.5）。
 *
 * 服务协议与帮助入口本轮不提供（用户决定），只保留版本、开源项目与开源许可。
 */
@Composable
fun AboutScreen(
    versionName: String,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val palette = TcTheme.palette
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg),
    ) {
        TcTopBar(
            title = "关于",
            leading = { TcIconButton(TcIcons.Back, "返回", onBack) },
        )
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                Text("TinyChat", style = TcTypography.headlineSmall, color = palette.labelPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (versionName.isBlank()) "版本未知" else "版本 $versionName",
                    style = TcTypography.labelMedium,
                    color = palette.labelTertiary,
                )
            }
            TcListRow(
                title = "开源项目",
                subtitle = REPOSITORY_URL,
                onClick = { openUrl(context, REPOSITORY_URL) },
                trailing = {
                    Icon(
                        imageVector = TcIcons.ExternalLink,
                        contentDescription = null,
                        tint = palette.labelTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            TcListRow(
                title = "开源许可",
                onClick = onOpenLicenses,
                trailing = {
                    Icon(
                        imageVector = TcIcons.ChevronRight,
                        contentDescription = null,
                        tint = palette.labelTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "使用你自己的 API Key 访问模型服务。本项目与深度求索公司无隶属关系，界面与协议实现参考 deepseek-ai/chat-demo（MIT）。",
                style = TcTypography.bodySmall,
                color = palette.labelTertiary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 开源许可清单（按许可证分组）。 */
@Composable
fun LicenseScreen(onBack: () -> Unit) {
    val palette = TcTheme.palette
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg),
    ) {
        TcTopBar(
            title = "开源许可",
            leading = { TcIconButton(TcIcons.Back, "返回", onBack) },
        )
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            TcSectionLabel("Apache License 2.0")
            ApacheComponents.forEach { LicenseRow(it) }

            TcSectionLabel("MIT License")
            MitComponents.forEach { (name, note) -> LicenseRow(name, note) }

            TcSectionLabel("仅构建与测试使用")
            TestComponents.forEach { (name, license) -> LicenseRow(name, license) }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "以上组件按各自许可证分发，版权归各项目作者所有。",
                style = TcTypography.bodySmall,
                color = palette.labelTertiary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val ApacheComponents = listOf(
    "Kotlin 标准库",
    "kotlinx.coroutines",
    "kotlinx.serialization",
    "AndroidX（core-ktx、activity、lifecycle、navigation、Room、DataStore、core-splashscreen）",
    "Jetpack Compose（UI、Foundation、Material3）",
    "OkHttp",
    "Coil",
    "multiplatform-markdown-renderer",
)

private val MitComponents = listOf(
    "KaTeX（含随包分发的字体）" to "数学公式渲染",
    "Mermaid" to "流程图渲染",
    "deepseek-ai/chat-demo" to "图标与界面参考",
)

private val TestComponents = listOf(
    "Robolectric" to "MIT License",
    "JUnit 4" to "Eclipse Public License 1.0",
)

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    val opened = runCatching { context.startActivity(intent) }.isSuccess
    if (!opened) {
        Toast.makeText(context, "没有可用的浏览器", Toast.LENGTH_SHORT).show()
    }
}

/** 许可证条目：标题允许换行，避免长组件名被省略号截断。 */
@Composable
private fun LicenseRow(name: String, note: String? = null) {
    val palette = TcTheme.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(name, style = TcTypography.bodyLarge, color = palette.labelPrimary)
        if (note != null) {
            Spacer(Modifier.height(2.dp))
            Text(note, style = TcTypography.labelMedium, color = palette.labelTertiary)
        }
    }
}
