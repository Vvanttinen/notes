package dev.vvanttinen.notes.web

import dev.vvanttinen.notes.user.CurrentUserService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api")
class MeController(
    private val currentUserService: CurrentUserService,
) {
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal jwt: Jwt): MeResponse =
        MeResponse(userId = currentUserService.resolve(jwt).id)
}

data class MeResponse(
    val userId: UUID,
)
