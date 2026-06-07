package dev.vvanttinen.notes.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "notes")
class NoteEntity(
    @Id
    var id: UUID,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    var owner: UserEntity,

    @Column(nullable = false, length = 255)
    var title: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var body: String,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,

    @Version
    @Column(nullable = false)
    var revision: Long? = null,
)