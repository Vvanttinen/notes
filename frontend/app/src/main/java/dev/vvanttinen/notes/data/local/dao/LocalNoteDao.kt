package dev.vvanttinen.notes.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.vvanttinen.notes.data.local.entity.LocalNoteEntity
import dev.vvanttinen.notes.domain.NoteSyncState
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
abstract class LocalNoteDao {
    @Query(
        """
        SELECT * FROM local_notes
        WHERE account_key = :accountKey
            AND deleted_at IS NULL
        ORDER BY updated_at DESC, id ASC
        """
    )
    abstract fun observeActiveNotes(accountKey: String): Flow<List<LocalNoteEntity>>

    @Query(
        """
        SELECT * FROM local_notes
        WHERE account_key = :accountKey
            AND id = :id
            AND deleted_at IS NULL
        """
    )
    abstract fun observeActiveNote(accountKey: String, id: UUID): Flow<LocalNoteEntity?>

    @Query(
        """
        SELECT * FROM local_notes
        WHERE account_key = :accountKey
            AND id = :id
            AND deleted_at IS NULL
        """
    )
    abstract suspend fun loadActiveNote(accountKey: String, id: UUID): LocalNoteEntity?

    @Query(
        """
        SELECT * FROM local_notes
        WHERE account_key = :accountKey
            AND id = :id
        """
    )
    abstract suspend fun loadNote(accountKey: String, id: UUID): LocalNoteEntity?

    @Upsert
    abstract suspend fun upsert(note: LocalNoteEntity)

    @Transaction
    open suspend fun createOrSaveLocalMutation(note: LocalNoteEntity) {
        val currentVersion = loadNote(note.accountKey, note.id)?.localMutationVersion ?: 0
        upsert(
            note.copy(
                deletedAt = null,
                localMutationVersion = currentVersion + 1,
                syncState = NoteSyncState.PENDING_UPSERT
            )
        )
    }

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
    abstract suspend fun markDeleted(accountKey: String, id: UUID, deletedAt: Instant, updatedAt: Instant): Int

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
    abstract fun searchActiveNotes(accountKey: String, query: String): Flow<List<LocalNoteEntity>>

    @Query(
        """
        SELECT * FROM local_notes
        WHERE account_key = :accountKey
            AND sync_state != 'SYNCED'
        ORDER BY updated_at ASC, id ASC
        """
    )
    abstract suspend fun listPendingChanges(accountKey: String): List<LocalNoteEntity>

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
    abstract suspend fun markSynchronized(
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
    abstract suspend fun deleteAcknowledgedTombstone(accountKey: String, id: UUID): Int
}
