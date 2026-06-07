package dev.vvanttinen.notes.data.local

import androidx.room.TypeConverter
import dev.vvanttinen.notes.domain.NoteSyncState
import java.time.Instant
import java.util.UUID

class NotesTypeConverters {
    @TypeConverter
    fun uuidToString(value: UUID?): String? = value?.toString()

    @TypeConverter
    fun stringToUuid(value: String?): UUID? = value?.let(UUID::fromString)

    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun syncStateToString(value: NoteSyncState?): String? = value?.name

    @TypeConverter
    fun stringToSyncState(value: String?): NoteSyncState? = value?.let(NoteSyncState::valueOf)
}
