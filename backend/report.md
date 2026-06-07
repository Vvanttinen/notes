# TP-006 Backend Entra Resource Server Report

## 1. Implementation Summary

- Added sanitized Microsoft Entra resource-server configuration using `NOTES_ENTRA_TENANT_ID` and `NOTES_ENTRA_API_CLIENT_ID`.
- Added an explicit stateless Spring Security filter chain for health and `/api/**` authorization.
- Added production JWT decoder configuration with tenant-specific issuer validation and Notes API audience validation.
- Added `CurrentUserService` to validate `tid` and `oid`, resolve the local user, and provision on first use.
- Added protected `GET /api/me`, returning only the local `userId`.
- Added sanitized Entra setup documentation at `docs/entra-api-registration.md`.
- Added deterministic Testcontainers-backed security and provisioning tests.
- The required resource-server implementation and test dependencies were already present in `build.gradle.kts`; no dependency edit was needed.

## 2. Security Rules and Validated Claims

- `GET /actuator/health` and health subpaths are anonymous.
- `/api/**` requires an authenticated JWT with `SCOPE_access_as_user`.
- Any other request is denied.
- CSRF is disabled for the bearer-token API.
- Session management is stateless.
- Form login, HTTP Basic, OAuth login redirects, and server-side sessions are not configured.
- Production JWT validation covers signature, issuer, timestamps, and audience.
- The current-user boundary requires nonblank UUID `tid` and `oid` claims.
- `tid` must match the configured single tenant.

## 3. Concurrent JIT Provisioning Safety

User provisioning uses PostgreSQL:

```sql
INSERT INTO users (id, entra_tenant_id, entra_object_id)
VALUES (:id, :entraTenantId, :entraObjectId)
ON CONFLICT ON CONSTRAINT uq_users_entra_identity DO NOTHING
```

The service then reads back the row by `(entra_tenant_id, entra_object_id)` in the same transaction. Concurrent first-use calls converge on the same row through the existing uniqueness constraint without relying on fragile check-then-insert behavior or leaving a transaction rollback-only after a duplicate-key exception.

## 4. Sanitized Entra Documentation

- `docs/entra-api-registration.md`

## 5. Tests Added and Commands Run

Added `ResourceServerIntegrationTest` coverage for:

- context startup with deterministic test Entra properties and no live Entra dependency;
- anonymous health access;
- unauthenticated `/api/me` rejection;
- missing `access_as_user` rejection;
- Spring's real `scp` to `SCOPE_access_as_user` JWT authority conversion;
- custom Notes API audience-validator acceptance and rejection;
- valid scoped `/api/me` user provisioning;
- repeated `/api/me` idempotency for the same identity;
- mismatched tenant rejection;
- missing and malformed identity claim rejection;
- concurrent first-use provisioning.

Commands run:

```text
.\gradlew.bat test
.\gradlew.bat check
```

Results:

- `.\gradlew.bat test` passed.
- `.\gradlew.bat check` passed.

## 6. Sensitive-Value Inspection

Inspected the final backend tree, excluding build and IDE output, for tokens, secrets, signing material, certificates, redirect URIs, private keys, local environment files, real GUID-shaped issuer/application identifiers, and committed bearer values.

Result:

- No generated local environment files or secret/certificate files were found.
- Matches were limited to sanitized documentation warning text, the literal OAuth error code `invalid_token`, and deterministic test UUID placeholders.
- No real tenant ID, client ID, access token, redirect URI, signing hash, certificate, or secret was identified.

## 7. Deviations

- No schema migration was added because the existing `users` table already has the required `(entra_tenant_id, entra_object_id)` uniqueness guarantee.
- No dependency edit was made because the required Spring Boot resource-server and resource-server test starters were already present.
- Mock MVC request helpers grant `SCOPE_access_as_user` explicitly so endpoint tests stay focused on authorization and application claim-validation boundaries; focused tests separately verify Spring's `scp` authority conversion and the custom audience validator.

## 8. Refreshed Current-State Summary

`context/current-state.md` now describes:

- resource-server dependency and configuration approach;
- required sanitized environment variable names;
- endpoint authorization rules;
- `/api/me` behavior;
- `(tid, oid)` to local user mapping;
- PostgreSQL-safe just-in-time provisioning;
- deterministic authentication-test coverage;
- deferred Android MSAL wiring, live end-to-end token verification, private notes CRUD, and synchronization work.
