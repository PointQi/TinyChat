package io.tinychat.data.archive

import io.tinychat.data.db.AttachmentEntity
import io.tinychat.data.db.TinyChatDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.OutputStream

/**
 * 导出 v3 兼容包（M6-4/5）。
 *
 * 只写官方有的条目与字段：**不加提示词、不加用量、不加本地列**。
 * 消息 id 直接用库里的全局自增（同一会话内仍单调、parent 自洽，v3 只要求这个）。
 */
class V3Exporter(
    private val db: TinyChatDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun export(
        out: OutputStream,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ExportReport = withContext(Dispatchers.IO) {
        val started = clock()
        val writer = V3ArchiveWriter(out)
        writer.writeManifest()
        writer.writeUser()

        val sessions = db.sessionDao().all()
        val indexEntries = ArrayList<V3IndexEntryDto>(sessions.size)
        var messageCount = 0
        var fragmentCount = 0
        var fileCount = 0
        val filesToWrite = LinkedHashMap<String, ExportFile>()

        sessions.forEachIndexed { position, session ->
            // ⚠️ 必须按 **id** 排（= 落库顺序），不能按 inserted_at：
            // 真样本里 ASSISTANT 的 inserted_at 时常比它对应用户提问早几毫秒
            // （发言时间 vs 建行时间），按时间排会把一问一答的顺序翻过来。
            val rows = db.messageDao().withFragments(session.id).sortedBy { it.message.id }
            val messages = ArrayList<V3MessageDto>(rows.size)

            rows.forEach { row ->
                val attachments = row.attachments
                val fragments = ArrayList<JsonObject>(row.fragments.size + 1)
                var hasFileFragment = false

                row.fragments.forEach { fragment ->
                    val payload = runCatching { json.parseToJsonElement(fragment.payload) as JsonObject }
                        .getOrNull() ?: return@forEach
                    if (fragment.type == V3Importer.FRAGMENT_FILE) {
                        hasFileFragment = true
                        fragments += rewriteFilePaths(payload, session.id, attachments, filesToWrite)
                    } else {
                        fragments += payload
                    }
                }

                // 本地新建的附件没有 FILE 碎片（M4 只写 attachments 表）→ 导出时补一条
                if (!hasFileFragment && attachments.isNotEmpty()) {
                    fragments.add(
                        0,
                        synthesizeFileFragment(session.id, attachments, filesToWrite),
                    )
                }
                fragmentCount += fragments.size
                messageCount++

                messages += V3MessageDto(
                    id = row.message.id,
                    parentId = row.message.parentId,
                    role = row.message.role.name,
                    status = row.message.status.wire,
                    thinkingEnabled = row.message.thinkingEnabled,
                    searchEnabled = row.message.searchEnabled,
                    accumulatedTokenUsage = row.message.accumulatedTokenUsage,
                    insertedAt = V3Time.format(row.message.insertedAt),
                    fragments = fragments,
                )
            }

            writer.writeSession(
                SessionPayload(
                    id = session.id,
                    json = V3SessionJson.build(
                        sessionId = session.id,
                        title = session.title,
                        pinned = session.pinned,
                        currentMessageId = session.currentMessageId,
                        createdAt = session.createdAt,
                        updatedAt = session.updatedAt,
                        messages = messages,
                    ),
                ),
            )

            indexEntries += V3IndexEntryDto(
                id = session.id,
                title = session.title,
                pinned = session.pinned,
                messageCount = messages.size,
                insertedAt = V3Time.format(session.createdAt),
                updatedAt = V3Time.format(session.updatedAt),
            )
            onProgress(position + 1, sessions.size)
        }

        writer.writeIndex(indexEntries)

        filesToWrite.values.forEach { file ->
            file.file.inputStream().use { input -> writer.writeFile(file.sessionId, file.fileId, file.fileName, input) }
            fileCount++
        }

        writer.finish()
        ExportReport(
            sessions = sessions.size,
            messages = messageCount,
            fragments = fragmentCount,
            files = fileCount,
            bytes = writer.bytesWritten,
            durationMs = clock() - started,
        )
    }

    /** FILE 碎片：把每个 `files[]` 的 `export_path` 改写成这次要写的条目名（没有原件就置 null） */
    private fun rewriteFilePaths(
        payload: JsonObject,
        sessionId: String,
        attachments: List<AttachmentEntity>,
        out: MutableMap<String, ExportFile>,
    ): JsonObject {
        val refs = V3SessionJson.fileRefs(payload)
        if (refs.isEmpty()) return payload

        val byId = attachments.associateBy { it.id }
        val rewritten = refs.map { ref ->
            val id = (ref["id"] as? JsonPrimitive)?.contentOrNull ?: return@map ref
            val attachment = byId[id]
            val local = attachment?.localPath?.let { File(it) }
            if (attachment != null && local != null && local.isFile) {
                out[id] = ExportFile(sessionId, id, attachment.fileName.fileNameOnly(), local)
                rewriteExportPath(ref, V3Format.fileEntry(sessionId, id, attachment.fileName.fileNameOnly()))
            } else {
                rewriteExportPath(ref, null)
            }
        }
        return JsonObject(payload + ("files" to JsonArray(rewritten)))
    }

    /** 只换 export_path 的值，**保持键序不变**：否则同一份数据导出两次字节都不一样 */
    private fun rewriteExportPath(ref: JsonObject, path: String?): JsonObject = buildJsonObject {
        var seen = false
        ref.forEach { (key, value) ->
            if (key == "export_path") {
                seen = true
                put(key, path)
            } else {
                put(key, value)
            }
        }
        if (!seen) put("export_path", path)
    }

    /** 本地附件补的 FILE 碎片：字段取库里能给的（token_usage 我们没记，省略） */
    private fun synthesizeFileFragment(
        sessionId: String,
        attachments: List<AttachmentEntity>,
        out: MutableMap<String, ExportFile>,
    ): JsonObject {
        val files = attachments.map { attachment ->
            val local = attachment.localPath?.let { File(it) }
            val present = local != null && local.isFile
            if (local != null && local.isFile) {
                out[attachment.id] = ExportFile(sessionId, attachment.id, attachment.fileName.fileNameOnly(), local)
            }
            buildJsonObject {
                put("id", attachment.id)
                put("file_name", attachment.fileName)
                put("file_size", attachment.fileSize)
                put(
                    "export_path",
                    if (present) V3Format.fileEntry(sessionId, attachment.id, attachment.fileName.fileNameOnly()) else null,
                )
                put("is_image", attachment.isImage)
                put("width", attachment.width)
                put("height", attachment.height)
            }
        }
        return buildJsonObject {
            put("type", "FILE")
            put("files", JsonArray(files))
        }
    }
}

/** 导出时要写进包里的一个原件 */
data class ExportFile(
    val sessionId: String,
    val fileId: String,
    val fileName: String,
    val file: File,
)

/** 本地文件名的安全化（导出条目名里不能再带路径） */
private fun String.fileNameOnly(): String =
    substringAfterLast('/').substringAfterLast('\\').ifBlank { "file" }
