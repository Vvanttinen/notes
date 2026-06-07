package dev.vvanttinen.notes.user

import dev.vvanttinen.notes.entity.UserEntity
import dev.vvanttinen.notes.repository.UserRepository
import dev.vvanttinen.notes.security.EntraProperties
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CurrentUserService(
    private val entraProperties: EntraProperties,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun resolve(jwt: Jwt): UserEntity {
        val tenantId = requiredUuidClaim(jwt, "tid")
        val objectId = requiredUuidClaim(jwt, "oid")

        if (tenantId != entraProperties.tenantId) {
            throw BadCredentialsException("The token tenant is not accepted.")
        }

        userRepository.insertIfAbsent(
            id = UUID.randomUUID(),
            entraTenantId = tenantId,
            entraObjectId = objectId,
        )

        return userRepository.findByEntraTenantIdAndEntraObjectId(tenantId, objectId)
            ?: throw AuthenticationServiceException("The current user could not be resolved.")
    }

    private fun requiredUuidClaim(jwt: Jwt, claimName: String): UUID {
        val claimValue = jwt.getClaimAsString(claimName)
        if (claimValue.isNullOrBlank()) {
            throw BadCredentialsException("The token is missing the required $claimName claim.")
        }

        return try {
            UUID.fromString(claimValue)
        } catch (exception: IllegalArgumentException) {
            throw BadCredentialsException("The token $claimName claim is not a UUID.", exception)
        }
    }
}
