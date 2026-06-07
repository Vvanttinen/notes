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
- BuildConfig generation is enabled for sanitized auth configuration flags.
- `android.disallowKotlinSourceSets=false` is set so KSP-generated sources work with this AGP built-in Kotlin project.

## Dependencies and repositories

- Room dependencies are configured through KSP.
- MSAL Android is declared as `com.microsoft.identity.client:msal:8.+`.
- Latest observed resolved MSAL version during verification: `8.3.3`.
- The Gradle dependency repositories include `google()`, `mavenCentral()`, and the MSAL-required Duo SDK feed at `https://pkgs.dev.azure.com/MicrosoftDeviceSDK/DuoSDK-Public/_packaging/Duo-SDK-Feed/maven/v1`.
- `androidx.lifecycle:lifecycle-runtime-compose` is present for lifecycle-aware Compose state collection.
- `kotlinx-coroutines-test` is present for deterministic auth-controller unit tests.

## Android application

- The app module is `app`.
- `MainActivity` enables edge-to-edge rendering and shows a minimal Compose MSAL authentication harness inside `NotesTheme`.
- `NotesApplication` creates the production Room database, local repository, and authentication controller lazily.
- Theme files exist under `dev.vvanttinen.notes.ui.theme`.
- Launcher icon and generated XML resource baseline files are present.

## MSAL configuration

- The app uses MSAL Android single-account mode with `ISingleAccountPublicClientApplication` only.
- The generated MSAL raw resource is `R.raw.msal_auth_config`.
- Gradle generates `msal_auth_config.json` under `app/build/generated/msalAuthConfig/res/raw/`; no environment-specific JSON is committed under `src/main/res/raw`.
- Configured builds generate a tenant-specific Azure AD MyOrg authority with one default authority, `account_mode` set to `SINGLE`, `authorization_user_agent` set to `DEFAULT`, and `broker_redirect_uri_registered` set to `true`.
- Unconfigured builds generate a non-sensitive placeholder MSAL resource for compilation, but runtime config checks prevent MSAL initialization.
- Local configuration inputs are read from environment variables first, then same-named Gradle properties:
  - `NOTES_ENTRA_TENANT_ID`
  - `NOTES_ENTRA_ANDROID_CLIENT_ID`
  - `NOTES_ENTRA_ANDROID_REDIRECT_URI`
  - `NOTES_ENTRA_ANDROID_SIGNATURE_HASH`
  - `NOTES_ENTRA_API_SCOPE`
- The expected Notes API delegated scope is `api://<NOTES_API_CLIENT_ID>/access_as_user`.
- No real tenant IDs, client IDs, redirect URIs, signing hashes, tokens, secrets, or signing material are committed.

## Android manifest authentication wiring

- The manifest declares `android.permission.INTERNET` and `android.permission.ACCESS_NETWORK_STATE`.
- The manifest declares `com.microsoft.identity.client.BrowserTabActivity` with a narrow `msauth` callback intent filter.
- The callback host is the package name `dev.vvanttinen.notes`.
- The callback path is generated from the non-URL-encoded signature hash with a leading `/`.
- The MSAL JSON redirect URI uses the portal-generated URL-encoded signature hash.

## Authentication boundary

- Authentication code lives under `dev.vvanttinen.notes.auth`.
- `NotesAuthController` exposes:
  - `authState: StateFlow<AuthState>`
  - `initialize()`
  - `refreshCurrentAccount()`
  - `signIn(activity)`
  - `signOut()`
  - `acquireNotesApiAccessTokenSilently()`
- `DefaultNotesAuthController` owns app-level auth state transitions and checks unconfigured mode before calling the MSAL gateway.
- `MsalSingleAccountGateway` is the only production class that directly uses MSAL callbacks and MSAL account types.
- PCA initialization is guarded by a mutex and cached after first creation.
- Foreground refresh uses the single-account current-account callback and updates state when the MSAL account is removed or replaced.
- Interactive sign-in uses `SignInParameters` and requests only the configured Notes API scope.
- Silent token acquisition uses `AcquireTokenSilentParameters` and never launches interactive UI.
- Sign-out uses the single-account sign-out callback and deselects the current local partition without deleting Room rows.

## Authentication state and token results

