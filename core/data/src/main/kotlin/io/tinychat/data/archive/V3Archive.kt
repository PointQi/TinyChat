package io.tinychat.data.archive

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// —— DTO：字段名与 v3 导出一一对应（未知字段忽略，见 03 §M6） ——

@Serializable
data class V3ManifestDto(
    @SerialName("export_format_version") val version: Int = 0,
)

@Serializable
data class V3SessionDto(
    val id: String,
    val title: String? = null,
    val pinned: Boolean = false,
    @SerialName("current_message_id") val currentMessageId: Long? = null,
    @SerialName("inserted_at") val insertedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val messages: List<V3MessageDto> = emptyList(),
)

@Serializable
data class V3MessageDto(
    val id: Long,
    @SerialName("parent_id") val parentId: Long? = null,
    val role: String = "USER",
    val status: String = "FINISHED",
    @SerialName("thinking_enabled") val thinkingEnabled: Boolean = false,
    @SerialName("search_enabled") val searchEnabled: Boolean = false,
    @SerialName("accumulated_token_usage") val accumulatedTokenUsage: Int = 0,
    @SerialName("inserted_at") val insertedAt: String? = null,
    val fragments: List<JsonObject> = emptyList(),
)

@Serializable
data class V3IndexEntryDto(
    val id: String,
    val title: String? = null,
    val pinned: Boolean = false,
    @SerialName("message_count") val messageCount: Int = 0,
    @SerialName("inserted_at") val insertedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** 导出时一个会话的 JSON 字节（由装配层从库里拼好） */
data class SessionPayload(val id: String, val json: ByteArray)

/**
 * 读 v3 导出包（M6-1）：**不整包进内存**，逐条目交给 [Listener]。
 *
 * ⚠️ 用 [java.util.zip.ZipFile]（中央目录）而**不是** `ZipInputStream`：
 * 真样本里有 STORE 条目带 data descriptor，JDK 的 ZipInputStream 会直接抛
 * `only DEFLATED entries can have EXT descriptor`，Android 上则会静默停在那个条目上
 * （现象：79 个附件只导进来 1 个）。ZipFile 先从中央目录拿尺寸，STORE/DEFLATE 都正常。
 *
 * 代价是需要**随机访问**，所以调用方先把流落成临时文件（[V3Importer] 负责）。
 *
 * 三条硬规矩（03 §6.8）：
 * 1. 第一条**必须**是 `manifest.json` 且 `export_format_version == 3`；
 * 2. 任何**未识别**的条目 → 整包失败（不猜、不跳过）；
 * 3. `user.json` 跳过（账号信息，我们不用）。
 */
class V3ArchiveReader {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    interface Listener {
        suspend fun onSession(session: V3SessionDto)

        /** 索引条目：用来校验「会话数 / message_count 与实际一致」 */
        suspend fun onIndex(entries: List<V3IndexEntryDto>)

        /** 附件原件：**必须在回调里把 [content] 读完** */
        suspend fun onFile(sessionId: String, fileId: String, fileName: String, content: InputStream)
    }

    suspend fun read(zipFile: java.io.File, listener: Listener) {
        val zip = try {
            java.util.zip.ZipFile(zipFile)
        } catch (e: Exception) {
            throw ArchiveException("无效的备份文件", e)
        }
        zip.use {
            val entries = zip.entries().toList()
            if (entries.isEmpty()) throw ArchiveException("空包：没有 ${V3Format.MANIFEST}")

            // 1) 第一条必须是 manifest
            val first = entries.first()
            if (first.name != V3Format.MANIFEST) {
                throw ArchiveException("导出包第一条必须是 ${V3Format.MANIFEST}，实际是「${first.name}」")
            }
            val version = json.decodeFromString(
                V3ManifestDto.serializer(),
                zip.readEntry(first).decodeToString(),
            ).version
            if (version != V3Format.VERSION) {
                throw ArchiveException("备份版本过高，请先升级 App")
            }

            // 2) 逐条目
            entries.drop(1).forEach { entry ->
                if (entry.isDirectory) return@forEach
                val name = entry.name
                when {
                    name == V3Format.MANIFEST -> throw ArchiveException("${V3Format.MANIFEST} 出现了两次")

                    name == V3Format.USER -> Unit // 账号信息，跳过

                    name == V3Format.INDEX ->
                        listener.onIndex(parseIndex(zip.readEntry(entry).decodeToString()))

                    name.startsWith(V3Format.SESSION_DIR) && name.endsWith(V3Format.SESSION_SUFFIX) ->
                        listener.onSession(parseSession(zip.readEntry(entry).decodeToString(), name))

                    else -> {
                        val parsed = V3Format.parseFileEntry(name)
                            ?: throw ArchiveException("未识别的 ZIP 条目：$name")
                        zip.getInputStream(entry).use { input ->
                            listener.onFile(parsed.first, parsed.second, parsed.third, input)
                        }
                    }
                }
            }
        }
    }

    private fun parseSession(text: String, entry: String): V3SessionDto =
        try {
            json.decodeFromString(V3SessionDto.serializer(), text)
        } catch (e: Exception) {
            throw ArchiveException("会话 JSON 解析失败（$entry）：${e.message}", e)
        }

    private fun parseIndex(text: String): List<V3IndexEntryDto> =
        try {
            json.decodeFromString(ListSerializer(V3IndexEntryDto.serializer()), text)
        } catch (e: Exception) {
            throw ArchiveException("${V3Format.INDEX} 解析失败：${e.message}", e)
        }

    /** 单个 JSON 条目的体积上限：防止畸形包把内存撑爆（最大会话实测 3.6MB） */
    private fun java.util.zip.ZipFile.readEntry(entry: ZipEntry): ByteArray {
        if (entry.size > MAX_ENTRY_BYTES) {
            throw ArchiveException("条目过大（${entry.name}）：超过 ${MAX_ENTRY_BYTES / 1024 / 1024}MB")
        }
        return getInputStream(entry).use { it.readBytes() }
    }

    companion object {
        const val MAX_ENTRY_BYTES: Long = 64L * 1024 * 1024
    }
}

/**
 * 流式写 v3 导出包（M6-4）：条目顺序与官方一致，**不新增条目、不新增会话字段**。
 *
 * 会话 JSON 由 [V3SessionJson] 拼好，这里只管字节与顺序。
 */
class V3ArchiveWriter(out: OutputStream) {

    private val zip = ZipOutputStream(BufferedOutputStream(out, 64 * 1024))

    var bytesWritten: Long = 0
        private set

    fun writeManifest() {
        writeEntry(V3Format.MANIFEST, """{"export_format_version":${V3Format.VERSION}}""".toByteArray())
    }

    /** 账号信息我们没有（也不该导出），写空对象，官方导入端会跳过 */
    fun writeUser() = writeEntry(V3Format.USER, "{}".toByteArray())

    fun writeSession(session: SessionPayload) =
        writeEntry(V3Format.sessionEntry(session.id), session.json)

    fun writeIndex(entries: List<V3IndexEntryDto>) {
        val array = buildJsonArray {
            entries.forEach { entry ->
                add(
                    buildJsonObject {
                        put("id", entry.id)
                        put("title", entry.title)
                        put("pinned", entry.pinned)
                        put("message_count", entry.messageCount)
                        put("inserted_at", entry.insertedAt)
                        put("updated_at", entry.updatedAt)
                    },
                )
            }
        }
        writeEntry(V3Format.INDEX, array.toString().toByteArray())
    }

    fun writeFile(sessionId: String, fileId: String, fileName: String, content: InputStream) {
        val entry = ZipEntry(V3Format.fileEntry(sessionId, fileId, fileName))
        zip.putNextEntry(entry)
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = content.read(buffer)
            if (read <= 0) break
            zip.write(buffer, 0, read)
            bytesWritten += read
        }
        zip.closeEntry()
    }

    /** 收尾（写完最后一个条目）。底层流由调用方关闭。 */
    fun finish() {
        zip.finish()
        zip.flush()
    }

    private fun writeEntry(name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
        bytesWritten += bytes.size
    }
}

