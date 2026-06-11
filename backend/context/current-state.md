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
- Spring Security OAuth 2.0 resource-server JWT support is present through Spring Boot dependency management.
- Spring Security resource-server test support is present for deterministic authenticated MVC tests.
- Springdoc OpenAPI 3.0.3 generates the OpenAPI document and serves Swagger UI.

## Authentication and authorization

- The backend acts as a single-tenant Microsoft Entra ID OAuth 2.0 resource server.
- The API validates bearer access tokens, not ID tokens or OAuth login sessions.
- Required live-run environment variables are `NOTES_ENTRA_TENANT_ID` and `NOTES_ENTRA_API_CLIENT_ID`.
- `application.properties` derives the tenant-specific v2 issuer URI as `https://login.microsoftonline.com/<NOTES_ENTRA_TENANT_ID>/v2.0`.
- `EntraProperties` binds the configured tenant UUID and Notes API client ID without committing real values.
- The production `JwtDecoder` uses the tenant-specific issuer and validates JWT signature, issuer, token timestamps, and the Notes API audience.
- API security is stateless, disables CSRF for bearer-token requests, and does not configure form login, HTTP Basic, OAuth login redirects, or server-side sessions.
- `GET /actuator/health` and health subpaths are permitted without authentication.
- `GET /v3/api-docs/**`, `/swagger-ui.html`, and `/swagger-ui/**` are permitted without authentication.
- `/api/**` requires an authenticated JWT with the default Spring Security `SCOPE_access_as_user` authority.
- Any other request is denied by default.

## Current user boundary

- `CurrentUserService` resolves the authenticated JWT to the local `users` row.
- `CurrentUser` obtains the request JWT from the Spring Security context and delegates local-user resolution to `CurrentUserService`, allowing controllers to remain thin HTTP routers.
- The service requires nonblank `tid` and `oid` claims, parses both as UUIDs, and rejects missing or malformed values as authentication failures.
- The service rejects a `tid` claim that does not match `NOTES_ENTRA_TENANT_ID`.
- The durable external identity key is `(entra_tenant_id, entra_object_id)`, sourced from validated `(tid, oid)` claims.
- Local application code continues to use the existing UUID `users.id`.
- Just-in-time user provisioning uses PostgreSQL `INSERT ... ON CONFLICT ON CONSTRAINT uq_users_entra_identity DO NOTHING` followed by a read-back in one transaction, so concurrent first requests converge on the winning row without duplicate-user rollback failures.

## API endpoints

- `GET /api/me` is a protected integration probe under `/api/**`.
- `GET /api/me` resolves or provisions the current local user and returns only `{ "userId": "<local-user-uuid>" }`.
- The endpoint does not expose raw access tokens or profile claims.
- Private note endpoints are protected under `/api/notes` and resolve the owner exclusively from the authenticated local user.
- `POST /api/notes` accepts a client-generated note UUID plus title and body, creates an active note, and returns `201 Created`, `Location`, a strong revision `ETag`, and the note DTO.
- `GET /api/notes` returns only the current user's active notes ordered by `updatedAt DESC, id ASC`.
- `GET /api/notes/{noteId}` returns an active owned note and its strong revision `ETag`.
- `PUT /api/notes/{noteId}` replaces title and body when `If-Match` contains the current strong revision ETag.
- `DELETE /api/notes/{noteId}` soft-deletes an active owned note when `If-Match` contains the current strong revision ETag and returns `204 No Content`.
- Cross-user, absent, and tombstoned note lookups are indistinguishable and return the same sanitized `404 Not Found` problem response.
- Note API DTOs expose `id`, `title`, `body`, `createdAt`, `updatedAt`, and `revision`; they do not expose owner identifiers or tombstone timestamps.
- Create requests require non-null `id`, `title`, and `body`; update requests require non-null `title` and `body`.
- Titles are limited to 255 characters. Empty title and body strings are accepted, content is not trimmed or rewritten, and no arbitrary body-size product constraint is applied.
- Note UUIDs are globally non-reusable, including after soft deletion; collisions return a generic `409 Conflict`.
- Revision `N` is represented as the strong ETag `"N"`. Update and delete require exactly one quoted strong non-negative decimal `If-Match` value.
- Missing `If-Match` returns `428 Precondition Required`; malformed, weak, wildcard, negative, overflowing, or multiple tags return `400 Bad Request`; stale valid revisions return `412 Precondition Failed` without mutation.
- Notes API validation and custom errors use sanitized RFC 9457-style `application/problem+json` responses.

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
- `NoteRepository` supports ownership-scoped lookup by `(id, owner.id)`, active ownership-scoped lookup that excludes tombstones, and active notes for an owner ordered by `updatedAt DESC, id ASC`.
- Note update and delete operations flush inside their request transaction so optimistic-lock failures are translated before the HTTP response is committed.
- Soft deletion sets `deleted_at` and preserves the row as a tombstone; no hard-delete API exists.

## Local PostgreSQL

