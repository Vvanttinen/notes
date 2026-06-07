package dev.vvanttinen.notes.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

interface LocalNoteRepository {
    fun observeActiveNotes(accountKey: String): Flow<List<LocalNote>>

    suspend fun loadActiveNote(accountKey: String, id: UUID): LocalNote?

    suspend fun createOrSaveLocalNote(note: LocalNote)

    suspend fun tombstoneLocalNote(
        accountKey: String,
        id: UUID,
        deletedAt: Instant,
        updatedAt: Instant
    ): Boolean

    fun searchActiveNotes(accountKey: String, query: String): Flow<List<LocalNote>>

    suspend fun listPendingChanges(accountKey: String): List<LocalNote>

    suspend fun markNoteSynchronized(
        accountKey: String,
        id: UUID,
        uploadedLocalMutationVersion: Long,
        serverRevision: Long,
        createdAt: Instant,
        updatedAt: Instant,
        deletedAt: Instant?
    ): Boolean

    suspend fun cleanUpAcknowledgedTombstone(accountKey: String, id: UUID): Boolean

    companion object {
        const val MAX_TITLE_LENGTH = 255
    }
}
