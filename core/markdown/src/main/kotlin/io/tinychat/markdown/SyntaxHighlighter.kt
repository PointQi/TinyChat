package io.tinychat.markdown

/** 一个着色片段：半开区间 `[start, end)`，全部片段不重叠、按 start 升序。 */
internal data class CodeToken(val start: Int, val end: Int, val kind: TokenKind)

/** 片段种类，和 [io.tinychat.designsystem.TcSyntaxColors] 的槽位一一对应。 */
internal enum class TokenKind {
    KEYWORD,
    STRING,
    NUMBER,
    COMMENT,
    PUNCTUATION,
    ANNOTATION,
    PROPERTY,
}

/** 超过这个长度就不着色：粘进来的巨型文件不值得为观感付重排代价 */
internal const val MaxHighlightLength = 20_000

/**
 * 把代码切成着色片段（M2-4）。
 *
 * 单趟线性扫描，字符串与注释先于其它一切：落在它们内部的数字、标点、关键字
 * 一律不再标记，因此片段天然不重叠。这是刻意的保守选择——宁可少染，
 * 也不要出现「字符串里的 42 被染成数字色」这种一眼假的错误。
 *
 * 未识别的语言、空串、超长文本返回空列表，调用方按纯文本渲染。
 */
internal fun highlight(code: String, language: SyntaxLanguage): List<CodeToken> {
    if (code.isEmpty() || code.length > MaxHighlightLength) return emptyList()
    return when (language) {
        SyntaxLanguage.PLAIN -> emptyList()
        SyntaxLanguage.MARKDOWN -> scanMarkdown(code)
        SyntaxLanguage.MERMAID -> scanMermaid(code)
        else -> scanCode(code, specFor(language))
    }
}

// ---------------------------------------------------------------- 通用词法扫描

/**
 * 一门代码语言的词法配置。C 族十几个语言只在关键字表上不同，
 * 所以规则收在一张表里，语言之间靠数据区分而不是靠代码分支。
 */
private class LanguageSpec(
    val keywords: Set<String>,
    val ignoreKeywordCase: Boolean = false,
    val lineComments: List<String> = listOf("//"),
    val blockComment: Pair<String, String>? = "/*" to "*/",
    val strings: List<String> = listOf("\"", "'"),
    /** 反斜杠转义 */
    val escapes: Boolean = true,
    /** 用「两个引号」表示一个引号（SQL 的 `'it''s'`） */
    val doubledQuote: Boolean = false,
    /** 字符串可以跨行（Python 三引号之外的普通字符串不行） */
    val multilineStrings: Boolean = false,
    val tripleQuotes: Boolean = false,
    /** `@Override` / `@app.route` 这类注解 */
    val annotation: Boolean = false,
    /** `$HOME` / `${HOME}` 这类变量 */
    val variableSigil: Boolean = false,
    /** JSON：字符串后面跟冒号就是这个对象的键，键与值分开染色 */
    val isJson: Boolean = false,
    /** `#` 只有落在词首才算注释（Shell 里 `a#b` 是普通字符） */
    val hashNeedsBoundary: Boolean = false,
    /** C 系的预处理指令：行首 `#include` / `#define` 单独染色 */
    val preprocessor: Boolean = false,
)

