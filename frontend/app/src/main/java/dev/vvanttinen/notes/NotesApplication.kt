package dev.vvanttinen.notes

import android.app.Application
import androidx.room.Room
import dev.vvanttinen.notes.auth.DefaultNotesAuthController
import dev.vvanttinen.notes.auth.EntraAuthConfig
import dev.vvanttinen.notes.auth.MsalSingleAccountGateway
import dev.vvanttinen.notes.auth.NotesAuthController
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

    val authController: NotesAuthController by lazy {
        DefaultNotesAuthController(
            config = EntraAuthConfig.fromValues(
                isConfigured = BuildConfig.NOTES_ENTRA_CONFIGURED,
                notesApiScope = BuildConfig.NOTES_ENTRA_API_SCOPE
            ),
            gateway = MsalSingleAccountGateway(
                applicationContext = applicationContext,
                configResourceId = R.raw.msal_auth_config
            )
        )
    }
}
