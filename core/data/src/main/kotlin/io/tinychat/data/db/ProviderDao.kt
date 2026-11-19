package io.tinychat.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {

    @Query("SELECT * FROM providers ORDER BY sort, id")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers ORDER BY sort, id")
    suspend fun all(): List<ProviderEntity>

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun byId(id: String): ProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProviderEntity)

    @Query("DELETE FROM providers WHERE id = :id AND builtin = 0")
    suspend fun delete(id: String)

    @Query("SELECT COALESCE(MAX(sort), -1) + 1 FROM providers")
    suspend fun nextSort(): Int

    @Query(
        """
        UPDATE providers
        SET check_status = :status, check_message = :message, checked_at = :at
        WHERE id = :id
        """,
    )
    suspend fun setCheck(id: String, status: String, message: String?, at: Long)

    @Query("SELECT COUNT(*) FROM providers")
    suspend fun count(): Int
}
