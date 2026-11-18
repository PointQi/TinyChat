package io.tinychat.data.attachment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import io.tinychat.net.transport.ChatException
import io.tinychat.model.ErrorKind
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * 选择器给出的一个文件（还没入库）。
 *
 * 只带 `uri` 与元信息，**不拷原件**：原件的存放路径需要 `messageId`，
 * 而 `messageId` 要等用户点发送时才有（02 §3.4）。缩略图另外生成，路径固定。
 */
data class PickedFile(
    val id: String,
    val uri: Uri,
    val fileName: String,
    val size: Long,
    val mime: String?,
    val isImage: Boolean,
    val width: Int? = null,
    val height: Int? = null,
    /** 图片缩略图（webp）；非图片或解码失败为 null */
    val thumbPath: String? = null,
)

/** 落盘结果：原件绝对路径 + 实际大小 */
data class SavedFile(val path: String, val size: Long)

/**
 * 附件文件仓库（M4-1/2/3）：目录布局按 02 §3.4。
 *
 * ```
 * filesDir/attachments/{messageId}/{id}_{safeName}   # 原件
 * filesDir/thumbs/{id}.webp                          # 缩略图（最长边 512px）
 * ```
 *
 * 两条硬规则：流式拷贝（**禁止 `readAllBytes`**）、单文件 100MiB 上限。
 */
class AttachmentStore(private val context: Context) {

    companion object {
        /** Files API 单文件上限（03 §6.5） */
        const val MAX_BYTES: Long = 100L * 1024 * 1024

        /** 缩略图最长边 */
        const val THUMB_MAX_PX = 512

        private const val COPY_BUFFER = 64 * 1024
    }

    private val attachmentsRoot = File(context.filesDir, "attachments")
    private val thumbsDir = File(context.filesDir, "thumbs")

    fun newId(): String = UUID.randomUUID().toString()

    fun thumbFile(id: String): File = File(thumbsDir, "$id.webp")

    /**
     * 读元信息 + 生成缩略图。原件不落盘。
     *
     * 名字/大小读不出来时给兜底值而不是抛错：某些 provider（含拍照的 FileProvider）
     * 不实现 `OpenableColumns`，但内容本身可读。
     */
    fun inspect(uri: Uri): PickedFile {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        var name: String? = null
        var size = -1L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }

        val fileName = (name?.takeIf { it.isNotBlank() } ?: fallbackName(uri, mime))
            .substringAfterLast('/')
            .substringAfterLast('\\')
        val isImage = mime?.startsWith("image/") == true || fileName.isImageExtension()
        val id = newId()

        var width: Int? = null
        var height: Int? = null
        var thumbPath: String? = null
        if (isImage) {
            val bounds = decodeBounds(uri)
            width = bounds?.first
            height = bounds?.second
            thumbPath = runCatching { writeThumb(id, uri) }.getOrNull()?.absolutePath
        }

        return PickedFile(
            id = id,
            uri = uri,
            fileName = fileName,
            // 有些 provider 不给 SIZE；拷贝时再按实际字节校验上限
            size = if (size > 0) size else 0L,
            mime = mime ?: guessMime(fileName),
            isImage = isImage,
            width = width,
            height = height,
            thumbPath = thumbPath,
        )
    }

    /**
     * 把原件拷进 `attachments/{messageId}/`。超上限直接拒（不写半截文件）。
     */
    fun save(messageId: Long, picked: PickedFile): SavedFile {
        if (picked.size > MAX_BYTES) {
            throw ChatException(ErrorKind.UPLOAD, message = "附件超过 100MiB")
        }
        val dir = File(attachmentsRoot, messageId.toString())
        dir.mkdirs()
        val target = File(dir, "${picked.id}_${picked.fileName.safeName()}")
        val input = context.contentResolver.openInputStream(picked.uri)
            ?: throw ChatException(ErrorKind.UPLOAD, message = "打不开这个文件")
        input.use { source ->
            target.outputStream().use { sink ->
                val copied = copyLimited(source, sink)
                if (copied < 0) {
                    target.delete()
                    throw ChatException(ErrorKind.UPLOAD, message = "附件超过 100MiB")
                }
            }
        }
        return SavedFile(path = target.absolutePath, size = target.length())
    }

    /** 删除某个附件相关的本地文件（原件 + 缩略图），用于草稿移除/消息删除 */
    fun delete(attachmentId: String, localPath: String?) {
        runCatching { thumbFile(attachmentId).delete() }
        val file = localPath?.let { File(it) }
        file?.let { runCatching { it.delete() } }
        // 顺手收敛空目录：一块附件都没有了，`attachments/{messageId}/` 就别留着
        val parent = file?.parentFile
        if (parent != null && parent.isDirectory && parent.listFiles()?.isEmpty() == true) {
            runCatching { parent.delete() }
        }
    }

    /** 删除整个消息目录（删消息/删会话的清理路径，M6-6 也会用） */
    fun deleteMessageDir(messageId: Long) {
        runCatching { File(attachmentsRoot, messageId.toString()).deleteRecursively() }
    }

    fun exists(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val file = File(path)
        return file.isFile && file.length() > 0
    }

    // —— 内部 ——

    private fun decodeBounds(uri: Uri): Pair<Int, Int>? = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
    }.getOrNull()

    /** 缩略图：短边按 2 的幂降采样后再压到最长边 512px */
    private fun writeThumb(id: String, uri: Uri): File? {
        val bounds = decodeBounds(uri) ?: return null
        val longest = maxOf(bounds.first, bounds.second)
        var sample = 1
        while (longest / (sample * 2) >= THUMB_MAX_PX) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        val scaled = if (maxOf(bitmap.width, bitmap.height) > THUMB_MAX_PX) {
            val ratio = THUMB_MAX_PX.toFloat() / maxOf(bitmap.width, bitmap.height)
            val out = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
            if (out !== bitmap) bitmap.recycle()
            out
        } else {
            bitmap
        }

        thumbsDir.mkdirs()
        val target = thumbFile(id)
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        target.outputStream().use { scaled.compress(format, 80, it) }
        scaled.recycle()
        return target.takeIf { it.length() > 0 }
    }

    /** 流式拷贝；超过上限返回 -1（调用方负责删半截文件） */
    private fun copyLimited(source: InputStream, sink: java.io.OutputStream): Long {
        val buffer = ByteArray(COPY_BUFFER)
        var total = 0L
        while (true) {
            val read = try {
                source.read(buffer)
            } catch (e: IOException) {
                throw ChatException(ErrorKind.UPLOAD, message = e.message ?: "读取附件失败", cause = e)
            }
            if (read <= 0) break
            total += read
            if (total > MAX_BYTES) return -1
            sink.write(buffer, 0, read)
        }
        return total
    }

    private fun fallbackName(uri: Uri, mime: String?): String {
        val fromUri = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        if (fromUri != null) return fromUri
        val ext = mime?.substringAfterLast('/', "")?.takeIf { it.isNotBlank() } ?: "bin"
        return "附件.$ext"
    }

    private fun guessMime(fileName: String): String? = when (fileName.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "pdf" -> "application/pdf"
        "txt", "md", "log" -> "text/plain"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "epub" -> "application/epub+zip"
        else -> null
    }

    private fun String.isImageExtension(): Boolean =
        substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif")

    private fun String.safeName(): String {
        val cleaned = filterNot { it in "\\/:*?\"<>|" }.trim()
        val limited = if (cleaned.length > 80) cleaned.takeLast(80) else cleaned
        return limited.ifBlank { "file" }
    }
}