private fun scanCode(code: String, spec: LanguageSpec): List<CodeToken> {
    val out = ArrayList<CodeToken>()
    val n = code.length
    var i = 0

    while (i < n) {
        val c = code[i]

        // 块注释先判：/* 和 // 都以斜杠开头，先试长的那个不会错
        val block = spec.blockComment
        if (block != null && code.startsWith(block.first, i)) {
            val close = code.indexOf(block.second, i + block.first.length)
            val end = if (close < 0) n else close + block.second.length
            out += CodeToken(i, end, TokenKind.COMMENT)
            i = end
            continue
        }

        val linePrefix = spec.lineComments.firstOrNull { code.startsWith(it, i) }
        if (linePrefix != null && (!spec.hashNeedsBoundary || i == 0 || code[i - 1].isWhitespace())) {
            var end = code.indexOf('\n', i).let { if (it < 0) n else it }
            if (end > i && code[end - 1] == '\r') end-- // 别把 \r 染进注释，否则等宽字体下会多一格底色
            out += CodeToken(i, end, TokenKind.COMMENT)
            i = end
            continue
        }

        // 三引号必须先于普通字符串，否则会先被当成一个空串
        if (spec.tripleQuotes) {
            val triple = when {
                code.startsWith("\"\"\"", i) -> "\"\"\""
                code.startsWith("'''", i) -> "'''"
                else -> null
            }
            if (triple != null) {
                val close = code.indexOf(triple, i + 3)
                val end = if (close < 0) n else close + 3
                out += CodeToken(i, end, TokenKind.STRING)
                i = end
                continue
            }
        }

        val quote = spec.strings.firstOrNull { code.startsWith(it, i) }
        if (quote != null) {
            var j = i + quote.length
            while (j < n) {
                if (code[j] == '\n' && !spec.multilineStrings) break
                if (spec.escapes && code[j] == '\\') {
                    j += 2
                    continue
                }
                if (spec.doubledQuote && code.startsWith(quote, j) && code.startsWith(quote, j + 1)) {
                    j += 2
                    continue
                }
                if (code.startsWith(quote, j)) {
                    j += quote.length
                    break
                }
                j++
            }
            val end = minOf(j, n)
            val kind = if (spec.isJson && followsColon(code, end)) TokenKind.PROPERTY else TokenKind.STRING
            out += CodeToken(i, end, kind)
            i = end
            continue
        }

        if (spec.annotation && c == '@' && i + 1 < n && isIdentifierStart(code[i + 1])) {
            var j = i + 1
            while (j < n && (isIdentifierPart(code[j]) || code[j] == '.')) j++
            out += CodeToken(i, j, TokenKind.ANNOTATION)
            i = j
            continue
        }

        if (spec.variableSigil && c == '$') {
            if (i + 1 < n && code[i + 1] == '{') {
                val close = code.indexOf('}', i + 2)
                val end = if (close < 0) i + 1 else close + 1
                out += CodeToken(i, end, TokenKind.PROPERTY)
                i = end
                continue
            }
            if (i + 1 < n && isIdentifierStart(code[i + 1])) {
                var j = i + 1
                while (j < n && isIdentifierPart(code[j])) j++
                out += CodeToken(i, j, TokenKind.PROPERTY)
                i = j
                continue
            }
        }

        if (spec.preprocessor && c == '#' && atLineStart(code, i)) {
            var j = i + 1
            while (j < n && isIdentifierPart(code[j])) j++
            val end = if (j > i + 1) j else i + 1
            out += CodeToken(i, end, TokenKind.ANNOTATION)
            i = end
            continue
        }

        if (isIdentifierStart(c)) {
            var j = i + 1
            while (j < n && isIdentifierPart(code[j])) j++
            if (isKeyword(code, i, j, spec)) out += CodeToken(i, j, TokenKind.KEYWORD)
            i = j
            continue
        }

        // 走到这里说明当前位置不是标识符的首字符，所以标识符里的数字永远不会进这条分支，
        // `sha256`、`a1` 不会被拆成「标识符 + 数字」
        if (c.isDigit()) {
            var j = i + 1
            while (j < n && (code[j].isLetterOrDigit() || code[j] == '_' || code[j] == '.')) j++
            out += CodeToken(i, j, TokenKind.NUMBER)
            i = j
            continue
        }

        if (c in PunctuationChars) {
            out += CodeToken(i, i + 1, TokenKind.PUNCTUATION)
            i++
            continue
        }

        i++
    }
    return out
}

