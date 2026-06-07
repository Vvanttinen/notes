package dev.vvanttinen.notes.support

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
@Import(TestcontainersConfiguration::class)
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
