package dev.vvanttinen.notes.web

import dev.vvanttinen.notes.note.NoteEtag
import dev.vvanttinen.notes.note.NoteService
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.util.UUID

@RestController
@RequestMapping("/api/notes")
class NotesController(
    private val noteService: NoteService,
) {
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateNoteRequest,
    ): ResponseEntity<NoteResponse> {
        val note = noteService.create(
            id = requireNotNull(request.id),
            title = requireNotNull(request.title),
            body = requireNotNull(request.body),
        )
        val location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{noteId}")
            .buildAndExpand(note.id)
            .toUri()

        return ResponseEntity.created(location)
            .eTag(NoteEtag.format(note.revision))
            .body(note.toResponse())
    }

    @GetMapping
    fun list(): List<NoteResponse> =
        noteService.list().map { it.toResponse() }

    @GetMapping("/{noteId}")
    fun get(
        @PathVariable noteId: UUID,
    ): ResponseEntity<NoteResponse> {
        val note = noteService.get(noteId)
        return ResponseEntity.ok()
            .eTag(NoteEtag.format(note.revision))
            .body(note.toResponse())
    }

    @PutMapping("/{noteId}")
    fun update(
        @PathVariable noteId: UUID,
        @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) ifMatch: List<String>?,
        @Valid @RequestBody request: UpdateNoteRequest,
    ): ResponseEntity<NoteResponse> {
        val note = noteService.update(
            noteId = noteId,
            expectedRevision = NoteEtag.parse(ifMatch),
            title = requireNotNull(request.title),
            body = requireNotNull(request.body),
        )

        return ResponseEntity.ok()
            .eTag(NoteEtag.format(note.revision))
            .body(note.toResponse())
    }

    @DeleteMapping("/{noteId}")
    fun delete(
        @PathVariable noteId: UUID,
        @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) ifMatch: List<String>?,
    ): ResponseEntity<Void> {
        noteService.delete(
            noteId = noteId,
            expectedRevision = NoteEtag.parse(ifMatch),
        )
        return ResponseEntity.noContent().build()
    }
}
