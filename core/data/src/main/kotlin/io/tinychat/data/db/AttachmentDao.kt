package io.tinychat.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.tinychat.model.AttachmentState

@Dao
interface AttachmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AttachmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AttachmentEntity)

    /** 顺序跟随用户选择顺序（文件在前、图片在后就是按这个来） */
    @Query("SELECT * FROM attachments WHERE message_id = :messageId ORDER BY rowid")
    suspend fun byMessage(messageId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun byId(id: String): AttachmentEntity?

    @Query("UPDATE attachments SET state = :state WHERE id = :id")
    suspend fun setState(id: String, state: AttachmentState)

    @Query("UPDATE attachments SET local_path = :localPath, state = :state WHERE id = :id")
    suspend fun setLocal(id: String, localPath: String?, state: AttachmentState)

    /** 会话里所有附件（删会话时清文件用） */
    @Query(
        "SELECT attachments.* FROM attachments INNER JOIN messages ON messages.id = attachments.message_id " +
            "WHERE messages.session_id = :sessionId",
    )
    suspend fun bySession(sessionId: String): List<AttachmentEntity>

    @Query("UPDATE attachments SET remote_id = :remoteId, state = :state WHERE id = :id")
    suspend fun setRemote(id: String, remoteId: String?, state: AttachmentState)

    @Query("DELETE FROM attachments WHERE message_id = :messageId")
    suspend fun deleteByMessage(messageId: Long)
}
