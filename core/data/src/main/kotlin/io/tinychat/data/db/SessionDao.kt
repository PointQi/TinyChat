package io.tinychat.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun byId(id: String): SessionEntity?

    /** 抽屉列表：置顶优先，其次按最后活跃倒序 */
    @Query("SELECT * FROM sessions WHERE deleted = 0 ORDER BY pinned DESC, updated_at DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE deleted = 0 ORDER BY pinned DESC, updated_at DESC")
    suspend fun all(): List<SessionEntity>

    /** 软删；附件由 M6 的异步清理任务处理 */
    @Query("UPDATE sessions SET deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("UPDATE sessions SET title = :title, updated_at = :now WHERE id = :id")
    suspend fun rename(id: String, title: String?, now: Long)

    @Query("UPDATE sessions SET pinned = :pinned, updated_at = :now WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, now: Long)

    @Query("UPDATE sessions SET current_message_id = :messageId, updated_at = :now WHERE id = :id")
    suspend fun setCurrentMessage(id: String, messageId: Long?, now: Long)

    @Query("UPDATE sessions SET updated_at = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)

    @Query("SELECT COUNT(*) FROM sessions WHERE deleted = 0")
    suspend fun count(): Int
}
