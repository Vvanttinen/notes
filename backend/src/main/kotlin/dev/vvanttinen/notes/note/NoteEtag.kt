package dev.vvanttinen.notes.note

object NoteEtag {
    private val strongRevisionPattern = Regex("^\"([0-9]+)\"$")

    fun format(revision: Long): String = "\"$revision\""

    fun parse(ifMatchValues: List<String>?): Long {
        if (ifMatchValues.isNullOrEmpty()) {
            throw MissingNotePreconditionException()
        }
        if (ifMatchValues.size != 1) {
            throw MalformedNotePreconditionException()
        }

        val revision = strongRevisionPattern.matchEntire(ifMatchValues.single())?.groupValues?.get(1)
            ?: throw MalformedNotePreconditionException()

        return revision.toLongOrNull()
            ?: throw MalformedNotePreconditionException()
    }
}
