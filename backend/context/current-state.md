# Backend current state

## Purpose

- `notes` is the Spring Boot API root for the Notes product.
- The backend is an independent Gradle root at `backend/`.

## Identifiers

- Kotlin package root: `dev.vvanttinen.notes`.
- Gradle group: `dev.vvanttinen`.
- Gradle project version: `0.0.1-SNAPSHOT`.
- Spring application name: `notes`.

## Toolchain and build

- Gradle settings file: `settings.gradle.kts`.
- Gradle root project name: `notes`.
- Gradle wrapper version: `9.5.1`.
- Java toolchain version: `21`.
- Kotlin JVM plugin version: `2.2.21`.
- Kotlin Spring plugin version: `2.2.21`.
- Kotlin JPA plugin version: `2.2.21`.
- Spring Boot Gradle plugin version: `4.0.6`.
- Spring dependency management plugin version: `1.1.7`.
- Kotlin compiler options include `-Xjsr305=strict` and `-Xannotation-default-target=param-property`.
- JUnit Platform is configured for Gradle `Test` tasks.

## Persistence baseline

- Flyway owns the database schema; Hibernate is configured with `spring.jpa.hibernate.ddl-auto=validate`.
- Initial schema migration: `src/main/resources/db/migration/V1__create_users_and_notes.sql`.
- The migration creates `users` before `notes` and defines explicit primary-key, unique, foreign-key, and index names.
- `users` stores local user rows keyed by UUID and unique Entra identity mapping columns `(entra_tenant_id, entra_object_id)`.
- `notes` stores UUID-keyed notes owned by `users(id)`, with title, body, timestamps, nullable `deleted_at` tombstones, and JPA optimistic-lock `revision`.
- `created_at` and `updated_at` use database defaults in the schema; `NoteEntity` also uses Hibernate creation/update timestamps for managed note writes.
- The active-note index supports owner-scoped active-note listing by `(owner_user_id, updated_at DESC, id)` where `deleted_at IS NULL`.
- No global sync sequence, sync trigger, sync cursor repository method, or synchronization endpoint exists in the simplified baseline.

## JPA and repositories

- JPA entities live under `dev.vvanttinen.notes.entity`.
- Spring Data repositories live under `dev.vvanttinen.notes.repository`.
- `UserEntity` maps the local `users` table.
- `NoteEntity` maps the `notes` table and uses a required lazy `ManyToOne` owner association to `UserEntity`.
- `NoteEntity.revision` is mapped with `@Version` for optimistic locking.
- `UserRepository` supports lookup by `(entraTenantId, entraObjectId)`.
- `NoteRepository` supports ownership-scoped lookup by `(id, owner.id)` and active notes for an owner ordered by `updatedAt DESC, id ASC`.

## Local PostgreSQL

- Local PostgreSQL Compose configuration exists in `compose.yaml`.
- Compose service: `postgres`.
- Compose image: `postgres:17-alpine`.
- Compose uses non-production defaults: database `notes_dev`, user `notes_dev`, and password `notes_dev_password`.
- Compose values can be overridden with `NOTES_DATABASE_NAME`, `NOTES_DATABASE_USERNAME`, and `NOTES_DATABASE_PASSWORD`.
- Application datasource values are read from `NOTES_DATABASE_URL`, `NOTES_DATABASE_USERNAME`, and `NOTES_DATABASE_PASSWORD`, with matching local-development defaults.
- Minimal local database startup command from `backend/`: `docker compose up -d postgres`.
- No secrets, tenant-specific values, client IDs, access tokens, redirect URIs, or authentication configuration values are stored in backend configuration.

## Integration tests

- PostgreSQL-backed integration tests use Testcontainers through `src/test/kotlin/dev/vvanttinen/notes/support/TestcontainersConfiguration.kt`.
- The test PostgreSQL image is `postgres:17-alpine`, matching the local Compose major version.
- Spring Boot test connection details are supplied through `@ServiceConnection`.
- `AbstractIntegrationTest` starts the Spring Boot context with the Testcontainers configuration and truncates `notes` before `users` with `RESTART IDENTITY CASCADE` before each test.
- `NotesApplicationTests` is a context-load smoke test that exercises container startup, Spring Boot service connection wiring, Flyway migration, and JPA mapping validation.
- `PersistenceIntegrationTest` covers user identity lookup, unique Entra identity enforcement, note ownership scoping, active-note filtering, active-note ordering by `updatedAt DESC, id ASC`, and optimistic-lock revision updates.
- Tests run against PostgreSQL, not H2.

## Verification

- Primary command on Windows: `gradlew.bat test` from `backend/`.
- Standard lifecycle verification command: `gradlew.bat check` from `backend/`.

## Deferred work

- REST CRUD controllers and DTOs remain deferred.
- Microsoft Entra resource-server behavior, MSAL integration, token parsing, claim validation, and just-in-time user provisioning remain deferred.
- Offline synchronization endpoints, cursor queries, conflict-resolution APIs, and related sync infrastructure remain deferred.
- Sharing tables and sharing behavior remain deferred.
- Frontend changes are not part of this backend baseline.