private fun isKeyword(code: String, start: Int, end: Int, spec: LanguageSpec): Boolean {
    if (spec.keywords.isEmpty()) return false
    val word = code.substring(start, end)
    return if (spec.ignoreKeywordCase) spec.keywords.contains(word.lowercase()) else spec.keywords.contains(word)
}

private fun isIdentifierStart(c: Char) = c.isLetter() || c == '_'

private fun isIdentifierPart(c: Char) = c.isLetterOrDigit() || c == '_'

/** 字符串结束后跳过空白看下一个字符是不是冒号（JSON 的键） */
private fun followsColon(code: String, from: Int): Boolean {
    var i = from
    while (i < code.length && (code[i] == ' ' || code[i] == '\t')) i++
    return i < code.length && code[i] == ':'
}

/** 这一行是不是只有缩进：往回只跳过空格和制表符，遇到换行就算行首 */
private fun atLineStart(code: String, index: Int): Boolean {
    var i = index - 1
    while (i >= 0 && (code[i] == ' ' || code[i] == '\t')) i--
    return i < 0 || code[i] == '\n'
}

private val PunctuationChars = "{}()[];,.:=+-*/%<>!&|?~^".toSet()

// ---------------------------------------------------------------- Markdown

/**
 * Markdown 用的是行规则而不是词法规则：标题、列表、引用、围栏按行识别，
 * 行内再挑出代码段、强调标记和链接。
 *
 * 围栏内部不再着色。一段 Markdown 里常常又嵌着代码围栏，递归下去收益很小、
 * 出错面却很大，所以围栏行只染围栏本身。
 */
private fun scanMarkdown(code: String): List<CodeToken> {
    val out = ArrayList<CodeToken>()
    var lineStart = 0
    while (lineStart <= code.length) {
        val newline = code.indexOf('\n', lineStart)
        val lineEnd = if (newline < 0) code.length else newline
        val line = code.substring(lineStart, lineEnd)
        val trimmed = line.trimStart()
        val indent = line.length - trimmed.length
        val contentStart = lineStart + indent

        when {
            trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                out += CodeToken(contentStart, lineEnd, TokenKind.PUNCTUATION)
            }

            trimmed.startsWith("#") -> {
                val hashes = trimmed.takeWhile { it == '#' }.length
                val after = trimmed.getOrNull(hashes)
                if (hashes <= 6 && (after == null || after == ' ' || after == '\t')) {
                    out += CodeToken(contentStart, lineEnd, TokenKind.KEYWORD)
                }
            }

            trimmed.startsWith(">") -> {
                out += CodeToken(contentStart, contentStart + 1, TokenKind.PUNCTUATION)
                scanMarkdownInline(line, lineStart, contentStart + 1, out)
            }

            else -> {
                markListMarker(trimmed, contentStart, out)
                scanMarkdownInline(line, lineStart, contentStart, out)
            }
        }

        lineStart = lineEnd + 1
    }
    return out
}

private fun markListMarker(trimmed: String, offset: Int, out: MutableList<CodeToken>) {
    if (trimmed.length >= 2 && trimmed[0] in listOf('-', '*', '+') && trimmed[1] == ' ') {
        out += CodeToken(offset, offset + 1, TokenKind.PUNCTUATION)
        return
    }
    val digits = trimmed.takeWhile { it.isDigit() }.length
    if (digits in 1..3 && trimmed.getOrNull(digits + 1) == ' ') {
        val marker = trimmed.getOrNull(digits)
        if (marker != null && (marker == '.' || marker == ')')) {
            out += CodeToken(offset, offset + digits + 1, TokenKind.PUNCTUATION)
        }
    }
}

