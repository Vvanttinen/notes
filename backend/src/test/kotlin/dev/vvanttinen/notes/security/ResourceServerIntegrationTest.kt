package dev.vvanttinen.notes.security

import dev.vvanttinen.notes.repository.UserRepository
import dev.vvanttinen.notes.support.AbstractIntegrationTest
import dev.vvanttinen.notes.user.CurrentUserService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResourceServerIntegrationTest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var currentUserService: CurrentUserService

    @Test
    fun `health endpoint permits anonymous requests`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
    }

    @Test
    fun `api me requires authentication`() {
        mockMvc.perform(get("/api/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `api me rejects an authenticated JWT without the access as user scope`() {
        mockMvc.perform(
            get("/api/me").with(
                jwt().jwt { jwt ->
                    jwt
                        .claim("tid", TEST_TENANT_ID.toString())
                        .claim("oid", TEST_OBJECT_ID.toString())
                }.authorities(emptyList()),
            ),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `Spring JWT authority converter maps scp claim to access as user authority`() {
        val authorities = JwtGrantedAuthoritiesConverter().convert(
            serviceJwt(scope = "access_as_user"),
        )

        assertTrue(authorities.any { it.authority == "SCOPE_access_as_user" })
    }

    @Test
    fun `audience validator accepts only the Notes API audience`() {
        val validator = audienceValidator()

        assertFalse(validator.validate(serviceJwt(audience = listOf(TEST_API_CLIENT_ID))).hasErrors())

        val wrongAudienceResult = validator.validate(
            serviceJwt(audience = listOf("40000000-0000-0000-0000-000000000000")),
        )

        assertTrue(wrongAudienceResult.hasErrors())
        assertEquals("invalid_token", wrongAudienceResult.errors.single().errorCode)
    }

    @Test
    fun `api me resolves and provisions the current user`() {
        val result = mockMvc.perform(get("/api/me").with(accessAsUserJwt()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").isString)
            .andReturn()

        val userId = extractUserId(result.response.contentAsString)
        val user = userRepository.findByEntraTenantIdAndEntraObjectId(TEST_TENANT_ID, TEST_OBJECT_ID)

        assertEquals(userId, user?.id)
        assertEquals(1, countUsers(TEST_TENANT_ID, TEST_OBJECT_ID))
    }

    @Test
    fun `api me returns the same user for repeated requests from the same identity`() {
        val firstResponse = mockMvc.perform(get("/api/me").with(accessAsUserJwt()))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        val secondResponse = mockMvc.perform(get("/api/me").with(accessAsUserJwt()))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        assertEquals(extractUserId(firstResponse), extractUserId(secondResponse))
        assertEquals(1, countUsers(TEST_TENANT_ID, TEST_OBJECT_ID))
    }

    @Test
    fun `api me rejects a mismatched tenant claim cleanly`() {
        mockMvc.perform(
            get("/api/me").with(
                accessAsUserJwt(
                    tenantId = UUID.fromString("10000000-0000-0000-0000-000000000001"),
                ),
            ),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `api me rejects a missing required identity claim cleanly`() {
        mockMvc.perform(
            get("/api/me").with(
                jwt().jwt { jwt ->
                    jwt
                        .claim("scp", "access_as_user")
                        .claim("oid", TEST_OBJECT_ID.toString())
                }.authorities(accessAsUserAuthority()),
            ),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `api me rejects a malformed required identity claim cleanly`() {
        mockMvc.perform(
            get("/api/me").with(
                jwt().jwt { jwt ->
                    jwt
                        .claim("scp", "access_as_user")
                        .claim("tid", TEST_TENANT_ID.toString())
                        .claim("oid", "not-a-uuid")
                }.authorities(accessAsUserAuthority()),
            ),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `concurrent first use provisions one local user`() {
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val jwt = serviceJwt()

        try {
            val futures = List(2) {
                executor.submit(
                    Callable {
                        start.await(5, TimeUnit.SECONDS)
                        currentUserService.resolve(jwt).id
                    },
                )
            }

            start.countDown()

            val resolvedIds = futures.map { it.get(10, TimeUnit.SECONDS) }.toSet()

            assertEquals(setOf(resolvedIds.single()), resolvedIds)
            assertEquals(1, countUsers(TEST_TENANT_ID, TEST_OBJECT_ID))
        } finally {
            executor.shutdownNow()
        }
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
    }.authorities(accessAsUserAuthority())

    private fun accessAsUserAuthority() =
        listOf(SimpleGrantedAuthority("SCOPE_access_as_user"))

    private fun serviceJwt(
        audience: List<String> = listOf(TEST_API_CLIENT_ID),
        scope: String? = null,
    ): Jwt {
        val jwt = Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .claim("tid", TEST_TENANT_ID.toString())
            .claim("oid", TEST_OBJECT_ID.toString())
            .audience(audience)
            .issuer("https://login.microsoftonline.com/$TEST_TENANT_ID/v2.0")
            .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .expiresAt(Instant.parse("2026-01-01T01:00:00Z"))

        if (scope != null) {
            jwt.claim("scp", scope)
        }

        return jwt.build()
    }

    @Suppress("UNCHECKED_CAST")
    private fun audienceValidator(): OAuth2TokenValidator<Jwt> =
        ReflectionTestUtils.invokeMethod<OAuth2TokenValidator<Jwt>>(
            SecurityConfiguration(),
            "audienceValidator",
            TEST_API_CLIENT_ID,
        ) ?: error("SecurityConfiguration audienceValidator method was not found.")

    private fun countUsers(
        tenantId: UUID,
        objectId: UUID,
    ): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE entra_tenant_id = ? AND entra_object_id = ?",
            Int::class.java,
            tenantId,
            objectId,
        ) ?: 0

    private fun extractUserId(responseBody: String): UUID {
        val userId = Regex(""""userId"\s*:\s*"([^"]+)"""")
            .find(responseBody)
            ?.groupValues
            ?.get(1)
            ?: error("Response did not contain a userId: $responseBody")
        return UUID.fromString(userId)
    }

    private companion object {
        val TEST_TENANT_ID: UUID = UUID.fromString("10000000-0000-0000-0000-000000000000")
        val TEST_OBJECT_ID: UUID = UUID.fromString("20000000-0000-0000-0000-000000000000")
        const val TEST_API_CLIENT_ID = "30000000-0000-0000-0000-000000000000"
    }
}
