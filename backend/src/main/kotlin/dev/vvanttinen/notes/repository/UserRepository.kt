package dev.vvanttinen.notes.repository

import dev.vvanttinen.notes.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserRepository : JpaRepository<UserEntity, UUID> {
    fun findByEntraTenantIdAndEntraObjectId(
        entraTenantId: UUID,
        entraObjectId: UUID,
    ): UserEntity?

    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO users (id, entra_tenant_id, entra_object_id)
            VALUES (:id, :entraTenantId, :entraObjectId)
            ON CONFLICT ON CONSTRAINT uq_users_entra_identity DO NOTHING
        """,
    )
    fun insertIfAbsent(
        @Param("id") id: UUID,
        @Param("entraTenantId") entraTenantId: UUID,
        @Param("entraObjectId") entraObjectId: UUID,
    ): Int
}
