package dev.vvanttinen.notes.web

import dev.vvanttinen.notes.note.DuplicateNoteIdException
import dev.vvanttinen.notes.note.MalformedNotePreconditionException
import dev.vvanttinen.notes.note.MissingNotePreconditionException
import dev.vvanttinen.notes.note.NoteNotFoundException
import dev.vvanttinen.notes.note.StaleNoteRevisionException
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(assignableTypes = [NotesController::class])
class NotesExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validationFailure(): ProblemDetail =
        problem(
            status = HttpStatus.BAD_REQUEST,
            title = "Invalid request",
            detail = "The note request is invalid.",
        )

    @ExceptionHandler(MalformedNotePreconditionException::class)
    fun malformedPrecondition(): ProblemDetail =
        problem(
            status = HttpStatus.BAD_REQUEST,
            title = "Invalid precondition",
            detail = "If-Match must contain one strong revision ETag.",
        )

    @ExceptionHandler(MissingNotePreconditionException::class)
    fun missingPrecondition(): ProblemDetail =
        problem(
            status = HttpStatusCode.valueOf(428),
            title = "Precondition required",
            detail = "If-Match is required for this operation.",
        )

    @ExceptionHandler(NoteNotFoundException::class)
    fun noteNotFound(): ProblemDetail =
        problem(
            status = HttpStatus.NOT_FOUND,
            title = "Note not found",
            detail = "The requested note was not found.",
        )

    @ExceptionHandler(DuplicateNoteIdException::class)
    fun duplicateNoteId(): ProblemDetail =
        problem(
            status = HttpStatus.CONFLICT,
            title = "Note conflict",
            detail = "A note with this identifier already exists.",
        )

    @ExceptionHandler(StaleNoteRevisionException::class)
    fun staleRevision(): ProblemDetail =
        problem(
            status = HttpStatus.PRECONDITION_FAILED,
            title = "Precondition failed",
            detail = "The note revision does not match.",
        )

    private fun problem(
        status: HttpStatusCode,
        title: String,
        detail: String,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).also {
            it.title = title
        }
}
