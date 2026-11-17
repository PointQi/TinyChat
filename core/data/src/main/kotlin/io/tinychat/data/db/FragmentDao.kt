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

    @Query("UPDATE fragments SET type = :type, payload = :payload WHERE id = :id")
    suspend fun updateTypeAndPayload(id: Long, type: String, payload: String)

    /** 流式落库用：按 (message_id, ord) 找已有行 */
    @Query("SELECT id FROM fragments WHERE message_id = :messageId AND ord = :ord")
    suspend fun idAt(messageId: Long, ord: Int): Long?

    /** 拒绝/失败时按类型清理，例如拒答要丢掉已下发的思考与正文 */
    @Query("DELETE FROM fragments WHERE message_id = :messageId AND type IN (:types)")
    suspend fun deleteByTypes(messageId: Long, types: List<String>)

    /** 下一个可用 ord；空消息返回 0 */
    @Query("SELECT COALESCE(MAX(ord), -1) + 1 FROM fragments WHERE message_id = :messageId")
    suspend fun nextOrd(messageId: Long): Int

    @Query("DELETE FROM fragments WHERE message_id = :messageId")
    suspend fun deleteByMessage(messageId: Long)

    /**
     * 会话搜索（M1-6b）：在用户提问与 AI 正文里找 `query`，按消息时间倒序。
     *
     * 直接 `LIKE` 原始 payload：碎片里就一份 JSON 字符串，正则解析在 SQL 里做不了；
     * 片段精修（去掉 JSON 噪音、截取上下文）在仓库层用 [io.tinychat.model.buildSearchHit] 完成。
     */
    @Query(
        """
        SELECT m.session_id AS session_id, s.title AS title, f.payload AS payload, m.inserted_at AS inserted_at
        FROM fragments f
        JOIN messages m ON m.id = f.message_id
        JOIN sessions s ON s.id = m.session_id
        WHERE f.type IN ('REQUEST', 'RESPONSE')
          AND s.deleted = 0
          AND f.payload LIKE '%' || :query || '%'
        ORDER BY m.inserted_at DESC
        """,
    )
    suspend fun searchPayloads(query: String): List<SearchPayloadRow>
}
