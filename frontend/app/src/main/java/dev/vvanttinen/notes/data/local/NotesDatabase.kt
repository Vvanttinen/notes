package dev.vvanttinen.notes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.vvanttinen.notes.data.local.dao.LocalNoteDao
import dev.vvanttinen.notes.data.local.entity.LocalNoteEntity

@Database(
    entities = [LocalNoteEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(NotesTypeConverters::class)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun localNoteDao(): LocalNoteDao

    companion object {
        const val DATABASE_NAME = "notes.db"
    }
}
