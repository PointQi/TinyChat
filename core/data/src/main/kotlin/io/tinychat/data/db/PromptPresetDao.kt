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

    /** 新预设追加到列表末尾 */
    @Query("SELECT COALESCE(MAX(sort), -1) + 1 FROM prompt_presets")
    suspend fun nextSort(): Int

    /** 决策 45 之后不再有内置预设，删除不分内外。 */
    @Query("DELETE FROM prompt_presets WHERE id = :id")
    suspend fun delete(id: String)
}
