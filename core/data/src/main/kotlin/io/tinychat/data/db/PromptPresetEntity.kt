package io.tinychat.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.tinychat.model.PromptMode

/**
 * `prompt_presets` 表（02 §3.1）。内置预设 `builtin = 1`，不可删改（M7.5）。
 */
@Entity(tableName = "prompt_presets")
data class PromptPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val content: String,
    val mode: PromptMode,
    /** 1 = 内置，不可删除 */
    val builtin: Boolean = false,
    val sort: Int = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