/** 会话 JSON 拼装：字段顺序与官方导出一致；null 也照写，别多别少 */
object V3SessionJson {

    fun build(
        sessionId: String,
        title: String?,
        pinned: Boolean,
        currentMessageId: Long?,
        createdAt: Long,
        updatedAt: Long,
        messages: List<V3MessageDto>,
    ): ByteArray {
        val root = buildJsonObject {
            put("id", sessionId)
            put("title", title)
            put("pinned", pinned)
            put("current_message_id", currentMessageId)
            put("inserted_at", V3Time.format(createdAt))
            put("updated_at", V3Time.format(updatedAt))
            put(
                "messages",
                buildJsonArray {
                    messages.forEach { message ->
                        add(
                            buildJsonObject {
                                put("id", message.id)
                                put("parent_id", message.parentId)
                                put("role", message.role)
                                put("status", message.status)
                                put("thinking_enabled", message.thinkingEnabled)
                                put("search_enabled", message.searchEnabled)
                                put("accumulated_token_usage", message.accumulatedTokenUsage)
                                // 已在 DTO 里换算成官方 ISO 格式
                                put("inserted_at", message.insertedAt)
                                put("fragments", JsonArray(message.fragments))
                            },
                        )
                    }
                },
            )
        }
        return root.toString().toByteArray()
    }

    /** 碎片类型（落库列用）；形状不对返回 null */
    fun fragmentType(fragment: JsonObject): String? =
        (fragment["type"] as? JsonPrimitive)?.contentOrNull

    /** FILE 碎片里的 `files[]` */
    fun fileRefs(fragment: JsonObject): List<JsonObject> =
        (fragment["files"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
}
