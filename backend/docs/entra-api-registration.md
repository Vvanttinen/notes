# Microsoft Entra API Registration

This backend is a single-tenant OAuth 2.0 resource server. It validates bearer access tokens issued for the Notes API and does not need an application secret for token validation.

## API Registration

1. Create a Microsoft Entra app registration named `Notes API`.
2. Select **Accounts in this organizational directory only**.
3. Do not add a redirect URI to the API registration.
4. In **Expose an API**, use the default Application ID URI form:

   ```text
   api://<NOTES_ENTRA_API_CLIENT_ID>
   ```

5. Add one delegated scope named `access_as_user`.
6. Configure the registration to issue v2 access tokens for this API.

## Backend Environment

Set these local environment variables when running the backend against live Entra tokens:

```text
NOTES_ENTRA_TENANT_ID=<tenant UUID>
NOTES_ENTRA_API_CLIENT_ID=<Notes API application client UUID>
```

Do not commit real tenant IDs, client IDs, access tokens, redirect URIs, signing material, certificates, client secrets, or generated local environment files.

## Deferred Client Setup

The separate Android public-client registration is intentionally deferred to the frontend MSAL task, including its signing-certificate hash, redirect URI configuration, and delegated permission grant for `access_as_user`.
