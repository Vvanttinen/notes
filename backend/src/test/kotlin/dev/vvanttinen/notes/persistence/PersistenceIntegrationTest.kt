package dev.vvanttinen.notes.persistence

import dev.vvanttinen.notes.entity.NoteEntity
import dev.vvanttinen.notes.entity.UserEntity
import dev.vvanttinen.notes.repository.NoteRepository
import dev.vvanttinen.notes.repository.UserRepository
import dev.vvanttinen.notes.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistenceIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var noteRepository: NoteRepository

    @Test
    fun `persist and resolve a user by Entra identity`() {
        val user = userRepository.saveAndFlush(
            user(
                id = uuid("00000000-0000-0000-0000-000000000001"),
                tenantId = uuid("10000000-0000-0000-0000-000000000001"),
                objectId = uuid("20000000-0000-0000-0000-000000000001"),
            ),
        )

        val resolved = userRepository.findByEntraTenantIdAndEntraObjectId(
            user.entraTenantId,
            user.entraObjectId,
        )

        assertEquals(user.id, resolved?.id)
    }

    @Test
    fun `reject a duplicate Entra identity pair`() {
        val tenantId = uuid("10000000-0000-0000-0000-000000000002")
        val objectId = uuid("20000000-0000-0000-0000-000000000002")

        userRepository.saveAndFlush(
            user(
                id = uuid("00000000-0000-0000-0000-000000000002"),
                tenantId = tenantId,
                objectId = objectId,
            ),
        )

        assertFailsWith<DataIntegrityViolationException> {
            userRepository.saveAndFlush(
                user(
                    id = uuid("00000000-0000-0000-0000-000000000003"),
                    tenantId = tenantId,
                    objectId = objectId,
                ),
            )
        }
    }

    @Test
    fun `persist and resolve a note for its owner`() {
        val firstOwner = userRepository.saveAndFlush(user(id = uuid("00000000-0000-0000-0000-000000000004")))
        val secondOwner = userRepository.saveAndFlush(user(id = uuid("00000000-0000-0000-0000-000000000005")))
        val note = noteRepository.saveAndFlush(
            note(
                id = uuid("30000000-0000-0000-0000-000000000001"),
                owner = firstOwner,
            ),
        )

        assertEquals(note.id, noteRepository.findByIdAndOwner_Id(note.id, firstOwner.id)?.id)
        assertNull(noteRepository.findByIdAndOwner_Id(note.id, secondOwner.id))
    }

    @Test
    fun `list only active notes for an owner`() {
        val owner = userRepository.saveAndFlush(user(id = uuid("00000000-0000-0000-0000-000000000006")))
        val otherOwner = userRepository.saveAndFlush(user(id = uuid("00000000-0000-0000-0000-000000000007")))
        val activeFirst = noteRepository.saveAndFlush(
            note(
                id = uuid("30000000-0000-0000-0000-000000000002"),
                owner = owner,
                title = "Active first",
            ),
        )
        val activeSecond = noteRepository.saveAndFlush(
            note(
                id = uuid("30000000-0000-0000-0000-000000000003"),
                owner = owner,
                title = "Active second",
            ),
        )
        noteRepository.saveAndFlush(
            note(
                id = uuid("30000000-0000-0000-0000-000000000004"),
                owner = owner,
                title = "Deleted",
                deletedAt = Instant.parse("2026-01-01T00:00:00Z"),
            ),
        )
        noteRepository.saveAndFlush(
            note(
                id = uuid("30000000-0000-0000-0000-000000000005"),
                owner = otherOwner,
                title = "Other owner",
            ),
        )

        val activeNoteIds = noteRepository
            .findByOwner_IdAndDeletedAtIsNullOrderByUpdatedAtDescIdAsc(owner.id)
            .map { it.id }
            .toSet()

        assertEquals(setOf(activeFirst.id, activeSecond.id), activeNoteIds)
    }

    @Test
    fun `order active notes consistently`() {
        val owner = userRepository.saveAndFlush(user(id = uuid("00000000-0000-0000-0000-000000000008")))
        val olderId = uuid("30000000-0000-0000-0000-000000000006")
        val newerFirstId = uuid("30000000-0000-0000-0000-000000000007")
        val newerSecondId = uuid("30000000-0000-0000-0000-000000000008")
        noteRepository.saveAndFlush(note(id = olderId, owner = owner, title = "Older"))
        noteRepository.saveAndFlush(note(id = newerSecondId, owner = owner, title = "Newer second by id"))
        noteRepository.saveAndFlush(note(id = newerFirstId, owner = owner, title = "Newer first by id"))

        setUpdatedAt(olderId, Instant.parse("2026-01-01T00:00:00Z"))
        setUpdatedAt(newerFirstId, Instant.parse("2026-01-02T00:00:00Z"))
        setUpdatedAt(newerSecondId, Instant.parse("2026-01-02T00:00:00Z"))

        val orderedIds = noteRepository
            .findByOwner_IdAndDeletedAtIsNullOrderByUpdatedAtDescIdAsc(owner.id)
            .map { it.id }

        assertEquals(listOf(newerFirstId, newerSecondId, olderId), orderedIds)
    }

    @Test
    fun `increment the optimistic-lock revision on update`() {
        val owner = userRepository.saveAndFlush(user(id = uuid("00000000-0000-0000-0000-000000000009")))
        val note = noteRepository.saveAndFlush(
            note(
                id = uuid("30000000-0000-0000-0000-000000000009"),
                owner = owner,
                title = "Original",
            ),
        )
        val initialRevision = assertNotNull(note.revision)

        note.title = "Updated"
        val updated = noteRepository.saveAndFlush(note)

        assertTrue(assertNotNull(updated.revision) > initialRevision)
    }

    private fun user(
        id: UUID,
        tenantId: UUID = uuid("10000000-0000-0000-0000-000000000000"),
        objectId: UUID = uuid("20000000-0000-0000-0000-${id.toString().takeLast(12)}"),
    ) = UserEntity(
        id = id,
        entraTenantId = tenantId,
        entraObjectId = objectId,
    )

    private fun note(
        id: UUID,
        owner: UserEntity,
        title: String = "Note",
        body: String = "Body",
        deletedAt: Instant? = null,
    ) = NoteEntity(
        id = id,
        owner = owner,
        title = title,
        body = body,
        deletedAt = deletedAt,
    )

    private fun setUpdatedAt(id: UUID, updatedAt: Instant) {
        jdbcTemplate.update(
            "UPDATE notes SET updated_at = ? WHERE id = ?",
            Timestamp.from(updatedAt),
            id,
        )
    }

    private fun uuid(value: String): UUID = UUID.fromString(value)
}
