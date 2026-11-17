package io.tinychat.model

enum class AttachmentState(val wire: String) {
    /** 只在本机 */
    LOCAL("LOCAL"),
    UPLOADING("UPLOADING"),
    /** 已拿到 Files API 的 file_id */
    UPLOADED("UPLOADED"),

    /** 上传失败，可重试 */
    FAILED("FAILED"),

    /** 导入包里只有元数据，原件不在 */
    MISSING("MISSING"),
    ;

    companion object {
        fun fromWire(value: String?): AttachmentState? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * 附件。`id` 是归档里的 file id（v3 的 `FILE.files[].id`），不是自增主键。
 */
data class Attachment(
    val id: String,
    val messageId: Long,
    val fileName: String,
    val fileSize: Long,
    val mime: String? = null,
    /** 原件的本地路径；null = 只有元数据 */
    val localPath: String? = null,
    /** Files API 的 file_id；null = 未上传 */
    val remoteId: String? = null,
    val isImage: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    val state: AttachmentState = AttachmentState.LOCAL,
)
