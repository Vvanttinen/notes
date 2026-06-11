package dev.vvanttinen.notes.web

import dev.vvanttinen.notes.note.NoteRecord
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateNoteRequest(
    @field:NotNull
    val id: UUID?,

    @field:NotNull
    @field:Size(max = 255)
    val title: String?,

    @field:NotNull
    val body: String?,
)

data class UpdateNoteRequest(
    @field:NotNull
    @field:Size(max = 255)
    val title: String?,

    @field:NotNull
    val body: String?,
)

data class NoteResponse(
    val id: UUID,
    val title: String,
    val body: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val revision: Long,
)

fun NoteRecord.toResponse() =
    NoteResponse(
        id = id,
        title = title,
        body = body,
        createdAt = createdAt,
        updatedAt = updatedAt,
        revision = revision,
    )