private fun scanMarkdownInline(line: String, lineStart: Int, from: Int, out: MutableList<CodeToken>) {
    var i = from - lineStart
    while (i < line.length) {
        val c = line[i]
        when {
            c == '`' -> {
                val run = runLength(line, i, '`')
                val fence = "`".repeat(run)
                val close = line.indexOf(fence, i + run)
                val end = if (close < 0) line.length else close + run
                out += CodeToken(lineStart + i, lineStart + end, TokenKind.STRING)
                i = end
            }

            // 只认星号做强调。下划线不认：snake_case 会被误染色，代价比收益大
            c == '*' && line.getOrNull(i + 1) == '*' -> {
                out += CodeToken(lineStart + i, lineStart + i + 2, TokenKind.PUNCTUATION)
                i += 2
            }

            c == '*' -> {
                out += CodeToken(lineStart + i, lineStart + i + 1, TokenKind.PUNCTUATION)
                i++
            }

            c == ']' && line.getOrNull(i + 1) == '(' -> {
                val close = line.indexOf(')', i + 2)
                val end = if (close < 0) line.length else close
                out += CodeToken(lineStart + i, lineStart + i + 2, TokenKind.PUNCTUATION)
                if (end > i + 2) out += CodeToken(lineStart + i + 2, lineStart + end, TokenKind.PROPERTY)
                if (close >= 0) out += CodeToken(lineStart + close, lineStart + close + 1, TokenKind.PUNCTUATION)
                i = if (close < 0) line.length else close + 1
            }

            else -> i++
        }
    }
}

private fun runLength(text: String, start: Int, ch: Char): Int {
    var i = start
    while (i < text.length && text[i] == ch) i++
    return i - start
}

// ---------------------------------------------------------------- Mermaid

/**
 * Mermaid 源码也做近似着色：箭头、`%%` 注释、引号字符串、常见图类型声明与
 * 结构关键字。图类型有十几种、各自语法不同，这里只认跨图类型通用的部分，
 * 认不准的保持原样——少染总比染错好。
 */
private fun scanMermaid(code: String): List<CodeToken> {
    val out = ArrayList<CodeToken>()
    val n = code.length
    var i = 0
    while (i < n) {
        val c = code[i]
        when {
            code.startsWith("%%", i) -> {
                var end = code.indexOf('\n', i).let { if (it < 0) n else it }
                if (end > i && code[end - 1] == '\r') end--
                out += CodeToken(i, end, TokenKind.COMMENT)
                i = end
            }

            c == '"' -> {
                var j = i + 1
                while (j < n && code[j] != '"' && code[j] != '\n') {
                    if (code[j] == '\\') j++
                    j++
                }
                val end = if (j < n && code[j] == '"') j + 1 else minOf(j, n)
                out += CodeToken(i, end, TokenKind.STRING)
                i = end
            }

            else -> {
                val arrow = MermaidArrows.firstOrNull { code.startsWith(it, i) }
                if (arrow != null) {
                    out += CodeToken(i, i + arrow.length, TokenKind.PUNCTUATION)
                    i += arrow.length
                    continue
                }
                if (isIdentifierStart(c)) {
                    var j = i + 1
                    while (j < n && isIdentifierPart(code[j])) j++
                    val word = code.substring(i, j)
                    if (MermaidKeywords.contains(word.lowercase())) {
                        out += CodeToken(i, j, TokenKind.KEYWORD)
                    }
                    i = j
                    continue
                }
                i++
            }
        }
    }
    return out
}

/** 长的写在前面，否则 `-->` 会被 `->` 抢先匹配 */
private val MermaidArrows = listOf(
    "<-->", "-->>", "-.->", "==>", "-->", "---", "-.-", "===", "--x", "--o", "<->", "->>", "->",
)

private val MermaidKeywords = setOf(
    "graph", "flowchart", "sequencediagram", "classdiagram", "statediagram", "statediagram-v2",
    "erdiagram", "journey", "gantt", "pie", "quadrantchart", "mindmap", "timeline", "gitgraph",
    "requirementdiagram", "subgraph", "end", "direction", "participant", "actor", "note", "loop",
    "alt", "else", "opt", "par", "critical", "rect", "classdef", "class", "style", "linkstyle",
    "click", "over", "activate", "deactivate", "title", "acctitle", "accdescr", "and",
    "tb", "td", "bt", "lr", "rl",
)

