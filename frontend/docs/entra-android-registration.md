# Microsoft Entra Android Registration

This frontend uses MSAL Android as a public client for the Notes API delegated `access_as_user` scope. Keep the existing `Notes API` app registration separate from the Android app registration.

## Portal Setup

- Create a new app registration named `Notes Android`.
- Supported account type: `Accounts in this organizational directory only`.
- Platform type: `Android`.
- Package name: `dev.vvanttinen.notes`.
- Android signature hash: use the development signing hash initially.
- API permission: add the separate `Notes API` delegated `access_as_user` permission.
- Client secret: none. Android is a public client.

After adding the Android platform, copy the portal-generated redirect URI. It has the shape:

```text
msauth://dev.vvanttinen.notes/<URL-encoded-signature-hash>
```

Encoding matters:

- MSAL JSON `redirect_uri` uses the portal-generated URL-encoded signature hash.
- AndroidManifest `BrowserTabActivity` path uses `/` plus the non-URL-encoded signature hash.

Release or Play signing requires an additional Android platform redirect configuration with the release signing hash before release builds can authenticate.

## Local Configuration

Do not commit real tenant IDs, client IDs, redirect URIs, signing hashes, access tokens, or secrets.

Supply local values through environment variables or matching untracked Gradle properties. Environment variables take precedence over Gradle properties with the same name.

```text
NOTES_ENTRA_TENANT_ID
NOTES_ENTRA_ANDROID_CLIENT_ID
NOTES_ENTRA_ANDROID_REDIRECT_URI
NOTES_ENTRA_ANDROID_SIGNATURE_HASH
NOTES_ENTRA_API_SCOPE
```

Expected values:

```text
NOTES_ENTRA_TENANT_ID
    Entra workforce tenant UUID

NOTES_ENTRA_ANDROID_CLIENT_ID
    Application/client ID of the Notes Android app registration

NOTES_ENTRA_ANDROID_REDIRECT_URI
    Portal-generated Android redirect URI

NOTES_ENTRA_ANDROID_SIGNATURE_HASH
    Non-URL-encoded signature hash for the Android manifest path

NOTES_ENTRA_API_SCOPE
    api://<NOTES_API_CLIENT_ID>/access_as_user
```

For untracked Gradle properties, place the same names in a user-level file such as `~/.gradle/gradle.properties`, not in Git.

## Build Behavior

The Gradle build generates `msal_auth_config.json` under `build/` instead of committing environment-specific values under `src/main/res/raw`.

Without all local values, the app builds with a safe placeholder resource and enters the explicit `Unconfigured` state at runtime. MSAL initialization is skipped until all required values are present.

## Manual Smoke Test

1. Configure the five local values above.
2. Rebuild and install the debug app.
3. Launch the app and select `Sign in with Microsoft`.
4. Complete browser-delegated Microsoft sign-in.
5. Confirm the app shows a generic signed-in state.
6. Relaunch the app and confirm cached-account restoration.
7. Return the app to the foreground and confirm it remains signed in.
8. Select `Test silent token acquisition` and confirm only a sanitized success or failure category is shown.
9. Select `Sign out` and confirm the app returns to the signed-out state.

The silent-token smoke test does not call `/api/me`; authenticated backend probing remains deferred.
