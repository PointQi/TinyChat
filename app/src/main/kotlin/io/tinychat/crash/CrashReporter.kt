package io.tinychat.crash

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 诊断信息的落盘（M9-2）。
 *
 * 刻意不接 `Context`：这一层要在崩溃处理里被调用，越少依赖越安全，也方便用普通 JUnit
 * 拿临时目录直接测（[CrashGuard] 负责把 `filesDir/crashes` 传进来）。
 *
 * 目录里一份报告一个文件，文件名是「时间-签名」，保留最近 [MAX_FILES] 份、超过
 * [KEEP_MILLIS] 的自动删；单份超过 [MAX_CHARS] 截断——崩溃处理里写几个 G 的日志
 * 等于把进程再拖死一次。
 */
object CrashReporter {

    const val MAX_FILES = 5
    const val MAX_CHARS = 240_000
    const val KEEP_MILLIS = 30L * 24 * 60 * 60 * 1000

    /** 写入一份报告；返回真实落盘的文件，失败返回 null（崩溃路径上不许抛）。 */
    fun write(dir: File, time: Long, signature: String, text: String): File? = runCatching {
        dir.mkdirs()
        val target = File(dir, fileName(time, signature))
        val body = if (text.length > MAX_CHARS) {
            text.take(MAX_CHARS) + "\n…（超过上限，已截断）"
        } else {
            text
        }
        target.writeText(body, Charsets.UTF_8)
        prune(dir, System.currentTimeMillis())
        target
    }.getOrNull()

    /** 由新到旧列出所有报告（文件名以时间开头，按名字排比 mtime 可靠）。 */
    fun list(dir: File): List<File> = dir.listFiles()
        ?.filter { it.isFile && it.name.endsWith(".txt") }
        ?.sortedByDescending { it.name }
        .orEmpty()

    fun delete(file: File) {
        runCatching { file.delete() }
    }

    fun clear(dir: File) {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    private fun prune(dir: File, now: Long) {
        val files = dir.listFiles()?.sortedByDescending { it.name } ?: return
        files.forEachIndexed { index, file ->
            val expired = now - file.lastModified() > KEEP_MILLIS
            if (expired || index >= MAX_FILES) file.delete()
        }
    }

    private fun fileName(time: Long, signature: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(time))
        val safe = signature
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .take(48)
            .ifBlank { "crash" }
        return "$stamp-$safe.txt"
    }

    // ------------------------------------------------------------------ 脱敏

    /** `sk-` 开头的 Key、Bearer 令牌、api_key=xxx 之类的赋值，一律换成 `***`。 */
    private val secretPatterns = listOf(
        Regex("sk-[A-Za-z0-9_\\-]{6,}"),
        Regex("(?i)bearer\\s+[A-Za-z0-9._\\-]+"),
        Regex("(?i)(api[_-]?key)\\s*[=:]\\s*[^\\s,;&\\]}]+"),
        Regex("(?i)(authorization)\\s*[=:]\\s*[^\\s,;&\\]}]+"),
    )

    /** URL 只留协议与主机，路径和查询串里可能有会话 id、临时令牌。 */
    private val urlPattern = Regex("(https?://[^/\\s?#]+)[^\\s]*")

    fun redact(text: String): String {
        var out = text
        out = secretPatterns[0].replace(out, "sk-***")
        out = secretPatterns[1].replace(out, "Bearer ***")
        out = secretPatterns[2].replace(out) { "${it.groupValues[1]}=***" }
        out = secretPatterns[3].replace(out) { "${it.groupValues[1]}=***" }
        out = urlPattern.replace(out) { "${it.groupValues[1]}/…" }
        return out
    }
}
