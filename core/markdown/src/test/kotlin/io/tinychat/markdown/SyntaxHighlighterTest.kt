package io.tinychat.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 着色器的边界行为（M2-4）。
 *
 * 重点不是「颜色对不对」——那要靠眼睛看截图——而是「会不会染错位置」：
 * 字符串里的数字、注释里的引号、标识符里的数字，这几类一旦出错，
 * 满屏颜色都会跟着离谱，而且很难从截图里定位。
 */
class SyntaxHighlighterTest {

    private fun kinds(code: String, language: SyntaxLanguage): List<Pair<String, TokenKind>> =
        highlight(code, language).map { code.substring(it.start, it.end) to it.kind }

    private fun texts(code: String, language: SyntaxLanguage): Set<String> =
        kinds(code, language).map { it.first }.toSet()

    private fun assertWellFormed(code: String, language: SyntaxLanguage) {
        val tokens = highlight(code, language)
        var last = 0
        for (token in tokens) {
            assertTrue("区间必须有序且不重叠：$token", token.start >= last)
            assertTrue("区间不能为空：$token", token.end > token.start)
            assertTrue("区间不能越界：$token", token.end <= code.length)
            last = token.end
        }
    }

    @Test
    fun `Kotlin 关键字 字符串 注释各就各位`() {
        val code = """val s = "hi \"there\"" // 注释"""
        val kinds = kinds(code, SyntaxLanguage.KOTLIN)
        assertTrue(kinds.contains("val" to TokenKind.KEYWORD))
        assertTrue(kinds.contains("\"hi \\\"there\\\"\"" to TokenKind.STRING))
        assertTrue(kinds.contains("// 注释" to TokenKind.COMMENT))
        assertWellFormed(code, SyntaxLanguage.KOTLIN)
    }

    @Test
    fun `标识符里的数字不会被拆成数字`() {
        val code = "val sha256 = 42"
        val kinds = kinds(code, SyntaxLanguage.KOTLIN)
        assertTrue(kinds.contains("42" to TokenKind.NUMBER))
        assertFalse("sha256 是标识符，里面的 256 不能单独染色", texts(code, SyntaxLanguage.KOTLIN).contains("256"))
        assertFalse(texts(code, SyntaxLanguage.KOTLIN).contains("sha"))
    }

    @Test
    fun `Kotlin 原始字符串跨行仍然是一个字符串`() {
        val code = "val s = \"\"\"a\nb\"\"\""
        val kinds = kinds(code, SyntaxLanguage.KOTLIN)
        assertTrue(kinds.contains("\"\"\"a\nb\"\"\"" to TokenKind.STRING))
    }

    @Test
    fun `Python 三引号跨行是一个字符串 装饰器是注解`() {
        val code = "@app.route(\"/\")\ndef f():\n    s = \"\"\"a\nb\"\"\""
        val kinds = kinds(code, SyntaxLanguage.PYTHON)
        assertTrue(kinds.contains("@app.route" to TokenKind.ANNOTATION))
        assertTrue(kinds.contains("def" to TokenKind.KEYWORD))
        assertTrue(kinds.contains("\"\"\"a\nb\"\"\"" to TokenKind.STRING))
    }

    @Test
    fun `Python 井号注释`() {
        val code = "x = 1  # 说明"
        val kinds = kinds(code, SyntaxLanguage.PYTHON)
        assertTrue(kinds.contains("# 说明" to TokenKind.COMMENT))
        assertTrue(kinds.contains("1" to TokenKind.NUMBER))
    }

    @Test
    fun `JSON 键与值分开染色`() {
        val code = """{"a": 1, "b": true}"""
        val kinds = kinds(code, SyntaxLanguage.JSON)
        assertTrue(kinds.contains("\"a\"" to TokenKind.PROPERTY))
        assertTrue(kinds.contains("\"b\"" to TokenKind.PROPERTY))
        assertTrue(kinds.contains("1" to TokenKind.NUMBER))
        assertTrue(kinds.contains("true" to TokenKind.KEYWORD))
        // 值里的字符串不是键
        val value = kinds("""{"k": "v"}""", SyntaxLanguage.JSON)
        assertTrue(value.contains("\"k\"" to TokenKind.PROPERTY))
        assertTrue(value.contains("\"v\"" to TokenKind.STRING))
    }

