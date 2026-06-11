package dev.vvanttinen.notes.note

import dev.vvanttinen.notes.entity.NoteEntity
import dev.vvanttinen.notes.repository.NoteRepository
import dev.vvanttinen.notes.user.CurrentUser
import jakarta.persistence.OptimisticLockException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class NoteService(
    private val currentUser: CurrentUser,
    private val noteRepository: NoteRepository,
) {
    @Transactional
    fun create(
        id: UUID,
        title: String,
        body: String,
    ): NoteRecord {
        val owner = currentUser.resolve()
        val note = NoteEntity(
            id = id,
            owner = owner,
            title = title,
            body = body,
        )

        return try {
            noteRepository.saveAndFlush(note).toRecord()
        } catch (exception: DataIntegrityViolationException) {
            throw DuplicateNoteIdException()
        }
    }

    @Transactional
    fun list(): List<NoteRecord> {
        val owner = currentUser.resolve()
        return noteRepository
            .findByOwner_IdAndDeletedAtIsNullOrderByUpdatedAtDescIdAsc(owner.id)
            .map(NoteEntity::toRecord)
    }

    @Transactional
    fun get(noteId: UUID): NoteRecord =
        findActiveOwned(noteId).toRecord()

    @Transactional
    fun update(
        noteId: UUID,
        expectedRevision: Long,
        title: String,
        body: String,
    ): NoteRecord {
        val note = findActiveOwned(noteId)
        requireCurrentRevision(note, expectedRevision)

        note.title = title
        note.body = body
        flushWithOptimisticLockTranslation()
        return note.toRecord()
    }

    @Transactional
    fun delete(
        noteId: UUID,
        expectedRevision: Long,
    ) {
        val note = findActiveOwned(noteId)
        requireCurrentRevision(note, expectedRevision)

        note.deletedAt = Instant.now()
        flushWithOptimisticLockTranslation()
    }

    private fun findActiveOwned(noteId: UUID): NoteEntity {
        val owner = currentUser.resolve()
        return noteRepository.findByIdAndOwner_IdAndDeletedAtIsNull(noteId, owner.id)
            ?: throw NoteNotFoundException()
    }

    private fun requireCurrentRevision(
        note: NoteEntity,
        expectedRevision: Long,
    ) {
        if (note.revision != expectedRevision) {
            throw StaleNoteRevisionException()
        }
    }

    private fun flushWithOptimisticLockTranslation() {
        try {
            noteRepository.flush()
        } catch (exception: ObjectOptimisticLockingFailureException) {
            throw StaleNoteRevisionException()
        } catch (exception: OptimisticLockException) {
            throw StaleNoteRevisionException()
        }
    }
}

data class NoteRecord(
    val id: UUID,
    val title: String,
    val body: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val revision: Long,
)

private fun NoteEntity.toRecord() =
    NoteRecord(
        id = id,
        title = title,
        body = body,
        createdAt = checkNotNull(createdAt),
        updatedAt = checkNotNull(updatedAt),
        revision = checkNotNull(revision),
    )
