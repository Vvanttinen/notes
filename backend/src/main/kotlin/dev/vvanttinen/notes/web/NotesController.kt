package dev.vvanttinen.notes.web

import dev.vvanttinen.notes.config.BEARER_AUTH_SCHEME
import dev.vvanttinen.notes.note.NoteEtag
import dev.vvanttinen.notes.note.NoteService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
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
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
class NotesController(
    private val noteService: NoteService,
) {
    @Operation(
        operationId = "createNote",
        summary = "Create a private note",
        description = "Creates an owned note using a client-generated UUID and returns its strong revision ETag.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Note created.",
                headers = [
                    Header(
                        name = HttpHeaders.LOCATION,
                        description = "Canonical path of the created note.",
                        required = true,
                        schema = Schema(
                            type = "string",
                            example = "/api/notes/00000000-0000-0000-0000-000000000001",
                        ),
                    ),
                    Header(
                        name = HttpHeaders.ETAG,
                        description = "Strong quoted note revision.",
                        required = true,
                        schema = Schema(type = "string", example = "\"0\""),
                    ),
                ],
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = NoteResponse::class),
                    ),
                ],
            ),
            ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
        ],
    )
    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
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

    @Operation(
        operationId = "listNotes",
        summary = "List private notes",
        description = "Lists the current user's active notes ordered by most recently updated.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Active owned notes.",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        array = ArraySchema(schema = Schema(implementation = NoteResponse::class)),
                    ),
                ],
            ),
            ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
        ],
    )
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun list(): List<NoteResponse> =
        noteService.list().map { it.toResponse() }

    @Operation(
        operationId = "getNote",
        summary = "Get a private note",
        description = "Returns an active owned note and its strong revision ETag.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Active owned note.",
                headers = [
                    Header(
                        name = HttpHeaders.ETAG,
                        description = "Strong quoted note revision.",
                        required = true,
                        schema = Schema(type = "string", example = "\"0\""),
                    ),
                ],
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = NoteResponse::class),
                    ),
                ],
            ),
            ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
        ],
    )
    @GetMapping("/{noteId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun get(
        @PathVariable noteId: UUID,
    ): ResponseEntity<NoteResponse> {
        val note = noteService.get(noteId)
        return ResponseEntity.ok()
            .eTag(NoteEtag.format(note.revision))
            .body(note.toResponse())
    }

    @Operation(
        operationId = "updateNote",
        summary = "Update a private note",
        description = "Replaces title and body when If-Match contains the current strong revision ETag.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updated note.",
                headers = [
                    Header(
                        name = HttpHeaders.ETAG,
                        description = "Fresh strong quoted note revision.",
                        required = true,
                        schema = Schema(type = "string", example = "\"1\""),
                    ),
                ],
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = NoteResponse::class),
                    ),
                ],
            ),
            ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            ApiResponse(responseCode = "412", ref = "#/components/responses/PreconditionFailed"),
            ApiResponse(responseCode = "428", ref = "#/components/responses/PreconditionRequired"),
        ],
    )
    @PutMapping(
        "/{noteId}",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    fun update(
        @PathVariable noteId: UUID,
        @Parameter(
            name = HttpHeaders.IF_MATCH,
            `in` = ParameterIn.HEADER,
            required = true,
            description = IF_MATCH_DESCRIPTION,
            schema = Schema(type = "string", example = "\"0\""),
        )
        @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
        ifMatch: String?,
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

    @Operation(
        operationId = "deleteNote",
        summary = "Delete a private note",
        description = "Soft-deletes an active owned note when If-Match contains its current strong revision ETag.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Note soft-deleted."),
            ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            ApiResponse(responseCode = "412", ref = "#/components/responses/PreconditionFailed"),
            ApiResponse(responseCode = "428", ref = "#/components/responses/PreconditionRequired"),
        ],
    )
    @DeleteMapping("/{noteId}")
    fun delete(
        @PathVariable noteId: UUID,
        @Parameter(
            name = HttpHeaders.IF_MATCH,
            `in` = ParameterIn.HEADER,
            required = true,
            description = IF_MATCH_DESCRIPTION,
            schema = Schema(type = "string", example = "\"0\""),
        )
        @RequestHeader(name = HttpHeaders.IF_MATCH, required = false)
        ifMatch: String?,
    ): ResponseEntity<Void> {
        noteService.delete(
            noteId = noteId,
            expectedRevision = NoteEtag.parse(ifMatch),
        )
        return ResponseEntity.noContent().build()
    }
}

private const val IF_MATCH_DESCRIPTION =
    "Exactly one quoted strong non-negative decimal revision ETag. Missing returns 428; malformed, weak, " +
        "wildcard, negative, non-numeric, overflowing, or multiple values return 400; stale returns 412."
