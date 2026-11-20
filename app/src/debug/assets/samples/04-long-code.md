下面是一段六十行左右的 Kotlin，用来同时检查折叠阈值、横向滚动、着色与等宽字形。

```kotlin
package io.tinychat.core.markdown

internal enum class SyntaxLanguage {
    KOTLIN, JAVA, JAVASCRIPT, TYPESCRIPT,
    C, CPP, JSON, SQL, SHELL, PYTHON,
    MARKDOWN, MERMAID, PLAIN;

    companion object {
        fun fromFence(fence: String?): SyntaxLanguage {
            val token = fence?.trim()?.substringBefore(' ')?.lowercase().orEmpty()
            return when (token) {
                "kt", "kts", "gradle" -> KOTLIN
                "js", "jsx", "node" -> JAVASCRIPT
                "ts", "tsx" -> TYPESCRIPT
                "json", "jsonc", "json5" -> JSON
                "sql", "mysql", "postgres", "psql", "sqlite" -> SQL
                "sh", "bash", "shell", "zsh", "console" -> SHELL
                "py", "python", "python3" -> PYTHON
                "md" -> MARKDOWN
                "mmd" -> MERMAID
                "c" -> C
                "c++", "cc", "cxx", "h", "hpp", "cpp" -> CPP
                "java" -> JAVA
                else -> PLAIN
            }
        }
    }
}

internal data class CodeToken(val start: Int, val end: Int, val kind: TokenKind)

internal enum class TokenKind {
    KEYWORD, STRING, NUMBER, COMMENT, PUNCTUATION, ANNOTATION, PROPERTY
}

internal fun highlight(code: String, language: SyntaxLanguage): List<CodeToken> {
    if (code.length > MaxHighlightLength) return emptyList()
    val spec = specFor(language) ?: return emptyList()
    val tokens = ArrayList<CodeToken>(code.length / 4)
    var i = 0
    while (i < code.length) {
        val c = code[i]
        if (c == '/' && i + 1 < code.length && code[i + 1] == '*') {
            val close = code.indexOf("*/", i + 2)
            val end = if (close < 0) code.length else close + 2
            if (spec.blockComment) tokens += CodeToken(i, end, TokenKind.COMMENT)
            i = end
            continue
        }
        if (c.isDigit() && (i == 0 || !isIdentifierPart(code[i - 1]))) {
            var j = i
            while (j < code.length && (code[j].isDigit() || code[j] == '_')) j++
            tokens += CodeToken(i, j, TokenKind.NUMBER)
            i = j
            continue
        }
        i++
    }
    return tokens
}
```

一段很长的单行代码，用来检查横向滚动是否顺畅、右端是否留了内边距：

```kotlin
val message = "这一行故意写得非常长，用来测试代码块的横向滚动是否会在最右端留出内边距，以及滚动时左侧语言名与右侧复制按钮是否保持固定不动，不会跟着一起滑走"
```

一段没有语法高亮的语言，退化为纯文本：

```text
纯文本代码块没有着色，但依然要等宽、要横向滚动、要有内边距。
```
