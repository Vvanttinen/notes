package dev.vvanttinen.notes.domain

enum class NoteSyncState {
    SYNCED,
    PENDING_UPSERT,
    PENDING_DELETE
}
