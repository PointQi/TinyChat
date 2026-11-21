package io.tinychat.data.attachment

import io.tinychat.model.ErrorKind
import io.tinychat.net.files.AttachmentPurpose
import io.tinychat.net.files.FilesClient
import io.tinychat.net.transport.ChatException
import java.io.File

/**
 * 上传附件（M4-3）：本地文件 → Files API `file_id`。
 *
 * 只做「一次上传」这一件事；重试、状态流转、进度落库都在 [io.tinychat.data.chat.ChatRepository] 里，
 * 这样断网恢复时可以重跑同一个附件而不必重新选文件。
 */
class AttachmentUploader(
    private val files: FilesClient = FilesClient(),
) {

    suspend fun upload(
        apiKey: String,
        localPath: String,
        fileName: String,
        mime: String?,
        isImage: Boolean,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> },
    ): String {
        val file = File(localPath)
        if (!file.isFile) {
            throw ChatException(ErrorKind.UPLOAD, message = "附件文件已丢失或被移动，请重新上传")
        }
        val purpose = AttachmentPurpose.of(fileName, isImage)
        return files.upload(
            apiKey = apiKey,
            fileName = fileName,
            mimeType = mime ?: "application/octet-stream",
            file = file,
            purpose = purpose,
            onProgress = onProgress,
        ).fileId
    }
}
