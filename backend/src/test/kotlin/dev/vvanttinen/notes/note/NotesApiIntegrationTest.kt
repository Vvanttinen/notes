package dev.vvanttinen.notes.note

import dev.vvanttinen.notes.entity.NoteEntity
import dev.vvanttinen.notes.entity.UserEntity
import dev.vvanttinen.notes.repository.NoteRepository
import dev.vvanttinen.notes.repository.UserRepository
import dev.vvanttinen.notes.support.AbstractIntegrationTest
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NotesApiIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var noteRepository: NoteRepository

    @Test
    fun `notes API requires authentication`() {
        mockMvc.perform(get("/api/notes"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `notes API requires access as user scope`() {
        mockMvc.perform(
            get("/api/notes").with(
                jwt().jwt { jwt ->
                    jwt
                        .claim("tid", TEST_TENANT_ID.toString())
                        .claim("oid", TEST_OBJECT_ID.toString())
                }.authorities(emptyList()),
            ),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `create provisions owner and returns location ETag and representation`() {
        val noteId = uuid("00000000-0000-0000-0000-000000000001")

        mockMvc.perform(
            post("/api/notes")
                .with(accessAsUserJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest(noteId, "Example title", "Example body")),
        )
            .andExpect(status().isCreated)
            .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost/api/notes/$noteId"))
            .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
            .andExpect(jsonPath("$.id").value(noteId.toString()))
            .andExpect(jsonPath("$.title").value("Example title"))
            .andExpect(jsonPath("$.body").value("Example body"))
            .andExpect(jsonPath("$.createdAt").isString)
            .andExpect(jsonPath("$.updatedAt").isString)
            .andExpect(jsonPath("$.revision").value(0))
            .andExpect(jsonPath("$.owner").doesNotExist())
            .andExpect(jsonPath("$.deletedAt").doesNotExist())

        val currentUser = userRepository.findByEntraTenantIdAndEntraObjectId(TEST_TENANT_ID, TEST_OBJECT_ID)
        val persistedOwnerId = jdbcTemplate.queryForObject(
            "SELECT owner_user_id FROM notes WHERE id = ?",
            UUID::class.java,
            noteId,
        )
        assertEquals(currentUser?.id, persistedOwnerId)
    }

    @Test
    fun `create accepts empty title and body`() {
        val noteId = uuid("00000000-0000-0000-0000-000000000002")

        mockMvc.perform(
            post("/api/notes")
                .with(accessAsUserJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest(noteId, "", "")),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value(""))
            .andExpect(jsonPath("$.body").value(""))
    }

    @Test
    fun `create rejects a title longer than schema limit`() {
        mockMvc.perform(
            post("/api/notes")
                .with(accessAsUserJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest(UUID.randomUUID(), "a".repeat(256), "Body")),
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Invalid request"))
            .andExpect(jsonPath("$.detail").value("The note request is invalid."))
    }

    @Test
    fun `create rejects UUID owned by another user with sanitized conflict`() {
        val otherOwner = persistUser(
            id = uuid("10000000-0000-0000-0000-000000000001"),
            objectId = uuid("20000000-0000-0000-0000-000000000001"),
        )
        val noteId = uuid("00000000-0000-0000-0000-000000000003")
        persistNote(noteId, otherOwner)

        mockMvc.perform(
            post("/api/notes")
                .with(accessAsUserJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest(noteId, "Collision", "Body")),
        )
            .andExpect(status().isConflict)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Note conflict"))
            .andExpect(jsonPath("$.detail").value("A note with this identifier already exists."))
            .andExpect(content().string(not(containsString(otherOwner.id.toString()))))
    }

    @Test
    fun `create rejects reuse of a tombstoned UUID`() {
        val noteId = UUID.randomUUID()
        persistNote(
            id = noteId,
            owner = persistCurrentUser(),
            deletedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

        mockMvc.perform(
            post("/api/notes")
                .with(accessAsUserJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest(noteId, "Replacement", "Body")),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.title").value("Note conflict"))
    }

    @Test
    fun `list returns only current owner active notes in required order`() {
        val owner = persistCurrentUser()
        val otherOwner = persistUser(
            id = uuid("10000000-0000-0000-0000-000000000002"),
            objectId = uuid("20000000-0000-0000-0000-000000000002"),
        )
        val olderId = uuid("00000000-0000-0000-0000-000000000010")
        val tiedFirstId = uuid("00000000-0000-0000-0000-000000000011")
        val tiedSecondId = uuid("00000000-0000-0000-0000-000000000012")
        persistNote(olderId, owner, title = "Older")
        persistNote(tiedSecondId, owner, title = "Tied second")
        persistNote(tiedFirstId, owner, title = "Tied first")
        persistNote(UUID.randomUUID(), owner, deletedAt = Instant.parse("2026-01-03T00:00:00Z"))
        persistNote(UUID.randomUUID(), otherOwner)
        setUpdatedAt(olderId, Instant.parse("2026-01-01T00:00:00Z"))
        setUpdatedAt(tiedFirstId, Instant.parse("2026-01-02T00:00:00Z"))
        setUpdatedAt(tiedSecondId, Instant.parse("2026-01-02T00:00:00Z"))

        mockMvc.perform(get("/api/notes").with(accessAsUserJwt()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].id").value(tiedFirstId.toString()))
            .andExpect(jsonPath("$[1].id").value(tiedSecondId.toString()))
            .andExpect(jsonPath("$[2].id").value(olderId.toString()))
    }

    @Test
    fun `read returns owned active note and current ETag`() {
        val owner = persistCurrentUser()
        val note = persistNote(UUID.randomUUID(), owner)

        mockMvc.perform(get("/api/notes/{noteId}", note.id).with(accessAsUserJwt()))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ETAG, NoteEtag.format(assertNotNull(note.revision))))
            .andExpect(jsonPath("$.id").value(note.id.toString()))
    }

    @Test
    fun `read hides absent cross owner and tombstoned notes with same problem shape`() {
        persistCurrentUser()
        val otherOwner = persistUser(
            id = uuid("10000000-0000-0000-0000-000000000003"),
            objectId = uuid("20000000-0000-0000-0000-000000000003"),
        )
        val otherNote = persistNote(UUID.randomUUID(), otherOwner)
        val deletedNote = persistNote(
            UUID.randomUUID(),
            userRepository.findByEntraTenantIdAndEntraObjectId(TEST_TENANT_ID, TEST_OBJECT_ID)!!,
            deletedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

        listOf(UUID.randomUUID(), otherNote.id, deletedNote.id).forEach { noteId ->
            mockMvc.perform(get("/api/notes/{noteId}", noteId).with(accessAsUserJwt()))
                .andExpect(status().isNotFound)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Note not found"))
                .andExpect(jsonPath("$.detail").value("The requested note was not found."))
        }
    }

    @Test
    fun `update requires If-Match`() {
        val note = persistNote(UUID.randomUUID(), persistCurrentUser())

        mockMvc.perform(
            put("/api/notes/{noteId}", note.id)
                .with(accessAsUserJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest("Updated", "Updated body")),
        )
            .andExpect(status().`is`(428))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Precondition required"))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "0",
            "W/\"0\"",
            "*",
            "\"-1\"",
            "\"9223372036854775808\"",
            "\"0\", \"1\"",
        ],
    )
    fun `update rejects malformed If-Match`(ifMatch: String) {
        val note = persistNote(UUID.randomUUID(), persistCurrentUser())

        mockMvc.perform(
            put("/api/notes/{noteId}", note.id)
                .with(accessAsUserJwt())
                .header(HttpHeaders.IF_MATCH, ifMatch)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest("Updated", "Updated body")),
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Invalid precondition"))
    }

    @Test
    fun `update rejects repeated If-Match headers`() {
        val note = persistNote(UUID.randomUUID(), persistCurrentUser())

        mockMvc.perform(
            put("/api/notes/{noteId}", note.id)
                .with(accessAsUserJwt())
                .header(HttpHeaders.IF_MATCH, "\"0\"", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest("Updated", "Updated body")),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Invalid precondition"))
    }

    @Test
    fun `update replaces content increments revision and returns fresh ETag`() {
        val note = persistNote(UUID.randomUUID(), persistCurrentUser())
        val initialRevision = assertNotNull(note.revision)

        mockMvc.perform(
            put("/api/notes/{noteId}", note.id)
                .with(accessAsUserJwt())
                .header(HttpHeaders.IF_MATCH, NoteEtag.format(initialRevision))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest("Updated", "Updated body")),
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ETAG, NoteEtag.format(initialRevision + 1)))
            .andExpect(jsonPath("$.title").value("Updated"))
            .andExpect(jsonPath("$.body").value("Updated body"))
            .andExpect(jsonPath("$.revision").value(initialRevision + 1))
    }

    @Test
    fun `stale update returns precondition failed without changing content`() {
        val note = persistNote(UUID.randomUUID(), persistCurrentUser(), title = "Original", body = "Original body")

        mockMvc.perform(
            put("/api/notes/{noteId}", note.id)
                .with(accessAsUserJwt())
                .header(HttpHeaders.IF_MATCH, "\"99\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest("Rejected", "Rejected body")),
        )
            .andExpect(status().isPreconditionFailed)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Precondition failed"))

        val persisted = noteRepository.findById(note.id).orElseThrow()
        assertEquals("Original", persisted.title)
        assertEquals("Original body", persisted.body)
    }

    @Test
    fun `delete requires If-Match`() {
        val note = persistNote(UUID.randomUUID(), persistCurrentUser())

        mockMvc.perform(delete("/api/notes/{noteId}", note.id).with(accessAsUserJwt()))
            .andExpect(status().`is`(428))
    }

    @Test
    fun `delete tombstones note and hides it from active reads and list`() {
        val note = persistNote(UUID.randomUUID(), persistCurrentUser())

        mockMvc.perform(
            delete("/api/notes/{noteId}", note.id)
                .with(accessAsUserJwt())
                .header(HttpHeaders.IF_MATCH, NoteEtag.format(assertNotNull(note.revision))),
        ).andExpect(status().isNoContent)

        assertNotNull(noteRepository.findById(note.id).orElseThrow().deletedAt)
        mockMvc.perform(get("/api/notes/{noteId}", note.id).with(accessAsUserJwt()))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/api/notes").with(accessAsUserJwt()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `stale delete returns precondition failed without tombstoning`() {
        val note = persistNote(UUID.randomUUID(), persistCurrentUser())

        mockMvc.perform(
            delete("/api/notes/{noteId}", note.id)
                .with(accessAsUserJwt())
                .header(HttpHeaders.IF_MATCH, "\"99\""),
        )
            .andExpect(status().isPreconditionFailed)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))

        assertNull(noteRepository.findById(note.id).orElseThrow().deletedAt)
    }

    private fun accessAsUserJwt(
        tenantId: UUID = TEST_TENANT_ID,
        objectId: UUID = TEST_OBJECT_ID,
    ) = jwt().jwt { jwt ->
        jwt
            .claim("scp", "access_as_user")
            .claim("tid", tenantId.toString())
            .claim("oid", objectId.toString())
            .audience(listOf(TEST_API_CLIENT_ID))
            .issuer("https://login.microsoftonline.com/$TEST_TENANT_ID/v2.0")
    }.authorities(SimpleGrantedAuthority("SCOPE_access_as_user"))

    private fun persistCurrentUser(): UserEntity =
        userRepository.findByEntraTenantIdAndEntraObjectId(TEST_TENANT_ID, TEST_OBJECT_ID)
            ?: persistUser(
                id = uuid("10000000-0000-0000-0000-000000000010"),
                objectId = TEST_OBJECT_ID,
            )

    private fun persistUser(
        id: UUID,
        objectId: UUID,
    ): UserEntity =
        userRepository.saveAndFlush(
            UserEntity(
                id = id,
                entraTenantId = TEST_TENANT_ID,
                entraObjectId = objectId,
            ),
        )

    private fun persistNote(
        id: UUID,
        owner: UserEntity,
        title: String = "Title",
        body: String = "Body",
        deletedAt: Instant? = null,
    ): NoteEntity =
        noteRepository.saveAndFlush(
            NoteEntity(
                id = id,
                owner = owner,
                title = title,
                body = body,
                deletedAt = deletedAt,
            ),
        )

    private fun setUpdatedAt(
        noteId: UUID,
        updatedAt: Instant,
    ) {
        jdbcTemplate.update(
            "UPDATE notes SET updated_at = ? WHERE id = ?",
            Timestamp.from(updatedAt),
            noteId,
        )
    }

    private fun createRequest(
        id: UUID,
        title: String,
        body: String,
    ): String =
        objectMapper.writeValueAsString(
            mapOf(
                "id" to id,
                "title" to title,
                "body" to body,
            ),
        )

    private fun updateRequest(
        title: String,
        body: String,
    ): String =
        objectMapper.writeValueAsString(
            mapOf(
                "title" to title,
                "body" to body,
            ),
        )

    private fun uuid(value: String): UUID = UUID.fromString(value)

    private companion object {
        val TEST_TENANT_ID: UUID = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val TEST_OBJECT_ID: UUID = UUID.fromString("20000000-0000-0000-0000-000000000000")
        const val TEST_API_CLIENT_ID = "30000000-0000-0000-0000-000000000000"
    }
}
