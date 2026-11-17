package io.tinychat.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PromptPresetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PromptPresetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PromptPresetEntity>)

    @Query("SELECT * FROM prompt_presets ORDER BY sort ASC, id ASC")
    suspend fun all(): List<PromptPresetEntity>

    @Query("SELECT * FROM prompt_presets WHERE id = :id")
    suspend fun byId(id: String): PromptPresetEntity?

    /** 内置预设不可删（M7.5） */
    @Query("DELETE FROM prompt_presets WHERE id = :id AND builtin = 0")
    suspend fun deleteCustom(id: String)
}
