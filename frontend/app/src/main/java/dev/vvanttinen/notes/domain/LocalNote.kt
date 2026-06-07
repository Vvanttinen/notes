package dev.vvanttinen.notes.domain

import java.time.Instant
import java.util.UUID

data class LocalNote(
    val accountKey: String,
    val id: UUID,
    val title: String,
    val body: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val serverRevision: Long?,
    val localMutationVersion: Long,
    val syncState: NoteSyncState
) {
    companion object {
        fun createNew(
            accountKey: String,
            title: String,
            body: String,
            now: Instant,
            id: UUID = UUID.randomUUID()
        ): LocalNote = LocalNote(
            accountKey = accountKey,
            id = id,
            title = title,
            body = body,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            serverRevision = null,
            localMutationVersion = 0,
            syncState = NoteSyncState.PENDING_UPSERT
        )
    }
}
