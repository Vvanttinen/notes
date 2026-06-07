package dev.vvanttinen.notes.security

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.util.UUID

@Validated
@ConfigurationProperties("notes.entra")
data class EntraProperties(
    val tenantId: UUID,

    @field:NotBlank
    val apiClientId: String,
) {
    val issuerUri: String
        get() = "https://login.microsoftonline.com/$tenantId/v2.0"
}
