package dev.vvanttinen.notes.auth

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

interface NotesAuthController {
    val authState: StateFlow<AuthState>

    suspend fun initialize()
    suspend fun refreshCurrentAccount()
    suspend fun signIn(activity: Activity)
    suspend fun signOut()
    suspend fun acquireNotesApiAccessTokenSilently(): SilentTokenResult
}

interface NotesAuthGateway {
    suspend fun initialize(): GatewayResult<AuthAccount?>
    suspend fun refreshCurrentAccount(): GatewayResult<AuthAccount?>
    suspend fun signIn(activity: Activity, scopes: List<String>): GatewayResult<AuthAccount>
    suspend fun signOut(): GatewayResult<Unit>
    suspend fun acquireTokenSilently(scopes: List<String>): GatewayResult<String>
}
