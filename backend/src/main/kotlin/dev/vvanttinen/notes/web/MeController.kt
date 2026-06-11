package dev.vvanttinen.notes.web

import dev.vvanttinen.notes.config.BEARER_AUTH_SCHEME
import dev.vvanttinen.notes.user.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
class MeController(
    private val currentUser: CurrentUser,
) {
    @Operation(
        operationId = "getCurrentUser",
        summary = "Get the current local user",
        description = "Resolves or provisions the local user represented by the authenticated access token.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Current local user.",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(implementation = MeResponse::class),
                    ),
                ],
            ),
            ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthorized"),
            ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
        ],
    )
    @GetMapping("/me", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun me(): MeResponse =
        MeResponse(userId = currentUser.currentUserId())
}