    @Test
    fun `SQL 关键字大小写不敏感 注释用双减号`() {
        val code = "SELECT id -- 说明\nfrom t"
        val kinds = kinds(code, SyntaxLanguage.SQL)
        assertTrue(kinds.contains("SELECT" to TokenKind.KEYWORD))
        assertTrue(kinds.contains("from" to TokenKind.KEYWORD))
        assertTrue(kinds.contains("-- 说明" to TokenKind.COMMENT))
    }

    @Test
    fun `SQL 连续两个单引号是一个转义`() {
        val code = "'it''s'"
        val tokens = highlight(code, SyntaxLanguage.SQL)
        assertEquals(1, tokens.size)
        assertEquals(TokenKind.STRING, tokens.single().kind)
        assertEquals(code, code.substring(tokens.single().start, tokens.single().end))
    }

    @Test
    fun `Shell 变量与词首井号注释`() {
        val code = "echo \$HOME # 注释"
        val kinds = kinds(code, SyntaxLanguage.SHELL)
        assertTrue(kinds.contains("\$HOME" to TokenKind.PROPERTY))
        assertTrue(kinds.contains("# 注释" to TokenKind.COMMENT))
        assertTrue(kinds.contains("echo" to TokenKind.KEYWORD))
    }

    @Test
    fun `Shell 词中间的井号不是注释`() {
        val code = "echo a#b"
        assertEquals(emptyList<Pair<String, TokenKind>>(), kinds(code, SyntaxLanguage.SHELL).filter { it.second == TokenKind.COMMENT })
    }

    @Test
    fun `C 字符字面量与块注释`() {
        val code = "char c = 'a'; /* ok */"
        val kinds = kinds(code, SyntaxLanguage.C)
        assertTrue(kinds.contains("'a'" to TokenKind.STRING))
        assertTrue(kinds.contains("/* ok */" to TokenKind.COMMENT))
        assertTrue(kinds.contains("char" to TokenKind.KEYWORD))
        // 注释里的内容不再单独染色
        assertFalse(texts(code, SyntaxLanguage.C).contains("ok"))
    }

    @Test
    fun `C 预处理指令单独染色 缩进后仍然算行首`() {
        val code = "#include <stdio.h>\n  #define N 3\nint x = N;"
        val kinds = kinds(code, SyntaxLanguage.C)
        assertTrue(kinds.contains("#include" to TokenKind.ANNOTATION))
        assertTrue(kinds.contains("#define" to TokenKind.ANNOTATION))
        // 词中间的井号不是指令
        assertTrue(kinds("int a = b#c;", SyntaxLanguage.C).none { it.second == TokenKind.ANNOTATION })
    }

    @Test
    fun `Markdown 标题整行 行内代码是字符串`() {
        val code = "# 标题\n用 `code` 说明"
        val kinds = kinds(code, SyntaxLanguage.MARKDOWN)
        assertTrue(kinds.contains("# 标题" to TokenKind.KEYWORD))
        assertTrue(kinds.contains("`code`" to TokenKind.STRING))
    }

    @Test
    fun `Markdown 围栏里的内容不再当成正文着色`() {
        val code = "```kotlin\nval x = 1\n```"
        val kinds = kinds(code, SyntaxLanguage.MARKDOWN)
        assertTrue(kinds.contains("```kotlin" to TokenKind.PUNCTUATION))
        assertTrue(kinds.none { it.second == TokenKind.KEYWORD })
    }

    @Test
    fun `Mermaid 箭头 关键字 注释`() {
        val code = "graph TD\n  A --> B %% 注释"
        val kinds = kinds(code, SyntaxLanguage.MERMAID)
        assertTrue(kinds.contains("graph" to TokenKind.KEYWORD))
        assertTrue(kinds.contains("TD" to TokenKind.KEYWORD))
        assertTrue(kinds.contains("-->" to TokenKind.PUNCTUATION))
        assertTrue(kinds.contains("%% 注释" to TokenKind.COMMENT))
    }

    @Test
    fun `图类型后面的箭头不会被当成标识符的一部分`() {
        val code = "A-->B"
        val kinds = kinds(code, SyntaxLanguage.MERMAID)
        assertTrue(kinds.contains("-->" to TokenKind.PUNCTUATION))
    }

