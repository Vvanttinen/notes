package dev.vvanttinen.notes.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.vvanttinen.notes.data.local.entity.LocalNoteEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface LocalNoteDao {
    @Query(
        """
        SELECT * FROM local_notes
        WHERE account_key = :accountKey
            AND deleted_at IS NULL
        ORDER BY updated_at DESC, id ASC
        """
    )
    fun observeActiveNotes(accountKey: String): Flow<List<LocalNoteEntity>>

    @Query(
        """
        SELECT * FROM local_notes
        WHERE account_key = :accountKey
            AND id = :id
            AND deleted_at IS NULL
        """
    )
    fun observeActiveNote(accountKey: String, id: UUID): Flow<LocalNoteEntity?>

    @Query(
        """
        SELECT * FROM local_notes
        WHERE account_key = :accountKey
            AND id = :id
            AND deleted_at IS NULL
        """
    )
    suspend fun loadActiveNote(accountKey: String, id: UUID): LocalNoteEntity?

    @Query(
        """
        SELECT * FROM local_notes
        WHERE account_key = :accountKey
            AND id = :id
        """
    )
    suspend fun loadNote(accountKey: String, id: UUID): LocalNoteEntity?

    @Upsert
    suspend fun upsert(note: LocalNoteEntity)

    @Query(
        """
        UPDATE local_notes
        SET deleted_at = :deletedAt,
            updated_at = :updatedAt,
            local_mutation_version = local_mutation_version + 1,
            sync_state = 'PENDING_DELETE'
        WHERE account_key = :accountKey
            AND id = :id
        """
    )
    suspend fun markDeleted(accountKey: String, id: UUID, deletedAt: Instant, updatedAt: Instant): Int

    @Query(
        """
        SELECT * FROM local_notes
        WHERE account_key = :accountKey
            AND deleted_at IS NULL
            AND (
                title LIKE '%' || :query || '%'
                OR body LIKE '%' || :query || '%'
            )
        ORDER BY updated_at DESC, id ASC
        """
    )
    fun searchActiveNotes(accountKey: String, query: String): Flow<List<LocalNoteEntity>>

    @Query(
        """
        SELECT * FROM local_notes
        WHERE account_key = :accountKey
            AND sync_state != 'SYNCED'
        ORDER BY updated_at ASC, id ASC
        """
    )
    suspend fun listPendingChanges(accountKey: String): List<LocalNoteEntity>

    @Query(
        """
        UPDATE local_notes
        SET server_revision = :serverRevision,
            created_at = :createdAt,
            updated_at = :updatedAt,
            deleted_at = :deletedAt,
            sync_state = 'SYNCED'
        WHERE account_key = :accountKey
            AND id = :id
            AND local_mutation_version = :uploadedLocalMutationVersion
        """
    )
    suspend fun markSynchronized(
        accountKey: String,
        id: UUID,
        uploadedLocalMutationVersion: Long,
        serverRevision: Long,
        createdAt: Instant,
        updatedAt: Instant,
        deletedAt: Instant?
    ): Int

    @Query(
        """
        DELETE FROM local_notes
        WHERE account_key = :accountKey
            AND id = :id
            AND deleted_at IS NOT NULL
            AND sync_state = 'SYNCED'
        """
    )
    suspend fun deleteAcknowledgedTombstone(accountKey: String, id: UUID): Int
}
