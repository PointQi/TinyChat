package io.tinychat.markdown

/**
 * 代码着色的语言集合（M2-4）。
 *
 * 只覆盖模型真正常用的十二种。多一种语言就多一张关键字表，表写偏了会把普通标识符
 * 染成关键字——那比不染色更难看，所以这里不追求数量，也不认「二十种以上」这种指标。
 * 不在枚举里的语言落到 [PLAIN]，按纯文本渲染，行为和引入着色前完全一致。
 */
internal enum class SyntaxLanguage {
    KOTLIN,
    JAVA,
    JAVASCRIPT,
    TYPESCRIPT,
    C,
    CPP,
    JSON,
    SQL,
    SHELL,
    PYTHON,
    MARKDOWN,
    MERMAID,
    PLAIN,
    ;

    companion object {
        /**
         * 围栏语言标记 -> 语言。
         *
         * 标记后面常跟附加信息（```` ```kotlin title="示例" ````），所以只取第一个词；
         * 别名按「模型实际会写的」收录，猜不准的一律落到 [PLAIN]，不做兜底联想。
         */
        fun fromFence(fence: String?): SyntaxLanguage {
            val name = fence
                ?.trim()
                ?.split(' ', '\t')
                ?.firstOrNull()
                ?.lowercase()
                .orEmpty()
            return when (name) {
                "kotlin", "kt", "kts", "gradle" -> KOTLIN
                "java" -> JAVA
                "javascript", "js", "jsx", "node" -> JAVASCRIPT
                "typescript", "ts", "tsx" -> TYPESCRIPT
                "c" -> C
                "cpp", "c++", "cc", "cxx", "h", "hpp" -> CPP
                "json", "jsonc", "json5" -> JSON
                "sql", "mysql", "postgres", "postgresql", "psql", "sqlite" -> SQL
                "sh", "bash", "shell", "zsh", "console", "shell-session" -> SHELL
                "python", "py", "python3" -> PYTHON
                "md", "markdown" -> MARKDOWN
                "mermaid", "mmd" -> MERMAID
                else -> PLAIN
            }
        }
    }
}
