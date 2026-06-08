package dev.vvanttinen.notes.auth

import android.content.Context
import dev.vvanttinen.notes.BuildConfig
import dev.vvanttinen.notes.R
import dev.vvanttinen.notes.auth.msal.MsalSingleAccountGateway

class AuthFeature private constructor(
    val controller: NotesAuthController
) {
    companion object {
        fun create(applicationContext: Context): AuthFeature {
            val config = EntraAuthConfig.fromValues(
                isConfigured = BuildConfig.NOTES_ENTRA_CONFIGURED,
                notesApiScope = BuildConfig.NOTES_ENTRA_API_SCOPE,
                msalAuthority = BuildConfig.NOTES_ENTRA_AUTHORITY
            )
            return AuthFeature(
                controller = DefaultNotesAuthController(
                    config = config,
                    gateway = MsalSingleAccountGateway(
                        applicationContext = applicationContext,
                        configResourceId = R.raw.msal_auth_config,
                        configuredAuthority = config.msalAuthority
                    )
                )
            )
        }
    }
}
