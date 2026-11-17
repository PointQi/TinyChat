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

    @Query("SELECT * FROM attachments WHERE message_id = :messageId")
    suspend fun byMessage(messageId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun byId(id: String): AttachmentEntity?

    @Query("UPDATE attachments SET local_path = :localPath, state = :state WHERE id = :id")
    suspend fun setLocal(id: String, localPath: String?, state: AttachmentState)

    @Query("UPDATE attachments SET remote_id = :remoteId, state = :state WHERE id = :id")
    suspend fun setRemote(id: String, remoteId: String?, state: AttachmentState)

    @Query("DELETE FROM attachments WHERE message_id = :messageId")
    suspend fun deleteByMessage(messageId: Long)
}
