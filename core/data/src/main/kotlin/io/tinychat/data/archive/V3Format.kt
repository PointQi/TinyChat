package io.tinychat.data.archive

import java.time.Instant
import java.time.ZoneOffset

/**
 * v3 导出包的形状（03 §6.8）。**条目名与顺序都是协议的一部分**，不要"优化"。
 *
 * ```
 * manifest.json                                    必须是第一条
 * user.json                                        导入时跳过
 * chat_sessions/{session_id}.json                  会话（含消息树）
 * chat_session_index.json                          [{id,title,pinned,message_count,inserted_at,updated_at}]
 * chat_sessions/{session_id}_files/{file_id}_{name} 附件原件
 * ```
 */
object V3Format {

    const val MANIFEST = "manifest.json"
    const val USER = "user.json"
    const val INDEX = "chat_session_index.json"

    const val SESSION_DIR = "chat_sessions/"
    const val SESSION_SUFFIX = ".json"
    const val FILES_SUFFIX = "_files/"

    const val VERSION = 3

    fun sessionEntry(sessionId: String): String = "$SESSION_DIR$sessionId$SESSION_SUFFIX"

    fun filesPrefix(sessionId: String): String = "$SESSION_DIR$sessionId$FILES_SUFFIX"

    fun fileEntry(sessionId: String, fileId: String, fileName: String): String =
        filesPrefix(sessionId) + "${fileId}_$fileName"

    /** `chat_sessions/{id}_files/{file_id}_{name}` → (sessionId, fileId, name)；形状不对返回 null */
    fun parseFileEntry(entry: String): Triple<String, String, String>? {
        if (!entry.startsWith(SESSION_DIR)) return null
        val rest = entry.removePrefix(SESSION_DIR)
        val sep = rest.indexOf(FILES_SUFFIX)
        if (sep <= 0) return null
        val sessionId = rest.substring(0, sep)
        val fileNameWithId = rest.substring(sep + FILES_SUFFIX.length)
        if (fileNameWithId.isEmpty()) return null
        // 文件 id 是 UUID，不含下划线；名字里可能有下划线，所以只切第一个
        val underscore = fileNameWithId.indexOf('_')
        if (underscore <= 0) return null
        return Triple(
            sessionId,
            fileNameWithId.substring(0, underscore),
            fileNameWithId.substring(underscore + 1),
        )
    }
}

/** v3 的时间是 UTC ISO 字符串，带 3 位毫秒；库里存 epoch millis */
object V3Time {

    fun parse(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return runCatching { Instant.parse(iso).toEpochMilli() }
            .recoverCatching {
                // 少数导出没带毫秒，退化到秒精度
                java.time.LocalDateTime.parse(iso.removeSuffix("Z"))
                    .toInstant(ZoneOffset.UTC).toEpochMilli()
            }
            .getOrNull()
    }

    /**
     * 写回官方的时间格式。**不要**固定三位毫秒：官方导出里整点秒数写作
     * `2025-06-17T07:55:45Z`（无小数），带毫秒才写 `.059Z`。
     * `Instant.toString()` 正好是这套规则，往返才能字节级一致。
     */
    fun format(millis: Long): String = Instant.ofEpochMilli(millis).toString()
}

/** 导入/导出失败：一律抛这个，UI 直接把 message 显示给用户，不吞异常 */
class ArchiveException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 一次导入的结果（也给 UI 显示「导入了 N 个会话」） */
data class ImportReport(
    val sessions: Int,
    val messages: Int,
    val fragments: Int,
    val files: Int,
    /** 按 03 §6.8 丢掉的 UI 提示类碎片（TIP / CONTENT_RISK） */
    val droppedFragments: Int,
    val durationMs: Long,
)

/** 一次导出的结果 */
data class ExportReport(
    val sessions: Int,
    val messages: Int,
    val fragments: Int,
    val files: Int,
    val bytes: Long,
    val durationMs: Long,
)