- Local PostgreSQL Compose configuration exists in `compose.yaml`.
- Compose service: `postgres`.
- Compose image: `postgres:17-alpine`.
- Compose uses non-production defaults: database `notes_dev`, user `notes_dev`, and password `notes_dev_password`.
- Compose values can be overridden with `NOTES_DATABASE_NAME`, `NOTES_DATABASE_USERNAME`, and `NOTES_DATABASE_PASSWORD`.
- Application datasource values are read from `NOTES_DATABASE_URL`, `NOTES_DATABASE_USERNAME`, and `NOTES_DATABASE_PASSWORD`, with matching local-development defaults.
- Minimal local database startup command from `backend/`: `docker compose up -d postgres`.
- No secrets, real tenant-specific values, real client IDs, access tokens, redirect URIs, signing material, certificates, or authentication secrets are stored in backend configuration.

## Documentation

- Generated OpenAPI JSON is available at `/v3/api-docs`, and Swagger UI is available at `/swagger-ui/index.html`.
- The generated OpenAPI document identifies the API as `Notes API` version `0.0.1` and uses stable operation IDs for the current-user and Notes CRUD operations.
- Protected API operations declare the sanitized `bearerAuth` HTTP bearer JWT scheme for Notes API access tokens with delegated `access_as_user` scope.
- The generated contract documents exact success statuses, `application/json` DTO responses, create/read/update `ETag` headers, create `Location`, strict scalar `If-Match` semantics, and reusable `application/problem+json` responses.
- Required request and response DTO fields are non-null in the generated OpenAPI schemas; a focused OpenAPI customizer corrects request-schema nullability while runtime request properties remain nullable for sanitized Bean Validation errors.
- Sanitized Microsoft Entra API registration instructions are in `docs/entra-api-registration.md`.
- The documentation covers a single-tenant `Notes API` registration, default `api://<NOTES_ENTRA_API_CLIENT_ID>` Application ID URI, delegated `access_as_user` scope, required backend environment variables, and deferred Android public-client setup.
- The private Notes CRUD contract, Swagger bearer-token testing, DTO validation, ETag rules, statuses, sanitized errors, and deferred synchronization work are documented in `docs/notes-api.md`.

## Integration tests

- PostgreSQL-backed integration tests use Testcontainers through `src/test/kotlin/dev/vvanttinen/notes/support/TestcontainersConfiguration.kt`.
- The test PostgreSQL image is `postgres:17-alpine`, matching the local Compose major version.
- Spring Boot test connection details are supplied through `@ServiceConnection`.
- `AbstractIntegrationTest` starts the Spring Boot context with the Testcontainers configuration, deterministic test-only Entra property values, and a primary test `JwtDecoder` so tests do not contact a live tenant.
- The deterministic test Entra properties override the bound Spring property names directly, so host environment variables cannot change test identities.
- `AbstractIntegrationTest` truncates `notes` before `users` with `RESTART IDENTITY CASCADE` before each test.
- `NotesApplicationTests` is a context-load smoke test that exercises container startup, Spring Boot service connection wiring, Flyway migration, and JPA mapping validation.
- `PersistenceIntegrationTest` covers user identity lookup, unique Entra identity enforcement, note ownership scoping, active-note filtering, active-note ordering by `updatedAt DESC, id ASC`, and optimistic-lock revision updates.
- `ResourceServerIntegrationTest` covers anonymous health, OpenAPI, and Swagger UI access, unauthenticated `/api/me` rejection, missing-scope rejection, Spring's `scp` to `SCOPE_` JWT authority conversion, Notes API audience-validator accept/reject behavior, valid scoped `/api/me` provisioning, repeated identity idempotency, mismatched tenant rejection, missing and malformed identity-claim rejection, and concurrent first-use provisioning.
- `NotesApiIntegrationTest` exercises real Spring MVC, Spring Security, current-user resolution, JPA, Flyway, and PostgreSQL wiring for Notes API authentication and scope enforcement, creation and ownership, empty content, title validation, UUID conflicts, active-list ordering, hidden cross-user and tombstoned reads, strong ETags, strict `If-Match` parsing, successful and stale updates, soft deletion, stale deletes, and sanitized problem responses.
- `OpenApiContractIntegrationTest` makes focused assertions against `/v3/api-docs` for metadata, bearer security, operation IDs, statuses, media types, headers, problem responses, strict `If-Match` documentation, schema requiredness and nullability, validation limits, and absence of tenant-specific values.
- Tests run against PostgreSQL, not H2.

## Verification

- Primary command on Windows: `gradlew.bat test` from `backend/`.
- Standard lifecycle verification command: `gradlew.bat check` from `backend/`.

## Deferred work

- Android MSAL wiring, Android app registration, redirect configuration, signing-certificate hash setup, and live end-to-end token verification remain deferred.
- Offline synchronization endpoints, cursor design and queries, batch synchronization, conflict-resolution APIs, and related sync infrastructure remain deferred.
- Android networking and frontend integration for the Notes CRUD API remain deferred.
- Sharing tables and sharing behavior remain deferred.
- Frontend changes are not part of this backend baseline.
