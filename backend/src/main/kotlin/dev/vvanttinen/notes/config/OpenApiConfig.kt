package dev.vvanttinen.notes.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE

const val BEARER_AUTH_SCHEME = "bearerAuth"

@Configuration
class OpenApiConfig {
    @Bean
    fun notesOpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Notes API")
                    .version("0.0.1")
                    .description("Authenticated private Notes backend API."),
            )
            .components(
                Components()
                    .addSecuritySchemes(
                        BEARER_AUTH_SCHEME,
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description(
                                "Notes API bearer access token with the delegated access_as_user scope.",
                            ),
                    )
                    .addSchemas("ProblemDetail", problemSchema())
                    .addResponses("BadRequest", problemResponse("The request is invalid."))
                    .addResponses("Unauthorized", problemResponse("A valid bearer access token is required."))
                    .addResponses("Forbidden", problemResponse("The access_as_user scope is required."))
                    .addResponses("NotFound", problemResponse("The requested note was not found."))
                    .addResponses("Conflict", problemResponse("The note identifier conflicts with an existing note."))
                    .addResponses("PreconditionFailed", problemResponse("The note revision is stale."))
                    .addResponses("PreconditionRequired", problemResponse("If-Match is required.")),
            )

    @Bean
    fun requiredRequestFieldsCustomizer(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            // Nullable Kotlin properties preserve sanitized Bean Validation errors at runtime.
            mapOf(
                "CreateNoteRequest" to setOf("id", "title", "body"),
                "UpdateNoteRequest" to setOf("title", "body"),
            ).forEach { (schemaName, propertyNames) ->
                val properties = openApi.components.schemas[schemaName]?.properties.orEmpty()
                propertyNames.forEach { propertyName ->
                    properties[propertyName]?.types = setOf("string")
                }
            }
        }

    private fun problemSchema(): ObjectSchema =
        ObjectSchema().apply {
            description = "Sanitized RFC 9457 problem details."
            addProperty("title", StringSchema().description("Short problem summary."))
            addProperty("status", IntegerSchema().format("int32").description("HTTP status code."))
            addProperty("detail", StringSchema().description("Sanitized problem detail."))
            addRequiredItem("title")
            addRequiredItem("status")
            addRequiredItem("detail")
        }

    private fun problemResponse(description: String): ApiResponse =
        ApiResponse()
            .description(description)
            .content(
                Content().addMediaType(
                    APPLICATION_PROBLEM_JSON_VALUE,
                    MediaType().schema(
                        ObjectSchema().`$ref`("#/components/schemas/ProblemDetail"),
                    ),
                ),
            )
}
