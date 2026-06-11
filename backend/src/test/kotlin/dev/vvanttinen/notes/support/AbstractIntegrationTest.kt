package dev.vvanttinen.notes.support

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest(
    properties = [
        "notes.entra.tenant-id=10000000-0000-0000-0000-000000000000",
        "notes.entra.api-client-id=30000000-0000-0000-0000-000000000000",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://login.microsoftonline.com/10000000-0000-0000-0000-000000000000/v2.0",
    ],
)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, TestSecurityConfiguration::class)
abstract class AbstractIntegrationTest {
    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanDatabase() {
        jdbcTemplate.execute(
            """
            TRUNCATE TABLE
                notes,
                users
            RESTART IDENTITY CASCADE
            """.trimIndent(),
        )
    }
}
