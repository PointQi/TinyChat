package io.tinychat.data.archive

import android.content.Context
import android.net.Uri
import io.tinychat.data.db.TinyChatDatabase
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 导入 / 导出的门面（M6-5）：只负责「从 SAF 的 Uri 开流」，具体格式逻辑在
 * [V3Importer] / [V3Exporter] 里。
 */
class ChatArchive(
    private val context: Context,
    private val db: TinyChatDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val attachmentsDir = File(context.filesDir, "attachments")

    /** 导入：**整包替换**本地数据（官方导出的语义就是"恢复备份"） */
    suspend fun import(uri: Uri): ImportReport {
        val report = withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw ArchiveException("打不开这个文件")
            val staged = File(context.cacheDir, "import-${java.util.UUID.randomUUID()}.zip")
            try {
                input.use { source -> staged.outputStream().use { sink -> source.copyTo(sink) } }
                V3Importer(db, attachmentsDir, clock).import(staged)
            } finally {
                runCatching { staged.delete() }
            }
        }
        // 替换后旧会话的附件文件已经没人引用，顺手清掉；再补一遍缩略图
        afterImport()
        return report
    }

    /** 导出：直接流式写到 SAF 给的位置 */
    suspend fun export(
        uri: Uri,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ExportReport = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw ArchiveException("打不开这个位置，无法写入")
        output.use { V3Exporter(db, clock).export(it, onProgress) }
    }

    /**
     * 直接读写文件（debug 验收台 / 单测用；SAF 那条路走上面的 Uri 版本）。
     */
    suspend fun importFile(file: File): ImportReport {
        val report = withContext(Dispatchers.IO) {
            V3Importer(db, attachmentsDir, clock).import(file)
        }
        afterImport()
        return report
    }

    /** 导入后收尾：补缩略图 + 清孤儿文件 */
    private suspend fun afterImport() = withContext(Dispatchers.IO) {
        val store = io.tinychat.data.attachment.AttachmentStore(context)
        // 导入只拷原件，不生成缩略图；图片消息会退化成"每次都解码原图"
        db.attachmentDao().all().forEach { row ->
            if (!row.isImage) return@forEach
            val path = row.localPath ?: return@forEach
            if (store.thumbFile(row.id).isFile) return@forEach
            store.writeThumbFromFile(row.id, java.io.File(path))
        }
        purgeOrphanAttachmentDirs()
    }

    suspend fun exportFile(
        file: File,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ExportReport = withContext(Dispatchers.IO) {
        file.outputStream().use { V3Exporter(db, clock).export(it, onProgress) }
    }

    /**
     * 清掉不再被 `attachments` 表引用的目录。
     *
     * 只在导入后调用：库里已经整包换过，磁盘上还留着上一份数据的目录。
     */
    private suspend fun purgeOrphanAttachmentDirs() {
        val aliveMessageIds = HashSet<String>()
        val aliveAttachmentIds = HashSet<String>()
        db.sessionDao().all().forEach { session ->
            db.messageDao().withFragments(session.id).forEach { row ->
                row.attachments.forEach { attachment ->
                    aliveMessageIds += attachment.messageId.toString()
                    aliveAttachmentIds += attachment.id
                }
            }
        }
        attachmentsDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.name !in aliveMessageIds) {
                runCatching { dir.deleteRecursively() }
            }
        }
        val thumbsDir = File(context.filesDir, "thumbs")
        thumbsDir.listFiles()?.forEach { file ->
            if (file.nameWithoutExtension !in aliveAttachmentIds) runCatching { file.delete() }
        }
    }
}
