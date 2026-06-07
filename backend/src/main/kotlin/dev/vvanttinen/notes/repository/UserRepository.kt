package dev.vvanttinen.notes.repository

import dev.vvanttinen.notes.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<UserEntity, UUID> {
    fun findByEntraTenantIdAndEntraObjectId(
        entraTenantId: UUID,
        entraObjectId: UUID,
    ): UserEntity?
}