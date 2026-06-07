package dev.vvanttinen.notes.data.repository

import dev.vvanttinen.notes.data.local.dao.LocalNoteDao
import dev.vvanttinen.notes.data.local.entity.toEntity
import dev.vvanttinen.notes.domain.LocalNote
import dev.vvanttinen.notes.domain.LocalNoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

class RoomLocalNoteRepository(
    private val localNoteDao: LocalNoteDao
) : LocalNoteRepository {
    override fun observeActiveNotes(accountKey: String): Flow<List<LocalNote>> =
        localNoteDao.observeActiveNotes(accountKey).map { notes -> notes.map { it.toDomain() } }

    override suspend fun loadActiveNote(accountKey: String, id: UUID): LocalNote? =
        localNoteDao.loadActiveNote(accountKey, id)?.toDomain()

    override suspend fun createOrSaveLocalNote(note: LocalNote) {
        require(note.title.length <= LocalNoteRepository.MAX_TITLE_LENGTH) {
            "Note title must be ${LocalNoteRepository.MAX_TITLE_LENGTH} characters or fewer."
        }

        localNoteDao.createOrSaveLocalMutation(note.toEntity())
    }

    override suspend fun tombstoneLocalNote(
        accountKey: String,
        id: UUID,
        deletedAt: Instant,
        updatedAt: Instant
    ): Boolean = localNoteDao.markDeleted(accountKey, id, deletedAt, updatedAt) > 0

    override fun searchActiveNotes(accountKey: String, query: String): Flow<List<LocalNote>> =
        localNoteDao.searchActiveNotes(accountKey, query).map { notes -> notes.map { it.toDomain() } }

    override suspend fun listPendingChanges(accountKey: String): List<LocalNote> =
        localNoteDao.listPendingChanges(accountKey).map { it.toDomain() }

    override suspend fun markNoteSynchronized(
        accountKey: String,
        id: UUID,
        uploadedLocalMutationVersion: Long,
        serverRevision: Long,
        createdAt: Instant,
        updatedAt: Instant,
        deletedAt: Instant?
    ): Boolean = localNoteDao.markSynchronized(
        accountKey = accountKey,
        id = id,
        uploadedLocalMutationVersion = uploadedLocalMutationVersion,
        serverRevision = serverRevision,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    ) > 0

    override suspend fun cleanUpAcknowledgedTombstone(accountKey: String, id: UUID): Boolean =
        localNoteDao.deleteAcknowledgedTombstone(accountKey, id) > 0
}
