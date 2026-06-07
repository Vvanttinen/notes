package dev.vvanttinen.notes.auth

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

data class EntraAuthConfig(
    val isConfigured: Boolean,
    val notesApiScope: String
) {
    companion object {
        fun fromValues(isConfigured: Boolean, notesApiScope: String): EntraAuthConfig =
            EntraAuthConfig(
                isConfigured = isConfigured && notesApiScope.isNotBlank(),
                notesApiScope = notesApiScope.trim()
            )
    }
}

data class AuthAccount(
    val authority: String,
    val accountId: String
)

sealed interface AuthState {
    data object Initializing : AuthState
    data object Unconfigured : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val accountKey: String) : AuthState
    data class Error(val category: AuthErrorCategory) : AuthState
}

enum class AuthErrorCategory {
    Canceled,
    Client,
    Configuration,
    InteractionRequired,
    Network,
    Service,
    Unknown
}

sealed interface SilentTokenResult {
    data class Success(val accessToken: String) : SilentTokenResult
    data object InteractionRequired : SilentTokenResult
    data object SignedOut : SilentTokenResult
    data class Failure(val category: AuthErrorCategory) : SilentTokenResult
}

sealed interface GatewayResult<out T> {
    data class Success<T>(val value: T) : GatewayResult<T>
    data class Failure(val category: AuthErrorCategory) : GatewayResult<Nothing>
    data object Canceled : GatewayResult<Nothing>
    data object InteractionRequired : GatewayResult<Nothing>
}

interface NotesAuthGateway {
    suspend fun initialize(): GatewayResult<AuthAccount?>
    suspend fun refreshCurrentAccount(): GatewayResult<AuthAccount?>
    suspend fun signIn(activity: Activity, scopes: List<String>): GatewayResult<AuthAccount>
    suspend fun signOut(): GatewayResult<Unit>
    suspend fun acquireTokenSilently(scopes: List<String>): GatewayResult<String>
}

interface NotesAuthController {
    val authState: StateFlow<AuthState>

    suspend fun initialize()
    suspend fun refreshCurrentAccount()
    suspend fun signIn(activity: Activity)
    suspend fun signOut()
    suspend fun acquireNotesApiAccessTokenSilently(): SilentTokenResult
}