    @Test
    fun `围栏语言标记的别名与附加信息`() {
        val cases = mapOf(
            "kotlin" to SyntaxLanguage.KOTLIN,
            "kt" to SyntaxLanguage.KOTLIN,
            "kts" to SyntaxLanguage.KOTLIN,
            "gradle" to SyntaxLanguage.KOTLIN,
            "js" to SyntaxLanguage.JAVASCRIPT,
            "tsx" to SyntaxLanguage.TYPESCRIPT,
            "py" to SyntaxLanguage.PYTHON,
            "c++" to SyntaxLanguage.CPP,
            "cpp" to SyntaxLanguage.CPP,
            "h" to SyntaxLanguage.CPP,
            "jsonc" to SyntaxLanguage.JSON,
            "postgres" to SyntaxLanguage.SQL,
            "bash" to SyntaxLanguage.SHELL,
            "console" to SyntaxLanguage.SHELL,
            "md" to SyntaxLanguage.MARKDOWN,
            "mmd" to SyntaxLanguage.MERMAID,
            "Kotlin" to SyntaxLanguage.KOTLIN,
            "kotlin title=\"示例\"" to SyntaxLanguage.KOTLIN,
            "rust" to SyntaxLanguage.PLAIN,
            "" to SyntaxLanguage.PLAIN,
            null to SyntaxLanguage.PLAIN,
        )
        cases.forEach { (fence, expected) ->
            assertEquals("围栏标记 $fence", expected, SyntaxLanguage.fromFence(fence))
        }
    }

    @Test
    fun `未识别语言与空内容不着色`() {
        assertEquals(emptyList<CodeToken>(), highlight("fun main() {}", SyntaxLanguage.PLAIN))
        assertEquals(emptyList<CodeToken>(), highlight("", SyntaxLanguage.KOTLIN))
    }

    @Test
    fun `超长代码直接放弃着色`() {
        val code = "a".repeat(MaxHighlightLength + 1)
        assertEquals(emptyList<CodeToken>(), highlight(code, SyntaxLanguage.KOTLIN))
        // 刚好到上限仍然着色，边界不是「一旦接近就放弃」
        val atLimit = "val x = 1".padEnd(MaxHighlightLength, ' ')
        assertEquals(TokenKind.KEYWORD, highlight(atLimit, SyntaxLanguage.KOTLIN).first().kind)
    }

    @Test
    fun `没闭合的字符串与注释不会抛异常也不会越界`() {
        listOf(
            "val s = \"未闭合" to SyntaxLanguage.KOTLIN,
            "/* 未闭合" to SyntaxLanguage.C,
            "\"\"\"未闭合" to SyntaxLanguage.PYTHON,
            "'未闭合" to SyntaxLanguage.SQL,
            "```kotlin\n未闭合" to SyntaxLanguage.MARKDOWN,
            "A --> \"未闭合" to SyntaxLanguage.MERMAID,
            "{" to SyntaxLanguage.JSON,
        ).forEach { (code, language) ->
            assertWellFormed(code, language)
        }
    }

    @Test
    fun `每个语言的样例都满足有序不重叠`() {
        val samples = mapOf(
            SyntaxLanguage.KOTLIN to "data class A(val b: Int = 1) { fun c() = \"x//y\" }",
            SyntaxLanguage.JAVA to "public class A { private final int b = 1; }",
            SyntaxLanguage.JAVASCRIPT to "const f = async (x) => { return `v=${'$'}{x}` }",
            SyntaxLanguage.TYPESCRIPT to "interface A { readonly b: string }",
            SyntaxLanguage.C to "int main(void) { return 0; }",
            SyntaxLanguage.CPP to "template <typename T> class A { T b; };",
            SyntaxLanguage.JSON to """{"a": [1, 2.5, -3], "b": null}""",
            SyntaxLanguage.SQL to "SELECT a, count(*) FROM t WHERE a LIKE 'x%' GROUP BY a",
            SyntaxLanguage.SHELL to "for f in *.txt; do echo \"\${f}\"; done",
            SyntaxLanguage.PYTHON to "def f(x: int = 1) -> str:\n    return f\"{x}\"",
            SyntaxLanguage.MARKDOWN to "# h\n- a `b` [c](https://example.com)\n> q",
            SyntaxLanguage.MERMAID to "flowchart LR\n  A[开始] -.-> B{判断}\n  B -->|\"是\"| C[结束]",
        )
        samples.forEach { (language, code) -> assertWellFormed(code, language) }
    }
}