- Observable auth states are `Initializing`, `Unconfigured`, `SignedOut`, `SignedIn(accountKey)`, and `Error(category)`.
- The Compose harness does not display local account keys, account IDs, usernames, emails, tenant IDs, client IDs, redirect URIs, signing hashes, claims, or access tokens.
- Silent token acquisition returns `Success(accessToken)`, `InteractionRequired`, `SignedOut`, or `Failure(category)`.
- Access tokens are returned only transiently to the caller and are not persisted, logged, parsed for authorization, or rendered in UI state.
- Error categories are sanitized and do not include raw identifiers or token material.

## Local account partition mapping

- The selected Room partition remains the existing opaque `account_key`.
- `AccountKeyDeriver` deterministically derives the local partition key from normalized MSAL account authority plus MSAL account ID.
- The derivation uses SHA-256, lowercase hexadecimal encoding, and the `v1:` prefix.
- Authority normalization trims whitespace, removes trailing slashes, and lowercases the URI scheme and host.
- Email, username, display name, mutable profile fields, token parsing, and claims parsing are not used for the local account key.
- The derived key is a local partition selector only and is not an API credential.

## Local Room persistence

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
- Pending-change indexing is scoped by account and pending-change ordering columns: `account_key ASC`, `updated_at ASC`, `id ASC`.

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

## Compose authentication harness

- `Initializing` shows a progress indicator while checking cached Microsoft sign-in state.
- `Unconfigured` shows local setup guidance and a retry action.
- `SignedOut` shows a `Sign in with Microsoft` action.
- `SignedIn` shows a generic signed-in indication, `Test silent token acquisition`, and `Sign out`.
- `Error` shows a sanitized error category with retry and sign-out paths.
- Silent-token smoke-test output is limited to sanitized success, interaction-required, signed-out, or failure categories.

## Documentation

- Sanitized Android app-registration documentation is in `docs/entra-android-registration.md`.
- The documentation covers the separate `Notes Android` public-client registration, package name, development signing hash, delegated `Notes API / access_as_user` permission, no client secret, redirect URI encoding differences, local configuration inputs, manual smoke-test steps, release signing follow-up, and no-secrets guidance.

## Testing and verification

- Unit test command: `gradlew.bat testDebugUnitTest` from `frontend/`.
- Lint command: `gradlew.bat lintDebug` from `frontend/`.
- Build command: `gradlew.bat assembleDebug` from `frontend/`.
- Connected database test command: `gradlew.bat connectedDebugAndroidTest` from `frontend/`.
- Auth unit tests cover missing configuration, configured initialization with and without a cached account, deterministic account-key selection, sign-in success, sign-in cancellation, sign-out partition deselection, foreground refresh after account removal and replacement, silent-token success, silent-token interaction-required behavior, silent-token signed-out behavior, and config scope completeness.
- Account-key tests cover repeated derivation stability and different authority/account ID separation.
- Room database behavior is covered by Android instrumented in-memory database tests in `LocalNoteDatabaseTest`.
- Instrumented tests cover UUID preservation, updates, active ordering, title/body search, tombstone exclusion, pending tombstone inclusion, account partitioning, nullable and zero server revisions, sequential and concurrent local mutation version advancement, stale acknowledgement rejection after newer edits and tombstones, wrong-account acknowledgement rejection, title-length boundaries, sync-state transitions, explicit tombstone cleanup, and type converters.
- Latest frontend verification on this snapshot passed `gradlew.bat testDebugUnitTest`, `gradlew.bat lintDebug`, `gradlew.bat assembleDebug`, and `gradlew.bat connectedDebugAndroidTest`.
- Connected tests ran on `SM-A528B - 14`.
- MSAL dependency insight confirmed `com.microsoft.identity.client:msal:8.+ -> 8.3.3`.

## Explicitly deferred

- Live Android Microsoft sign-in smoke testing remains deferred unless local Entra values are supplied outside Git.
- Authenticated backend `/api/me` probing remains deferred.
- Networking libraries, backend API clients, REST DTOs, backend CRUD integration, and remote repositories are not implemented.
- Product CRUD screens, note-list screens, editor screens, and search feature screens are not implemented beyond the authentication harness.
- WorkManager, background synchronization, sync cursors, outbox tables, retry policies, and conflict resolution are not implemented.
- Access-token attachment to future HTTP requests is deferred to the future networking baseline.
- Backend files are not modified by this frontend task.
