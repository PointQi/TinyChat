package io.tinychat.data.db

import androidx.room.TypeConverter
import io.tinychat.model.AttachmentState
import io.tinychat.model.ErrorKind
import io.tinychat.model.MessageStatus
import io.tinychat.model.PromptMode
import io.tinychat.model.Role
import io.tinychat.model.ThinkingEffort

/**
 * 枚举落库一律存 `wire` 字符串（与导出/协议同源）。
 *
 * 遇到无法识别的值时**抛异常**，不静默兜底：导入器在写库前必须已校验过，
 * 抛出来就是 bug，宁可整包回滚（02 §5.5）也不能写进一条被悄悄改过的数据。
 */
class Converters {

    @TypeConverter
    fun roleToString(value: Role): String = value.wire

    @TypeConverter
    fun stringToRole(value: String): Role =
        Role.fromWire(value) ?: error("未知 role: $value")

    @TypeConverter
    fun statusToString(value: MessageStatus): String = value.wire

    @TypeConverter
    fun stringToStatus(value: String): MessageStatus =
        MessageStatus.fromWire(value) ?: error("未知 message status: $value")

    @TypeConverter
    fun promptModeToString(value: PromptMode): String = value.wire

    @TypeConverter
    fun stringToPromptMode(value: String): PromptMode =
        PromptMode.entries.firstOrNull { it.wire == value } ?: error("未知 prompt mode: $value")

    @TypeConverter
    fun effortToString(value: ThinkingEffort): String = value.wire

    @TypeConverter
    fun stringToEffort(value: String): ThinkingEffort =
        ThinkingEffort.entries.firstOrNull { it.wire == value } ?: error("未知 effort: $value")

    @TypeConverter
    fun errorKindToString(value: ErrorKind): String = value.wire

    @TypeConverter
    fun stringToErrorKind(value: String): ErrorKind =
        ErrorKind.fromWire(value) ?: error("未知 error kind: $value")

    @TypeConverter
    fun attachmentStateToString(value: AttachmentState): String = value.wire

    @TypeConverter
    fun stringToAttachmentState(value: String): AttachmentState =
        AttachmentState.fromWire(value) ?: error("未知 attachment state: $value")
}
