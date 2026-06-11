package dev.vvanttinen.notes.repository

import dev.vvanttinen.notes.entity.NoteEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NoteRepository : JpaRepository<NoteEntity, UUID> {
    fun findByIdAndOwner_Id(id: UUID, ownerId: UUID): NoteEntity?

    fun findByIdAndOwner_IdAndDeletedAtIsNull(id: UUID, ownerId: UUID): NoteEntity?

    fun findByOwner_IdAndDeletedAtIsNullOrderByUpdatedAtDescIdAsc(
        ownerId: UUID,
    ): List<NoteEntity>
}