// ---------------------------------------------------------------- 各语言的规则表

private fun specFor(language: SyntaxLanguage): LanguageSpec = when (language) {
    SyntaxLanguage.KOTLIN -> LanguageSpec(
        keywords = KotlinKeywords,
        annotation = true,
        // Kotlin 的原始字符串 """ 可以跨行，必须按三引号处理，否则会被读成一串空串
        tripleQuotes = true,
        multilineStrings = true,
    )

    SyntaxLanguage.JAVA -> LanguageSpec(
        keywords = JavaKeywords,
        annotation = true,
        tripleQuotes = true, // Java 15+ 的文本块
        multilineStrings = true,
    )

    SyntaxLanguage.JAVASCRIPT -> LanguageSpec(
        keywords = JavaScriptKeywords,
        strings = listOf("\"", "'", "`"), // 模板字符串
        multilineStrings = true,
    )

    SyntaxLanguage.TYPESCRIPT -> LanguageSpec(
        keywords = JavaScriptKeywords + TypeScriptExtra,
        strings = listOf("\"", "'", "`"),
        multilineStrings = true,
    )

    SyntaxLanguage.C -> LanguageSpec(
        keywords = CKeywords,
        preprocessor = true,
    )

    SyntaxLanguage.CPP -> LanguageSpec(
        keywords = CKeywords + CppExtra,
        preprocessor = true,
    )

    SyntaxLanguage.JSON -> LanguageSpec(
        keywords = setOf("true", "false", "null"),
        strings = listOf("\"", "'"),
        isJson = true,
    )

    SyntaxLanguage.SQL -> LanguageSpec(
        keywords = SqlKeywords,
        ignoreKeywordCase = true,
        lineComments = listOf("--"),
        strings = listOf("'", "\""),
        doubledQuote = true,
    )

    SyntaxLanguage.SHELL -> LanguageSpec(
        keywords = ShellKeywords,
        lineComments = listOf("#"),
        strings = listOf("\"", "'"),
        variableSigil = true,
        hashNeedsBoundary = true,
    )

    SyntaxLanguage.PYTHON -> LanguageSpec(
        keywords = PythonKeywords,
        lineComments = listOf("#"),
        blockComment = null,
        tripleQuotes = true,
        multilineStrings = true,
        annotation = true,
    )

    // 上面两个走各自的扫描器，这里只是把 when 补全
    SyntaxLanguage.MARKDOWN, SyntaxLanguage.MERMAID, SyntaxLanguage.PLAIN ->
        LanguageSpec(keywords = emptySet())
}

private val KotlinKeywords = setOf(
    "abstract", "actual", "annotation", "as", "break", "by", "catch", "class", "companion",
    "const", "constructor", "continue", "crossinline", "data", "delegate", "do", "dynamic",
    "else", "enum", "expect", "external", "false", "field", "file", "final", "finally", "for",
    "fun", "get", "if", "import", "in", "infix", "init", "inline", "inner", "interface",
    "internal", "is", "lateinit", "noinline", "object", "open", "operator", "out", "override",
    "package", "param", "private", "property", "protected", "public", "receiver", "reified",
    "return", "sealed", "set", "setparam", "super", "suspend", "tailrec", "this", "throw",
    "true", "try", "typealias", "val", "var", "vararg", "when", "where", "while",
)

private val JavaKeywords = setOf(
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
    "continue", "default", "do", "double", "else", "enum", "extends", "false", "final", "finally",
    "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface",
    "long", "native", "new", "null", "package", "permits", "private", "protected", "public",
    "record", "return", "sealed", "short", "static", "strictfp", "super", "switch",
    "synchronized", "this", "throw", "throws", "transient", "true", "try", "var", "void",
    "volatile", "while",
)

