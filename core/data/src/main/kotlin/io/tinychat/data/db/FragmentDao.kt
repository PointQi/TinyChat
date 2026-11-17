package io.tinychat.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FragmentDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: FragmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<FragmentEntity>): List<Long>

    @Query("SELECT * FROM fragments WHERE message_id = :messageId ORDER BY ord ASC")
    suspend fun byMessage(messageId: Long): List<FragmentEntity>

    @Query("SELECT * FROM fragments WHERE message_id IN (:messageIds) ORDER BY message_id ASC, ord ASC")
    suspend fun byMessages(messageIds: List<Long>): List<FragmentEntity>

    /** 流式追加：同一碎片原地更新 payload，避免高频插入 */
    @Query("UPDATE fragments SET payload = :payload WHERE id = :id")
    suspend fun updatePayload(id: Long, payload: String)

    /** 下一个可用 ord；空消息返回 0 */
    @Query("SELECT COALESCE(MAX(ord), -1) + 1 FROM fragments WHERE message_id = :messageId")
    suspend fun nextOrd(messageId: Long): Int

    @Query("DELETE FROM fragments WHERE message_id = :messageId")
    suspend fun deleteByMessage(messageId: Long)
}
