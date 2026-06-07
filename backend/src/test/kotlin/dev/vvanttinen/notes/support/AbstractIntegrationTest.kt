package dev.vvanttinen.notes.support

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest(
    properties = [
        "NOTES_ENTRA_TENANT_ID=10000000-0000-0000-0000-000000000000",
        "NOTES_ENTRA_API_CLIENT_ID=30000000-0000-0000-0000-000000000000",
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
