package dev.vvanttinen.notes.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.vvanttinen.notes.data.repository.RoomLocalNoteRepository
import dev.vvanttinen.notes.domain.LocalNote
import dev.vvanttinen.notes.domain.LocalNoteRepository
import dev.vvanttinen.notes.domain.NoteSyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit

class LocalNoteDatabaseTest {
    private lateinit var database: NotesDatabase
    private lateinit var repository: RoomLocalNoteRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomLocalNoteRepository(database.localNoteDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertAndReadPreservesUuid() = runBlocking {
        val id = uuid("00000000-0000-0000-0000-000000000001")
        repository.createOrSaveLocalNote(note(id = id))

        val loaded = repository.loadActiveNote(ACCOUNT_A, id)

        assertEquals(id, loaded?.id)
    }

    @Test
    fun updateNoteContent() = runBlocking {
        val id = uuid("00000000-0000-0000-0000-000000000002")
        repository.createOrSaveLocalNote(note(id = id, title = "Original", body = "Old"))
        repository.createOrSaveLocalNote(note(id = id, title = "Updated", body = "New"))

        val loaded = repository.loadActiveNote(ACCOUNT_A, id)

        assertEquals("Updated", loaded?.title)
        assertEquals("New", loaded?.body)
    }

    @Test
    fun activeNotesUseUpdatedDescendingAndUuidAscendingOrder() = runBlocking {
        val older = uuid("00000000-0000-0000-0000-000000000010")
        val tieSecond = uuid("00000000-0000-0000-0000-000000000020")
        val tieFirst = uuid("00000000-0000-0000-0000-000000000011")
        repository.createOrSaveLocalNote(note(id = older, updatedAt = instant(1_000)))
        repository.createOrSaveLocalNote(note(id = tieSecond, updatedAt = instant(2_000)))
        repository.createOrSaveLocalNote(note(id = tieFirst, updatedAt = instant(2_000)))

        val ids = repository.observeActiveNotes(ACCOUNT_A).first().map { it.id }

        assertEquals(listOf(tieFirst, tieSecond, older), ids)
    }

    @Test
    fun searchActiveNotesByTitle() = runBlocking {
        val match = uuid("00000000-0000-0000-0000-000000000030")
        repository.createOrSaveLocalNote(note(id = match, title = "Grocery list"))
        repository.createOrSaveLocalNote(note(id = uuid("00000000-0000-0000-0000-000000000031"), title = "Meeting"))

        val results = repository.searchActiveNotes(ACCOUNT_A, "Grocery").first()

        assertEquals(listOf(match), results.map { it.id })
    }

    @Test
    fun searchActiveNotesByBody() = runBlocking {
        val match = uuid("00000000-0000-0000-0000-000000000040")
        repository.createOrSaveLocalNote(note(id = match, body = "Contains offline persistence details"))
        repository.createOrSaveLocalNote(note(id = uuid("00000000-0000-0000-0000-000000000041"), body = "Other"))

        val results = repository.searchActiveNotes(ACCOUNT_A, "persistence").first()

        assertEquals(listOf(match), results.map { it.id })
    }

    @Test
    fun tombstonedNotesAreExcludedFromActiveReadsAndSearch() = runBlocking {
        val id = uuid("00000000-0000-0000-0000-000000000050")
        repository.createOrSaveLocalNote(note(id = id, title = "Delete me", body = "Find me"))
        repository.tombstoneLocalNote(ACCOUNT_A, id, deletedAt = instant(3_000), updatedAt = instant(3_000))

        assertTrue(repository.observeActiveNotes(ACCOUNT_A).first().isEmpty())
        assertNull(repository.loadActiveNote(ACCOUNT_A, id))
        assertTrue(repository.searchActiveNotes(ACCOUNT_A, "Find").first().isEmpty())
    }

    @Test
    fun pendingChangesIncludeTombstones() = runBlocking {
        val id = uuid("00000000-0000-0000-0000-000000000060")
        repository.createOrSaveLocalNote(note(id = id))
        repository.tombstoneLocalNote(ACCOUNT_A, id, deletedAt = instant(3_000), updatedAt = instant(3_000))

        val pending = repository.listPendingChanges(ACCOUNT_A).single()

        assertEquals(id, pending.id)
        assertEquals(NoteSyncState.PENDING_DELETE, pending.syncState)
        assertEquals(instant(3_000), pending.deletedAt)
    }

    @Test
    fun accountPartitionPreventsCrossAccountReadsMutationsAndCleanup() = runBlocking {
        val id = uuid("00000000-0000-0000-0000-000000000070")
        repository.createOrSaveLocalNote(note(accountKey = ACCOUNT_B, id = id, title = "Private", body = "Only B"))

        assertTrue(repository.observeActiveNotes(ACCOUNT_A).first().isEmpty())
        assertNull(repository.loadActiveNote(ACCOUNT_A, id))
        assertTrue(repository.searchActiveNotes(ACCOUNT_A, "Private").first().isEmpty())
        assertFalse(repository.tombstoneLocalNote(ACCOUNT_A, id, deletedAt = instant(4_000), updatedAt = instant(4_000)))
        assertFalse(repository.cleanUpAcknowledgedTombstone(ACCOUNT_A, id))
        assertEquals(id, repository.loadActiveNote(ACCOUNT_B, id)?.id)
    }

    @Test
    fun neverSynchronizedLocalNoteStoresNullServerRevision() = runBlocking {
        val id = uuid("00000000-0000-0000-0000-000000000080")
        repository.createOrSaveLocalNote(note(id = id, serverRevision = null))

        assertNull(repository.loadActiveNote(ACCOUNT_A, id)?.serverRevision)
    }

    @Test
    fun synchronizedRevisionZeroIsPersistedAsNonNull() = runBlocking {
        val id = uuid("00000000-0000-0000-0000-000000000090")
        repository.createOrSaveLocalNote(note(id = id))
        val uploadedVersion = repository.loadActiveNote(ACCOUNT_A, id)!!.localMutationVersion
        repository.markNoteSynchronized(
            accountKey = ACCOUNT_A,
            id = id,
            uploadedLocalMutationVersion = uploadedVersion,
            serverRevision = 0,
            createdAt = instant(10_000),
            updatedAt = instant(11_000),
            deletedAt = null
        )

        assertEquals(0L, repository.loadActiveNote(ACCOUNT_A, id)?.serverRevision)
    }

    @Test
    fun localCreationOrEditTransitionsToPendingUpsert() = runBlocking {
        val id = uuid("00000000-0000-0000-0000-000000000100")
        repository.createOrSaveLocalNote(note(id = id, syncState = NoteSyncState.SYNCED))

        val loaded = repository.loadActiveNote(ACCOUNT_A, id)
        assertEquals(NoteSyncState.PENDING_UPSERT, loaded?.syncState)
        assertEquals(1L, loaded?.localMutationVersion)
    }

    @Test
    fun localMutationVersionAdvancesForCreateEditAndTombstone() = runBlocking {
        val id = uuid("00000000-0000-0000-0000-000000000101")

        repository.createOrSaveLocalNote(note(id = id, title = "Created", updatedAt = instant(1_000)))
        val created = repository.loadActiveNote(ACCOUNT_A, id)
        assertEquals(1L, created?.localMutationVersion)
        assertEquals(NoteSyncState.PENDING_UPSERT, created?.syncState)

        repository.createOrSaveLocalNote(note(id = id, title = "Edited", updatedAt = instant(2_000)))
        val edited = repository.loadActiveNote(ACCOUNT_A, id)
        assertEquals(2L, edited?.localMutationVersion)
        assertEquals("Edited", edited?.title)
        assertEquals(NoteSyncState.PENDING_UPSERT, edited?.syncState)

        repository.tombstoneLocalNote(ACCOUNT_A, id, deletedAt = instant(3_000), updatedAt = instant(3_000))
        val deleted = repository.listPendingChanges(ACCOUNT_A).single()
        assertEquals(3L, deleted.localMutationVersion)
        assertEquals(instant(3_000), deleted.deletedAt)
        assertEquals(NoteSyncState.PENDING_DELETE, deleted.syncState)
    }

    @Test
    fun concurrentLocalSavesAllocateOneMutationVersionPerSave() = runBlocking {
        val id = uuid("00000000-0000-0000-0000-000000000102")
        val concurrentSaveCount = 8
        repository.createOrSaveLocalNote(note(id = id, title = "Initial", updatedAt = instant(1_000)))

        coroutineScope {
            val startBarrier = CyclicBarrier(concurrentSaveCount)
            (1..concurrentSaveCount).map { index ->
                async(Dispatchers.IO) {
                    startBarrier.await(5, TimeUnit.SECONDS)
                    repository.createOrSaveLocalNote(
                        note(
                            id = id,
                            title = "Edit $index",
                            body = "Body $index",
                            updatedAt = instant(2_000L + index)
                        )
                    )
                }
            }.awaitAll()
        }

        val loaded = repository.loadActiveNote(ACCOUNT_A, id)
        assertEquals(1L + concurrentSaveCount, loaded?.localMutationVersion)
        assertEquals(NoteSyncState.PENDING_UPSERT, loaded?.syncState)
    }

    @Test
    fun tombstoningTransitionsToPendingDelete() = runBlocking {
        val id = uuid("00000000-0000-0000-0000-000000000110")
        repository.createOrSaveLocalNote(note(id = id))
        repository.tombstoneLocalNote(ACCOUNT_A, id, deletedAt = instant(12_000), updatedAt = instant(12_000))

        val pending = repository.listPendingChanges(ACCOUNT_A).single()
        assertEquals(NoteSyncState.PENDING_DELETE, pending.syncState)
        assertEquals(2L, pending.localMutationVersion)
    }

    @Test
    fun synchronizationAcknowledgementTransitionsToSynced() = runBlocking {
        val id = uuid("00000000-0000-0000-0000-000000000120")
        repository.createOrSaveLocalNote(note(id = id))
        val uploadedVersion = repository.loadActiveNote(ACCOUNT_A, id)!!.localMutationVersion
        repository.markNoteSynchronized(
            accountKey = ACCOUNT_A,
            id = id,
            uploadedLocalMutationVersion = uploadedVersion,
            serverRevision = 42,
            createdAt = instant(13_000),
            updatedAt = instant(14_000),
            deletedAt = null
        )

        val loaded = repository.loadActiveNote(ACCOUNT_A, id)
        assertEquals(NoteSyncState.SYNCED, loaded?.syncState)
        assertEquals(42L, loaded?.serverRevision)
        assertEquals(instant(13_000), loaded?.createdAt)
        assertEquals(instant(14_000), loaded?.updatedAt)
        assertEquals(uploadedVersion, loaded?.localMutationVersion)
    }

    @Test
    fun staleSynchronizationAcknowledgementCannotClearNewerPendingLocalEdit() = runBlocking {
        val id = uuid("00000000-0000-0000-0000-000000000121")
        repository.createOrSaveLocalNote(note(id = id, title = "Original", body = "Original body"))
        val firstUploadVersion = repository.loadActiveNote(ACCOUNT_A, id)!!.localMutationVersion
        assertTrue(
            repository.markNoteSynchronized(
                accountKey = ACCOUNT_A,
                id = id,
                uploadedLocalMutationVersion = firstUploadVersion,
                serverRevision = 1,
                createdAt = instant(13_000),
                updatedAt = instant(13_000),
                deletedAt = null
            )
        )

        repository.createOrSaveLocalNote(
            note(
                id = id,
                title = "Newer edit",
                body = "Newer body",
                createdAt = instant(13_000),
                updatedAt = instant(15_000),
                serverRevision = 1,
                syncState = NoteSyncState.SYNCED
            )
        )

        assertFalse(
            repository.markNoteSynchronized(
                accountKey = ACCOUNT_A,
                id = id,
                uploadedLocalMutationVersion = firstUploadVersion,
                serverRevision = 2,
                createdAt = instant(13_000),
                updatedAt = instant(14_000),
                deletedAt = null
            )
        )

        val loaded = repository.loadActiveNote(ACCOUNT_A, id)
        assertEquals("Newer edit", loaded?.title)
        assertEquals("Newer body", loaded?.body)
        assertEquals(NoteSyncState.PENDING_UPSERT, loaded?.syncState)
        assertEquals(1L, loaded?.serverRevision)
        assertEquals(firstUploadVersion + 1, loaded?.localMutationVersion)
    }

    @Test
    fun staleSynchronizationAcknowledgementCannotClearNewerPendingTombstone() = runBlocking {
        val id = uuid("00000000-0000-0000-0000-000000000122")
        repository.createOrSaveLocalNote(note(id = id, title = "Original", body = "Original body"))
        val uploadedUpsertVersion = repository.loadActiveNote(ACCOUNT_A, id)!!.localMutationVersion

        repository.tombstoneLocalNote(ACCOUNT_A, id, deletedAt = instant(16_000), updatedAt = instant(16_000))

        assertFalse(
            repository.markNoteSynchronized(
                accountKey = ACCOUNT_A,
                id = id,
                uploadedLocalMutationVersion = uploadedUpsertVersion,
                serverRevision = 2,
                createdAt = instant(13_000),
                updatedAt = instant(14_000),
                deletedAt = null
            )
        )

        val pending = repository.listPendingChanges(ACCOUNT_A).single()
        assertEquals(id, pending.id)
        assertEquals("Original", pending.title)
        assertEquals("Original body", pending.body)
        assertEquals(instant(16_000), pending.updatedAt)
        assertEquals(instant(16_000), pending.deletedAt)
        assertNull(pending.serverRevision)
        assertEquals(uploadedUpsertVersion + 1, pending.localMutationVersion)
        assertEquals(NoteSyncState.PENDING_DELETE, pending.syncState)
    }

    @Test
    fun wrongAccountSynchronizationAcknowledgementIsRejected() = runBlocking {
        val id = uuid("00000000-0000-0000-0000-000000000123")
        repository.createOrSaveLocalNote(note(accountKey = ACCOUNT_B, id = id, title = "Private", body = "Only B"))
        val accountBNote = repository.loadActiveNote(ACCOUNT_B, id)!!

        assertFalse(
            repository.markNoteSynchronized(
                accountKey = ACCOUNT_A,
                id = id,
                uploadedLocalMutationVersion = accountBNote.localMutationVersion,
                serverRevision = 7,
                createdAt = instant(20_000),
                updatedAt = instant(21_000),
                deletedAt = null
            )
        )

        val unchanged = repository.loadActiveNote(ACCOUNT_B, id)
        assertEquals("Private", unchanged?.title)
        assertEquals("Only B", unchanged?.body)
        assertNull(unchanged?.serverRevision)
        assertEquals(accountBNote.localMutationVersion, unchanged?.localMutationVersion)
        assertEquals(NoteSyncState.PENDING_UPSERT, unchanged?.syncState)
    }

    @Test
    fun acknowledgedTombstoneIsRemovedOnlyByExplicitCleanup() = runBlocking {
        val id = uuid("00000000-0000-0000-0000-000000000130")
        repository.createOrSaveLocalNote(note(id = id))
        repository.tombstoneLocalNote(ACCOUNT_A, id, deletedAt = instant(15_000), updatedAt = instant(15_000))
        val uploadedVersion = repository.listPendingChanges(ACCOUNT_A).single().localMutationVersion
        repository.markNoteSynchronized(
            accountKey = ACCOUNT_A,
            id = id,
            uploadedLocalMutationVersion = uploadedVersion,
            serverRevision = 43,
            createdAt = instant(13_000),
            updatedAt = instant(15_000),
            deletedAt = instant(15_000)
        )

        assertTrue(repository.listPendingChanges(ACCOUNT_A).isEmpty())
        assertTrue(repository.cleanUpAcknowledgedTombstone(ACCOUNT_A, id))
        assertFalse(repository.cleanUpAcknowledgedTombstone(ACCOUNT_A, id))
    }

    @Test
    fun cleanupDoesNotRemoveActiveNotes() = runBlocking {
        val id = uuid("00000000-0000-0000-0000-000000000140")
        repository.createOrSaveLocalNote(note(id = id))
        val uploadedVersion = repository.loadActiveNote(ACCOUNT_A, id)!!.localMutationVersion
        repository.markNoteSynchronized(
            accountKey = ACCOUNT_A,
            id = id,
            uploadedLocalMutationVersion = uploadedVersion,
            serverRevision = 44,
            createdAt = instant(16_000),
            updatedAt = instant(16_000),
            deletedAt = null
        )

        assertFalse(repository.cleanUpAcknowledgedTombstone(ACCOUNT_A, id))
        assertEquals(id, repository.loadActiveNote(ACCOUNT_A, id)?.id)
    }

    @Test
    fun titleAtBackendLimitIsAccepted() = runBlocking {
        val id = uuid("00000000-0000-0000-0000-000000000150")
        val title = "x".repeat(LocalNoteRepository.MAX_TITLE_LENGTH)

        repository.createOrSaveLocalNote(note(id = id, title = title))

        assertEquals(title, repository.loadActiveNote(ACCOUNT_A, id)?.title)
    }

    @Test
    fun titleBeyondBackendLimitIsRejected() = runBlocking {
        val id = uuid("00000000-0000-0000-0000-000000000151")
        val title = "x".repeat(LocalNoteRepository.MAX_TITLE_LENGTH + 1)

        val failure = runCatching {
            repository.createOrSaveLocalNote(note(id = id, title = title))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(
            "Note title must be ${LocalNoteRepository.MAX_TITLE_LENGTH} characters or fewer.",
            failure?.message
        )
        assertNull(repository.loadActiveNote(ACCOUNT_A, id))
    }

    @Test
    fun typeConvertersRoundTripRepresentativeValues() {
        val converters = NotesTypeConverters()
        val id = uuid("11111111-2222-3333-4444-555555555555")
        val timestamp = instant(123_456)

        assertEquals(id, converters.stringToUuid(converters.uuidToString(id)))
        assertEquals(timestamp, converters.epochMillisToInstant(converters.instantToEpochMillis(timestamp)))
        assertNull(converters.epochMillisToInstant(converters.instantToEpochMillis(null)))
        assertEquals(
            NoteSyncState.PENDING_DELETE,
            converters.stringToSyncState(converters.syncStateToString(NoteSyncState.PENDING_DELETE))
        )
    }

    private fun note(
        accountKey: String = ACCOUNT_A,
        id: UUID = uuid("00000000-0000-0000-0000-000000000001"),
        title: String = "Title",
        body: String = "Body",
        createdAt: Instant = instant(1_000),
        updatedAt: Instant = instant(1_000),
        deletedAt: Instant? = null,
        serverRevision: Long? = null,
        localMutationVersion: Long = 0,
        syncState: NoteSyncState = NoteSyncState.PENDING_UPSERT
    ): LocalNote = LocalNote(
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

    private fun uuid(value: String): UUID = UUID.fromString(value)

    private fun instant(epochMillis: Long): Instant = Instant.ofEpochMilli(epochMillis)

    companion object {
        private const val ACCOUNT_A = "opaque-account-a"
        private const val ACCOUNT_B = "opaque-account-b"
    }
}