private val JavaScriptKeywords = setOf(
    "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger",
    "default", "delete", "do", "else", "export", "extends", "false", "finally", "for",
    "function", "if", "import", "in", "instanceof", "let", "new", "null", "of", "return",
    "static", "super", "switch", "this", "throw", "true", "try", "typeof", "undefined", "var",
    "void", "while", "with", "yield",
)

private val TypeScriptExtra = setOf(
    "abstract", "any", "as", "asserts", "boolean", "constructor", "declare", "enum", "implements",
    "infer", "interface", "is", "keyof", "namespace", "never", "number", "object", "override",
    "private", "protected", "public", "readonly", "satisfies", "string", "symbol", "type",
    "unique", "unknown",
)

private val CKeywords = setOf(
    "auto", "bool", "break", "case", "char", "const", "continue", "default", "do", "double",
    "else", "enum", "extern", "false", "float", "for", "goto", "if", "inline", "int", "long",
    "NULL", "register", "restrict", "return", "short", "signed", "sizeof", "static", "struct",
    "switch", "true", "typedef", "union", "unsigned", "void", "volatile", "while",
)

private val CppExtra = setOf(
    "alignas", "alignof", "and", "asm", "catch", "class", "constexpr", "const_cast", "decltype",
    "delete", "dynamic_cast", "explicit", "export", "final", "friend", "mutable", "namespace",
    "new", "noexcept", "nullptr", "operator", "override", "private", "protected", "public",
    "reinterpret_cast", "static_assert", "static_cast", "template", "this", "throw", "try",
    "typeid", "typename", "using", "virtual", "wchar_t",
)

private val SqlKeywords = setOf(
    "add", "all", "alter", "and", "any", "array", "as", "asc", "avg", "begin", "between",
    "bigint", "boolean", "by", "cascade", "case", "cast", "char", "check", "coalesce", "commit",
    "constraint", "count", "create", "cross", "current_date", "current_timestamp", "date",
    "decimal", "default", "delete", "desc", "distinct", "drop", "else", "end", "except",
    "exists", "false", "foreign", "from", "full", "group", "having", "if", "ilike", "in",
    "index", "inner", "insert", "int", "integer", "intersect", "into", "is", "join", "json",
    "jsonb", "key", "left", "like", "limit", "max", "min", "not", "null", "nulls", "numeric",
    "offset", "on", "or", "order", "outer", "over", "partition", "primary", "references",
    "returning", "right", "rollback", "select", "serial", "set", "smallint", "sum", "table",
    "text", "then", "timestamp", "transaction", "true", "truncate", "union", "unique", "update",
    "using", "uuid", "values", "varchar", "view", "when", "where", "window", "with",
)

private val ShellKeywords = setOf(
    "alias", "awk", "break", "brew", "case", "cat", "cd", "chmod", "command", "continue", "cp",
    "curl", "declare", "docker", "do", "done", "echo", "elif", "else", "env", "esac", "eval",
    "exec", "exit", "export", "false", "fi", "find", "for", "function", "git", "grep", "head",
    "if", "in", "kill", "local", "ls", "make", "mkdir", "mv", "npm", "pip", "printf", "ps",
    "pwd", "read", "readonly", "return", "rm", "rsync", "scp", "sed", "set", "shift", "sleep",
    "sort", "source", "ssh", "sudo", "tail", "tar", "test", "then", "time", "trap", "true",
    "unset", "until", "wc", "wget", "while", "xargs",
)

private val PythonKeywords = setOf(
    "False", "None", "True", "and", "as", "assert", "async", "await", "break", "case", "class",
    "cls", "continue", "def", "del", "elif", "else", "except", "finally", "for", "from",
    "global", "if", "import", "in", "is", "lambda", "match", "nonlocal", "not", "or", "pass",
    "raise", "return", "self", "try", "while", "with", "yield",
)
