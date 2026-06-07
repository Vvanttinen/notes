package dev.vvanttinen.notes.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "users",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_users_entra_identity",
            columnNames = ["entra_tenant_id", "entra_object_id"],
        ),
    ],
)
class UserEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "entra_tenant_id", nullable = false)
    var entraTenantId: UUID,

    @Column(name = "entra_object_id", nullable = false)
    var entraObjectId: UUID,
) {
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    var createdAt: Instant? = null
        protected set

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    var updatedAt: Instant? = null
        protected set
}
