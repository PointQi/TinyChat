package io.tinychat.data.db

import io.tinychat.model.Attachment
import io.tinychat.model.Fragment
import io.tinychat.model.FragmentPayloadCodec
import io.tinychat.model.Message
import io.tinychat.model.Session
import io.tinychat.model.Usage

/**
 * 实体 ⇄ 领域模型映射。
 *
 * 约定：映射层不做校验、不做业务判断，只做形状转换；
 * 语义判断（哪些消息能进上下文等）属于 M1-10 的上下文重建器。
 */

fun SessionEntity.toDomain(): Session = Session(
    id = id,
    title = title,
    pinned = pinned,
    currentMessageId = currentMessageId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    providerId = providerId,
    model = model,
    thinkingEnabled = thinkingEnabled,
    searchEnabled = searchEnabled,
    effort = effort,
    systemPromptSnapshot = systemPromptSnapshot,
    systemPromptMode = systemPromptMode,
    deleted = deleted,
)

fun Session.toEntity(): SessionEntity = SessionEntity(
    id = id,
    title = title,
    pinned = pinned,
    currentMessageId = currentMessageId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    providerId = providerId,
    model = model,
    thinkingEnabled = thinkingEnabled,
    searchEnabled = searchEnabled,
    effort = effort,
    systemPromptSnapshot = systemPromptSnapshot,
    systemPromptMode = systemPromptMode,
    deleted = deleted,
)

fun MessageEntity.toDomain(): Message = Message(
    id = id,
    sessionId = sessionId,
    parentId = parentId,
    role = role,
    status = status,
    thinkingEnabled = thinkingEnabled,
    searchEnabled = searchEnabled,
    accumulatedTokenUsage = accumulatedTokenUsage,
    insertedAt = insertedAt,
    model = model,
    effort = effort,
    usage = toUsage(),
    errorKind = errorKind,
    errorMessage = errorMessage,
    reminderSnapshot = reminderSnapshot,
    thinkingSignature = thinkingSignature,
)

private fun MessageEntity.toUsage(): Usage? {
    val anyPresent = listOf(
        promptTokens, cacheHitTokens, completionTokens, thinkingTokens,
        safetyTokens, searchCalls, fetchCalls, imageSearchCalls,
    ).any { it != null }
    if (!anyPresent) return null
    return Usage(
        promptTokens = promptTokens ?: 0,
        cacheHitTokens = cacheHitTokens ?: 0,
        completionTokens = completionTokens ?: 0,
        thinkingTokens = thinkingTokens ?: 0,
        safetyTokens = safetyTokens ?: 0,
        searchCalls = searchCalls ?: 0,
        fetchCalls = fetchCalls ?: 0,
        imageSearchCalls = imageSearchCalls ?: 0,
    )
}

fun Message.toEntity(costMicros: Long? = null): MessageEntity = MessageEntity(
    id = id,
    sessionId = sessionId,
    parentId = parentId,
    role = role,
    status = status,
    thinkingEnabled = thinkingEnabled,
    searchEnabled = searchEnabled,
    accumulatedTokenUsage = accumulatedTokenUsage,
    insertedAt = insertedAt,
    model = model,
    effort = effort,
    promptTokens = usage?.promptTokens,
    cacheHitTokens = usage?.cacheHitTokens,
    completionTokens = usage?.completionTokens,
    thinkingTokens = usage?.thinkingTokens,
    safetyTokens = usage?.safetyTokens,
    searchCalls = usage?.searchCalls,
    fetchCalls = usage?.fetchCalls,
    imageSearchCalls = usage?.imageSearchCalls,
    costMicros = costMicros,
    errorKind = errorKind,
    errorMessage = errorMessage,
    reminderSnapshot = reminderSnapshot,
    thinkingSignature = thinkingSignature,
)

fun FragmentEntity.toDomain(): Fragment = Fragment(
    id = id,
    messageId = messageId,
    ord = ord,
    payload = FragmentPayloadCodec.decode(payload),
)

fun Fragment.toEntity(): FragmentEntity = FragmentEntity(
    id = id,
    messageId = messageId,
    ord = ord,
    // type 列取 payload 的 wire 值，未识别类型也能原样落库
    type = payload.typeWire,
    payload = FragmentPayloadCodec.encode(payload),
)

fun AttachmentEntity.toDomain(): Attachment = Attachment(
    id = id,
    messageId = messageId,
    fileName = fileName,
    fileSize = fileSize,
    mime = mime,
    localPath = localPath,
    remoteId = remoteId,
    isImage = isImage,
    width = width,
    height = height,
    state = state,
)

fun Attachment.toEntity(): AttachmentEntity = AttachmentEntity(
    id = id,
    messageId = messageId,
    fileName = fileName,
    fileSize = fileSize,
    mime = mime,
    localPath = localPath,
    remoteId = remoteId,
    isImage = isImage,
    width = width,
    height = height,
    state = state,
)
