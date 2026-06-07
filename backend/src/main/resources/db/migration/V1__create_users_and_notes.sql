CREATE TABLE users (
    id UUID NOT NULL,
    entra_tenant_id UUID NOT NULL,
    entra_object_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_entra_identity
        UNIQUE (entra_tenant_id, entra_object_id)
);

CREATE TABLE notes (
    id UUID NOT NULL,
    owner_user_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    revision BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_notes PRIMARY KEY (id),
    CONSTRAINT fk_notes_owner_user
        FOREIGN KEY (owner_user_id) REFERENCES users (id)
);

CREATE INDEX ix_notes_owner_active_updated
    ON notes (owner_user_id, updated_at DESC, id)
    WHERE deleted_at IS NULL;