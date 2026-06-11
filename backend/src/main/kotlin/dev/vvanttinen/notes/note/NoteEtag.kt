package dev.vvanttinen.notes.note

object NoteEtag {
    private val strongRevisionPattern = Regex("^\"([0-9]+)\"$")

    fun format(revision: Long): String = "\"$revision\""

    fun parse(ifMatch: String?): Long {
        if (ifMatch == null) {
            throw MissingNotePreconditionException()
        }

        val revision = strongRevisionPattern.matchEntire(ifMatch)?.groupValues?.get(1)
            ?: throw MalformedNotePreconditionException()

        return revision.toLongOrNull()
            ?: throw MalformedNotePreconditionException()
    }
}
