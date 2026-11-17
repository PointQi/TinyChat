package io.tinychat.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 碎片 payload ⇄ JSON 编解码。
 *
 * 约定：
 * 1. 落库/导出的 JSON **与 v3 导出逐字段同构**，`type` 字段写在最前；
 * 2. 已知类型用强类型模型；解码失败（字段类型不符、出现未知字段）时**降级为
 *    [UnknownPayload] 原样保留**，绝不静默丢字段，也绝不抛异常中断导入；
 * 3. `UnknownPayload` 的 `raw` **不含** `type`，由本类统一补。
 */
object FragmentPayloadCodec {

    private val json = Json {
        encodeDefaults = false
        // 值为 null 的字段直接省略，与真实样本一致（null 字段在样本里就是缺省）
        explicitNulls = false
        ignoreUnknownKeys = false
        prettyPrint = false
    }

    private val serializers: Map<String, KSerializer<out FragmentPayload>> = mapOf(
        FragmentType.REQUEST.wire to RequestPayload.serializer(),
        FragmentType.RESPONSE.wire to ResponsePayload.serializer(),
        FragmentType.THINK.wire to ThinkPayload.serializer(),
        FragmentType.TEMPLATE_RESPONSE.wire to TemplateResponsePayload.serializer(),
        FragmentType.FILE.wire to FilePayload.serializer(),
        FragmentType.TOOL_SEARCH.wire to ToolSearchPayload.serializer(),
        FragmentType.TOOL_OPEN.wire to ToolOpenPayload.serializer(),
        FragmentType.TOOL_FIND.wire to ToolFindPayload.serializer(),
        FragmentType.TOOL_IMAGE_SEARCH.wire to ToolImageSearchPayload.serializer(),
        FragmentType.READ_LINK.wire to ReadLinkPayload.serializer(),
    )

    /** payload → JSON 文本（含 `type`） */
    fun encode(payload: FragmentPayload): String {
        // @Serializable 的 data class 序列化结果必然是 JsonObject，这里仍按 JsonElement 处理
        val body: JsonElement = when (payload) {
            is RequestPayload -> json.encodeToJsonElement(RequestPayload.serializer(), payload)
            is ResponsePayload -> json.encodeToJsonElement(ResponsePayload.serializer(), payload)
            is ThinkPayload -> json.encodeToJsonElement(ThinkPayload.serializer(), payload)
            is TemplateResponsePayload ->
                json.encodeToJsonElement(TemplateResponsePayload.serializer(), payload)
            is FilePayload -> json.encodeToJsonElement(FilePayload.serializer(), payload)
            is ToolSearchPayload -> json.encodeToJsonElement(ToolSearchPayload.serializer(), payload)
            is ToolOpenPayload -> json.encodeToJsonElement(ToolOpenPayload.serializer(), payload)
            is ToolFindPayload -> json.encodeToJsonElement(ToolFindPayload.serializer(), payload)
            is ToolImageSearchPayload ->
                json.encodeToJsonElement(ToolImageSearchPayload.serializer(), payload)
            is ReadLinkPayload -> json.encodeToJsonElement(ReadLinkPayload.serializer(), payload)
            is UnknownPayload -> payload.raw
        }

        return buildJsonObject {
            put("type", payload.typeWire)
            if (body is JsonObject) body.forEach { (key, value) -> put(key, value) }
        }.toString()
    }

    /** JSON 文本 → payload；type 缺失或未识别一律降级为 [UnknownPayload] */
    fun decode(text: String): FragmentPayload {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
                ?: return UnknownPayload("", JsonObject(emptyMap()))
        } catch (e: SerializationException) {
            // JSON 本身损坏：保留原文，交由上层决定是否丢弃
            return UnknownPayload("", JsonObject(emptyMap()))
        }

        val wireType = (root["type"] as? JsonPrimitive)?.content ?: ""
        val body = JsonObject(root.filterKeys { it != "type" })
        val serializer = serializers[wireType]
            ?: return UnknownPayload(wireType, body)

        return try {
            json.decodeFromJsonElement(serializer, body)
        } catch (e: SerializationException) {
            // 字段类型不符 / 出现已知模型里没有的字段 → 整块原样保留
            UnknownPayload(wireType, body)
        } catch (e: IllegalArgumentException) {
            UnknownPayload(wireType, body)
        }
    }
}
