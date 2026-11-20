package io.tinychat.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.tinychat.designsystem.TcTheme
import io.tinychat.designsystem.TcThemeMode
import io.tinychat.markdown.TcMarkdown

/**
 * 代码着色验收台（**只在 debug 构建里存在**，release 不会打包）。
 *
 * ```
 * adb shell am start -n io.tinychat/io.tinychat.debug.CodeLabActivity
 * adb shell am start -n io.tinychat/io.tinychat.debug.CodeLabActivity --ez dark true
 * ```
 *
 * 十二种语言各一段，走的是聊天页同一条渲染链路，所以看到的就是消息里会看到的样子。
 * 加这个台子的理由：着色错了只有眼睛能发现，而单测只能证明「片段位置对不对」。
 */
class CodeLabActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = intent.getBooleanExtra("dark", false)
        setContent {
            TcTheme(mode = if (dark) TcThemeMode.DARK else TcThemeMode.LIGHT) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TcTheme.palette.bg)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "代码着色验收（十二种语言）",
                        color = TcTheme.palette.labelSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    TcMarkdown(content = SAMPLE)
                }
            }
        }
    }
}

private val SAMPLE = """
```kotlin
data class User(val id: Long, val name: String)

fun greet(user: User): String {
    // 字符串里的 // 不是注释
    return "你好，${'$'}{user.name}"
}
```

```java
public final class Counter {
    private int value = 0;
    public int next() { return ++value; }
}
```

```python
@app.get("/items/{item_id}")
def read(item_id: int, q: str | None = None) -> dict:
    '''返回一条记录。'''
    return {"id": item_id, "q": q}
```

```javascript
const ids = [1, 2, 3];
const doubled = ids.map((n) => n * 2); // 箭头函数
console.log(`共 ${'$'}{doubled.length} 条`);
```

```typescript
interface User { readonly id: number; name: string }
type Result<T> = { ok: true; data: T } | { ok: false; error: string };
```

```json
{
  "model": "deepseek-v4.1-flash",
  "temperature": 0.7,
  "stream": true,
  "stop": null
}
```

```sql
SELECT u.id, count(o.id) AS orders
FROM users u
LEFT JOIN orders o ON o.user_id = u.id
WHERE u.created_at >= '2026-01-01' -- 只统计今年
GROUP BY u.id
ORDER BY orders DESC;
```

```bash
for f in *.log; do
    echo "处理 ${'$'}{f}"
    gzip -c "${'$'}f" > "${'$'}f.gz"
done
```

```c
#include <stdio.h>

int main(void) {
    int n = 42;
    printf("%d\n", n); /* 输出 */
    return 0;
}
```

```cpp
template <typename T>
class Box {
public:
    explicit Box(T v) : value_(v) {}
    T get() const { return value_; }
private:
    T value_;
};
```

```markdown
# 标题
- 列表项 `code`
> 引用
```

```mermaid
flowchart LR
  A[开始] --> B{判断}
  B -->|是| C[结束]
  %% 这条注释不会被渲染
```
""".trimIndent()
