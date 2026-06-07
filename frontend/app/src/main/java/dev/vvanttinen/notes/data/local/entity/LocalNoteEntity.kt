package dev.vvanttinen.notes.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import dev.vvanttinen.notes.domain.LocalNote
import dev.vvanttinen.notes.domain.NoteSyncState
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "local_notes",
    primaryKeys = ["account_key", "id"],
    indices = [
        Index(
            name = "index_local_notes_account_active_order",
            value = ["account_key", "deleted_at", "updated_at", "id"],
            orders = [Index.Order.ASC, Index.Order.ASC, Index.Order.DESC, Index.Order.ASC]
        ),
        Index(
            name = "index_local_notes_account_pending_order",
            value = ["account_key", "updated_at", "id"],
            orders = [Index.Order.ASC, Index.Order.ASC, Index.Order.ASC]
        )
    ]
)
data class LocalNoteEntity(
    @ColumnInfo(name = "account_key")
    val accountKey: String,
    val id: UUID,
    val title: String,
    val body: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Instant?,
    @ColumnInfo(name = "server_revision")
    val serverRevision: Long?,
    @ColumnInfo(name = "local_mutation_version")
    val localMutationVersion: Long,
    @ColumnInfo(name = "sync_state")
    val syncState: NoteSyncState
) {
    fun toDomain(): LocalNote = LocalNote(
        accountKey = accountKey,
        id = id,
        title = title,
        body = body,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        serverRevision = serverRevision,
        localMutationVersion = localMutationVersion,
        syncState = syncState
    )
}

fun LocalNote.toEntity(): LocalNoteEntity = LocalNoteEntity(
    accountKey = accountKey,
    id = id,
    title = title,
    body = body,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    serverRevision = serverRevision,
    localMutationVersion = localMutationVersion,
    syncState = syncState
)
