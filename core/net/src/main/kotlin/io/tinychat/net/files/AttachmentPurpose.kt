package io.tinychat.net.files

/**
 * `purpose` 自动判定（M4-5，规则来自 03 §6.5）。
 *
 * - **图片**：走 `user_data`（图片在对话里用 `image` 块引用，服务端不需要跑版式识别）；
 * - **解析型文档**（PDF / Word / PPT）：`text_recognition`，服务端要跑 OCR/抽取；
 * - **纯文本类**（txt/md/csv/json/代码/XLSX/EPUB）：`user_data`，**不计费**。
 *
 * 含图版式的 `rich_layout_recognition` 需要用户显式选择，不做自动判定 —— 猜错要多花钱。
 */
object AttachmentPurpose {

    private val PARSE_BY_OCR = setOf("pdf", "doc", "docx", "ppt", "pptx", "rtf", "odt", "odp", "ods")

    fun of(fileName: String, isImage: Boolean): FilePurpose {
        if (isImage) return FilePurpose.USER_DATA
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return if (ext in PARSE_BY_OCR) FilePurpose.TEXT_RECOGNITION else FilePurpose.USER_DATA
    }
}
