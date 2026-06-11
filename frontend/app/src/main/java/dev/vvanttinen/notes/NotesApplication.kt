package dev.vvanttinen.notes

import android.app.Application
import androidx.room.Room
import dev.vvanttinen.notes.auth.AuthFeature
import dev.vvanttinen.notes.data.local.NotesDatabase
import dev.vvanttinen.notes.data.repository.RoomLocalNoteRepository
import dev.vvanttinen.notes.domain.LocalNoteRepository

class NotesApplication : Application() {
    val database: NotesDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            NotesDatabase::class.java,
            NotesDatabase.DATABASE_NAME
        ).build()
    }

    val localNoteRepository: LocalNoteRepository by lazy {
        RoomLocalNoteRepository(database.localNoteDao())
    }

    val authFeature: AuthFeature by lazy {
        AuthFeature.create(applicationContext)
    }
}
