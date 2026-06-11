package dev.vvanttinen.notes.note

sealed class NoteApiException : RuntimeException()

class NoteNotFoundException : NoteApiException()

class DuplicateNoteIdException : NoteApiException()

class MissingNotePreconditionException : NoteApiException()

class MalformedNotePreconditionException : NoteApiException()

class StaleNoteRevisionException : NoteApiException()
