# Frontend current state

## Purpose

- `Notes` is the Android application root for the Notes product.
- The frontend is an independent Gradle root at `frontend/`.
- This root contains only the Android frontend; no repository-level Gradle aggregation is configured.

## Identifiers

- Product name: `Notes`.
- Android namespace: `dev.vvanttinen.notes`.
- Android application ID: `dev.vvanttinen.notes`.
- Kotlin package root: `dev.vvanttinen.notes`.

## Toolchain and build

- Gradle settings file: `settings.gradle.kts`.
- Gradle root project name: `Notes`.
- Gradle wrapper version: `9.4.1`.
- Android Gradle Plugin version: `9.2.1`.
- Kotlin Compose plugin version: `2.2.10`.
- KSP plugin version: `2.2.10-2.0.2`.
- Gradle toolchain resolver plugin version: `1.0.0`.
- Java source compatibility: `11`.
- Java target compatibility: `11`.
- Generated Gradle daemon JVM properties are present and specify `toolchainVersion=21`; no machine-specific JDK path is recorded.
- Minimum SDK: `26`.
- Target SDK: `36`.
- Compile SDK: Android `36.1`.
- Compose is enabled through `buildFeatures.compose = true`.
- `android.disallowKotlinSourceSets=false` is set so KSP-generated sources work with this AGP built-in Kotlin project.

## Android application

- The app module is `app`.
- `MainActivity` enables edge-to-edge rendering and shows a minimal Compose shell inside `NotesTheme`.
- `NotesApplication` creates the production Room database and a small local repository instance lazily.
- Theme files exist under `dev.vvanttinen.notes.ui.theme`.
- Launcher icon and generated XML resource baseline files are present.

## Local Room persistence

- Room dependencies are configured through KSP.
- Database class: `dev.vvanttinen.notes.data.local.NotesDatabase`.
- Database name: `notes.db`.
- Database version: `1`.
- Schema export path: `app/schemas/dev.vvanttinen.notes.data.local.NotesDatabase/1.json`.
- Entity table: `local_notes`.
- Entity type: `LocalNoteEntity`.
- Composite primary key: `(account_key, id)`.
- `account_key` is an opaque local account partition key and is required in every DAO read, mutation, and cleanup operation.
- `id` is a client-generated or backend-preserved note UUID stored as stable text.
- `created_at`, `updated_at`, and nullable `deleted_at` are Kotlin `Instant` values stored as epoch milliseconds.
- `server_revision` is nullable; `null` means the backend has not accepted the note yet, while `0` is a valid synchronized backend revision.
- `local_mutation_version` is a local-only upload token incremented atomically for local creates, edits, and tombstones.
- `sync_state` is a stable text enum with values `SYNCED`, `PENDING_UPSERT`, and `PENDING_DELETE`.
- `LocalNote.createNew` generates client-side UUIDs for new offline notes unless a caller supplies a specific UUID.
- Active-note indexing is scoped by account and tombstone/order columns: `account_key ASC`, `deleted_at ASC`, `updated_at DESC`, `id ASC`.
- Pending-change indexing is scoped by account and pending-change ordering columns: `account_key ASC`, `updated_at ASC`, `id ASC`. The index deliberately does not lead with `sync_state` because pending lookup filters with `sync_state != 'SYNCED'`, which is not an equality predicate.

## DAO and repository capabilities

- `LocalNoteDao` observes active notes for one account ordered by `updated_at DESC, id ASC`.
- `LocalNoteDao` observes and loads a single active note by `account_key` and `id`.
- `LocalNoteDao` saves local creates and edits in a Room transaction that reads the current account-scoped row and writes the next `local_mutation_version` atomically with the local `PENDING_UPSERT` state.
- `LocalNoteDao` tombstones existing notes with a single account-scoped SQL update that sets `PENDING_DELETE` and increments `local_mutation_version` with SQLite arithmetic.
- `LocalNoteDao` searches active title/body text with `LIKE`, lists pending changes, marks notes synchronized, and deletes acknowledged tombstones.
- Synchronization acknowledgement is an account-scoped compare-and-set guarded by the uploaded `local_mutation_version`; stale backend acknowledgements and wrong-account acknowledgements return failure and cannot clear newer pending local changes.
- Tombstoned notes are excluded from active listings, single active-note reads, and search results.
- Pending-change lookup includes pending tombstones.
- Acknowledged tombstones are removed only through the explicit account-scoped cleanup method.
- `LocalNoteRepository` exposes the small local boundary for observing, loading, saving local edits, tombstoning, searching, listing pending changes, acknowledging synchronization, and cleaning up acknowledged tombstones.
- `RoomLocalNoteRepository` validates the 255-character title application boundary, delegates local create/edit persistence to the DAO transaction so version advancement is atomic, and requires the uploaded local mutation version when acknowledging synchronization.

## Type converters

- `NotesTypeConverters` converts:
  - `UUID` to and from text;
  - `Instant` to and from epoch milliseconds;
  - `NoteSyncState` to and from stable enum-name text.

## Testing and verification

- Unit test command: `gradlew.bat testDebugUnitTest` from `frontend/`.
- Lint command: `gradlew.bat lintDebug` from `frontend/`.
- Build command: `gradlew.bat assembleDebug` from `frontend/`.
- Connected database test command: `gradlew.bat connectedDebugAndroidTest` from `frontend/`.
- Room database behavior is covered by Android instrumented in-memory database tests in `LocalNoteDatabaseTest`.
- Instrumented tests cover UUID preservation, updates, active ordering, title/body search, tombstone exclusion, pending tombstone inclusion, account partitioning, nullable and zero server revisions, sequential and concurrent local mutation version advancement, stale acknowledgement rejection after newer edits and tombstones, wrong-account acknowledgement rejection, title-length boundaries, sync-state transitions, explicit tombstone cleanup, and type converters.
- Latest frontend verification on this snapshot passed `gradlew.bat assembleDebug`, `gradlew.bat lintDebug`, `gradlew.bat testDebugUnitTest`, and `gradlew.bat connectedDebugAndroidTest`; connected tests require an attached device or emulator.

## Explicitly deferred

- MSAL, Microsoft Entra ID configuration, sign-in, sign-out, token acquisition, and token parsing are not implemented.
- Client IDs, tenant values, redirect URIs, scopes, secrets, and access tokens are not configured or committed.
- Networking libraries, backend API clients, REST DTOs, backend CRUD integration, and remote repositories are not implemented.
- WorkManager, background synchronization, sync cursors, outbox tables, retry policies, and conflict resolution are not implemented.
- Product CRUD screens, note-list screens, editor screens, and search feature screens are not implemented beyond the minimal Compose shell.
- Backend files are not modified by this frontend task.
