package dev.vvanttinen.notes.user

import dev.vvanttinen.notes.entity.UserEntity
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

@Component
class CurrentUser(
    private val currentUserService: CurrentUserService,
) {
    fun resolve(): UserEntity {
        val authentication = SecurityContextHolder.getContext().authentication
        val jwt = (authentication as? JwtAuthenticationToken)?.token
            ?: throw AuthenticationCredentialsNotFoundException("An authenticated JWT is required.")

        return currentUserService.resolve(jwt)
    }

    fun currentUserId() = resolve().id
}
