package io.tinychat.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcThemeMode
import io.tinychat.designsystem.TcTypography
import io.tinychat.markdown.TcMarkdown
import io.tinychat.markdown.TcRenderLab
import kotlinx.coroutines.launch

/**
 * 渲染实验室（**只在 debug 构建里存在**，release 不会打包）。
 *
 * 用 `adb shell am start -n io.tinychat/.debug.RenderLabActivity` 直接拉起，
 * 不需要点屏幕 —— 验证公式/图表、量单条渲染耗时都在这里做。
 */
class RenderLabActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = intent.getBooleanExtra("dark", false)
        setContent {
            TcTheme(mode = if (dark) TcThemeMode.DARK else TcThemeMode.LIGHT) {
                LabScreen()
            }
        }
    }
}

@Composable
private fun LabScreen() {
    val palette = TcTheme.palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nonce by remember { mutableStateOf(0) }
    var report by remember { mutableStateOf("未测") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg)
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                text = "渲染实验室",
                style = TcTypography.titleMedium,
                color = palette.labelPrimary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "重渲染",
                style = TcTypography.bodyMedium,
                color = palette.brand,
                modifier = Modifier
                    .clickable { nonce++ }
                    .padding(8.dp),
            )
            Text(
                text = "测耗时",
                style = TcTypography.bodyMedium,
                color = palette.brand,
                modifier = Modifier
                    .clickable {
                        scope.launch {
                            val first = TcRenderLab.benchMath(context)
                            val second = TcRenderLab.benchMath(context)
                            report = "冷启 " + first.joinToString(" ") + "ms\n稳态 " + second.joinToString(" ") + "ms"
                        }
                    }
                    .padding(8.dp),
            )
        }
        Text(
            text = report,
            style = TcTypography.bodySmall,
            color = palette.labelTertiary,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            key2(nonce) { TcMarkdown(content = SAMPLE, streaming = false) }
        }
    }
}

@Composable
private fun key2(nonce: Int, content: @Composable () -> Unit) {
    androidx.compose.runtime.key(nonce) { content() }
}

private val SAMPLE = """
# 渲染实验室

## 行内公式
质能方程 ${'$'}E=mc^2${'$'}，分式 ${'$'}\frac{a}{b}${'$'}，根号 ${'$'}\sqrt[3]{x+1}${'$'}，
求和 ${'$'}\sum_{k=1}^{n} k = \frac{n(n+1)}{2}${'$'}，希腊字母 ${'$'}\alpha\beta\gamma${'$'}。
价格不是公式：${'$'}5 和 ${'$'}10 应该原样显示。

## 块级公式
${'$'}${'$'}
\int_{0}^{\infty} e^{-x^2}\,dx = \frac{\sqrt{\pi}}{2}
${'$'}${'$'}

再来一个多行对齐：
${'$'}${'$'}
\begin{aligned}
(a+b)^2 &= a^2+2ab+b^2 \\
(a-b)^2 &= a^2-2ab+b^2
\end{aligned}
${'$'}${'$'}

## 围栏写法
```math
\frac{\partial f}{\partial x} = 2x
```

## Mermaid
```mermaid
flowchart TD
  A[用户提问] --> B{要不要搜索}
  B -- 要 --> C[联网搜索]
  B -- 不要 --> D[直接回答]
  C --> E[引用角标]
  D --> F[流式输出]
  E --> F
  F --> G[操作栏 复制/分享]
```

```mermaid
sequenceDiagram
  participant U as 用户
  participant A as App
  participant S as 服务端
  U->>A: 发送
  A->>S: POST /messages
  S-->>A: SSE 事件流
  A-->>U: 逐字渲染
```

```mermaid
pie title 时间分配
  "渲染" : 40
  "网络" : 35
  "解析" : 25
```

```mermaid
classDiagram
  class Session {
    +Long id
    +String title
  }
  class Message {
    +Long id
    +String status
  }
  Session "1" --> "*" Message
```

```mermaid
stateDiagram-v2
  [*] --> WIP
  WIP --> FINISHED: message_stop
  WIP --> INCOMPLETE: 用户停止
  WIP --> FAILED: 错误
  INCOMPLETE --> WIP: 继续生成
```

## 错误态
```mermaid
flowchart LR
  A -->>
```
""".trimIndent()
