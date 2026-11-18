package io.tinychat.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.tinychat.model.MessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert
    suspend fun insert(entity: MessageEntity): Long

    @Insert
    suspend fun insertAll(entities: List<MessageEntity>): List<Long>

    @Update
    suspend fun update(entity: MessageEntity)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun byId(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY inserted_at ASC, id ASC")
    suspend fun allInSession(sessionId: String): List<MessageEntity>

    /** 会话页主查询：消息 + 碎片，流式过程中随库里变化自动刷新 */
    @Transaction
    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY inserted_at ASC, id ASC")
    fun observeWithFragments(sessionId: String): Flow<List<MessageWithFragments>>

    @Transaction
    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY inserted_at ASC, id ASC")
    suspend fun withFragments(sessionId: String): List<MessageWithFragments>

    /**
     * 某个节点下的子节点；同一 USER 下的多个 ASSISTANT 即「重新生成」产生的兄弟。
     * `IS` 而非 `=`：根节点（parent_id 为 null）也要能查。
     */
    @Query("SELECT * FROM messages WHERE parent_id IS :parentId ORDER BY inserted_at ASC, id ASC")
    suspend fun children(parentId: Long?): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId AND status = 'WIP'")
    suspend fun wipInSession(sessionId: String): List<MessageEntity>

    /** 进程被杀后的自愈：所有残留 WIP 归为 INCOMPLETE（02 §4.6） */
    @Query("UPDATE messages SET status = 'INCOMPLETE' WHERE status = 'WIP'")
    suspend fun recoverWipAll(): Int

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: MessageStatus)

    /** 思考块签名（仅本地，不导出）：续写本轮思考时要原样回传 */
    @Query("UPDATE messages SET thinking_signature = :signature WHERE id = :id")
    suspend fun setThinkingSignature(id: Long, signature: String)

    @Query("SELECT COALESCE(MAX(id), 0) FROM messages WHERE session_id = :sessionId")
    suspend fun maxIdInSession(sessionId: String): Long

    /** 级联删除该消息及其碎片、附件 */
    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM messages WHERE session_id = :sessionId")
    suspend fun countInSession(sessionId: String): Int
}
