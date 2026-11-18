package io.tinychat.data.archive

import androidx.room.withTransaction
import io.tinychat.data.db.AttachmentEntity
import io.tinychat.data.db.FragmentEntity
import io.tinychat.data.db.MessageEntity
import io.tinychat.data.db.SessionEntity
import io.tinychat.data.db.TinyChatDatabase
import io.tinychat.model.AttachmentState
import io.tinychat.model.KnownProviders
import io.tinychat.model.MessageStatus
import io.tinychat.model.PromptMode
import io.tinychat.model.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.InputStream

/**
 * 导入 v3 导出包（M6-2/3）：**整包一个事务**，中途任何异常都回滚到导入前。
 *
 * 流程：清库 → 逐会话插入（消息先插后补 parent）→ 碎片入表（FILE 碎片同时落附件行）
 * → 附件原件边读边写到 `attachments/{messageId}/{fileId}_{name}` → 用
 * `chat_session_index.json` 交叉校验会话数与 message_count。
 *
 * 丢弃规则（03 §6.8）：`TIP`（UI 提示条）与 `CONTENT_RISK`（同一句英文免责声明）。
 */
class V3Importer(
    private val db: TinyChatDatabase,
    private val attachmentsDir: File,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val reader = V3ArchiveReader()

    /**
     * 导入一个已落盘的 zip 文件（随机访问需要文件，见 [V3ArchiveReader] 的说明）。
     */
    suspend fun import(zip: File): ImportReport = withContext(Dispatchers.IO) {
        val started = clock()
        attachmentsDir.mkdirs()
        val touchedDirs = LinkedHashSet<File>()
        var sessions = 0
        var messages = 0
        var fragments = 0
        var files = 0
        var dropped = 0
        var index: List<V3IndexEntryDto>? = null
        val actualCounts = HashMap<String, Int>()

        val listener = object : V3ArchiveReader.Listener {

            override suspend fun onSession(session: V3SessionDto) {
                if (session.id.isBlank()) throw ArchiveException("会话缺少 id")
                val ids = session.messages.map { it.id }.toSet()
                if (ids.size != session.messages.size) {
                    throw ArchiveException("会话 ${session.id} 里有重复的消息 id")
                }
                session.messages.forEach { message ->
                    val parent = message.parentId
                    if (parent != null && parent !in ids) {
                        throw ArchiveException("会话 ${session.id} 的消息 ${message.id} 指向不存在的父节点 $parent")
                    }
                }

                val createdAt = V3Time.parse(session.insertedAt) ?: clock()
                val updatedAt = V3Time.parse(session.updatedAt) ?: createdAt
                val thinking = session.messages.firstOrNull()?.thinkingEnabled ?: false
                val search = session.messages.firstOrNull()?.searchEnabled ?: false

                db.sessionDao().upsert(
                    SessionEntity(
                        id = session.id,
                        title = session.title,
                        pinned = session.pinned,
                        currentMessageId = null,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        providerId = KnownProviders.deepSeek.id,
                        model = KnownProviders.flash.id,
                        thinkingEnabled = thinking,
                        searchEnabled = search,
                        effort = null,
                        systemPromptSnapshot = null,
                        systemPromptMode = PromptMode.REPLACE,
                        deleted = false,
                    ),
                )

                // 1) 先按包内顺序插成新行（parent 留空），拿到新 id
                val rows = session.messages.map { message ->
                    MessageEntity(
                        sessionId = session.id,
                        parentId = null,
                        role = parseRole(message.role, session.id, message.id),
                        status = parseStatus(message.status, session.id, message.id),
                        thinkingEnabled = message.thinkingEnabled,
                        searchEnabled = message.searchEnabled,
                        accumulatedTokenUsage = message.accumulatedTokenUsage,
                        insertedAt = V3Time.parse(message.insertedAt) ?: createdAt,
                    )
                }
                val newIds = db.messageDao().insertAll(rows)
                val idMap = session.messages.mapIndexed { i, message -> message.id to newIds[i] }.toMap()

                // 2) 补父子关系
                session.messages.forEachIndexed { i, message ->
                    message.parentId?.let { parent ->
                        db.messageDao().setParent(newIds[i], idMap.getValue(parent))
                    }
                }
                db.sessionDao().setCurrentMessage(
                    session.id,
                    session.currentMessageId?.let { idMap[it] },
                    updatedAt,
                )

                // 3) 碎片 + 附件
                session.messages.forEachIndexed { i, message ->
                    val fragmentRows = ArrayList<FragmentEntity>(message.fragments.size)
                    message.fragments.forEach { fragment ->
                        val type = V3SessionJson.fragmentType(fragment)
                            ?: throw ArchiveException("会话 ${session.id} 的碎片缺少 type")
                        if (type in DROPPED_FRAGMENTS) {
                            dropped++
                            return@forEach
                        }
                        fragmentRows += FragmentEntity(
                            messageId = newIds[i],
                            ord = fragmentRows.size,
                            type = type,
                            payload = fragment.toString(),
                        )
                        if (type == FRAGMENT_FILE) {
                            attachmentRows(session.id, newIds[i], fragment).forEach { attachment ->
                                db.attachmentDao().upsert(attachment)
                            }
                        }
                    }
                    if (fragmentRows.isNotEmpty()) {
                        db.fragmentDao().insertAll(fragmentRows)
                        fragments += fragmentRows.size
                    }
                }

                sessions++
                messages += newIds.size
                actualCounts[session.id] = newIds.size
            }

            override suspend fun onIndex(entries: List<V3IndexEntryDto>) {
                index = entries
            }

            override suspend fun onFile(
                sessionId: String,
                fileId: String,
                fileName: String,
                content: InputStream,
            ) {
                // 同一个附件可能挂在多条消息上，取第一条定位文件目录即可
                val attachment = db.attachmentDao().byId(fileId).firstOrNull()
                    ?: return // 碎片被丢弃的孤儿文件
                val target = File(attachmentsDir, "${attachment.messageId}").apply { mkdirs() }
                touchedDirs += target
                val file = File(target, "${fileId}_${attachment.fileName.safeFileName()}")
                file.outputStream().use { sink -> content.copyTo(sink, 64 * 1024) }
                db.attachmentDao().setLocal(fileId, file.absolutePath, AttachmentState.LOCAL)
                files++
            }
        }

        try {
            db.withTransaction {
                db.sessionDao().deleteAll()
                reader.read(zip, listener)
                // 交叉校验：条索与实际对不上就整包失败（M6-2）
                index?.let { entries ->
                    if (entries.size != sessions) {
                        throw ArchiveException("索引里有 ${entries.size} 个会话，实际读到 $sessions 个")
                    }
                    entries.forEach { entry ->
                        val actual = actualCounts[entry.id]
                            ?: throw ArchiveException("索引里的会话 ${entry.id} 在包内不存在")
                        if (actual != entry.messageCount) {
                            throw ArchiveException(
                                "会话 ${entry.id} 的 message_count=${entry.messageCount}，实际 $actual 条",
                            )
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            // 事务已回滚；文件是事务外的副作用，得自己清（只清这次写过的目录）
            touchedDirs.forEach { dir ->
                runCatching { dir.listFiles()?.forEach { it.delete() } }
                runCatching { dir.delete() }
            }
            throw e
        }

        ImportReport(
            sessions = sessions,
            messages = messages,
            fragments = fragments,
            files = files,
            droppedFragments = dropped,
            durationMs = clock() - started,
        )
    }

    private fun attachmentRows(sessionId: String, messageId: Long, fragment: JsonObject): List<AttachmentEntity> =
        V3SessionJson.fileRefs(fragment).mapNotNull { ref ->
            val id = ref.string("id") ?: return@mapNotNull null
            AttachmentEntity(
                id = id,
                messageId = messageId,
                fileName = ref.string("file_name") ?: id,
                fileSize = ref.long("file_size") ?: 0L,
                mime = null,
                localPath = null,
                remoteId = null,
                isImage = ref["is_image"] == JsonPrimitive(true),
                width = ref.int("width"),
                height = ref.int("height"),
                // 原件还没到（可能后面会出现，也可能根本没有）→ 先标 MISSING
                state = AttachmentState.MISSING,
            )
        }

    private fun parseRole(raw: String, sessionId: String, messageId: Long): Role =
        when (raw.uppercase()) {
            "USER" -> Role.USER
            "ASSISTANT" -> Role.ASSISTANT
            else -> throw ArchiveException("会话 $sessionId 的消息 $messageId 角色未知：$raw")
        }

    private fun parseStatus(raw: String, sessionId: String, messageId: Long): MessageStatus =
        MessageStatus.fromWire(raw.uppercase())
            ?: throw ArchiveException("会话 $sessionId 的消息 $messageId 状态未知：$raw")

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.int(key: String): Int? = long(key)?.toInt()

    companion object {
        const val FRAGMENT_FILE = "FILE"

        /** 03 §6.8：TIP 是 UI 提示条、CONTENT_RISK 是同一句英文免责声明 */
        val DROPPED_FRAGMENTS = setOf("TIP", "CONTENT_RISK")

        /** 文件名里不能出现路径分隔符（导入包是外部输入） */
        fun String.safeFileName(): String =
            filterNot { it in "\\/:*?\"<>|" }.trim().ifBlank { "file" }.take(120)
    }
}
