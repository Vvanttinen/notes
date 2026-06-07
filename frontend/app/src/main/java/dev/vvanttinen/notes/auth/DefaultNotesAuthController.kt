package dev.vvanttinen.notes.auth

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultNotesAuthController(
    private val config: EntraAuthConfig,
    private val gateway: NotesAuthGateway
) : NotesAuthController {
    private val operationMutex = Mutex()
    private val mutableAuthState = MutableStateFlow<AuthState>(AuthState.Initializing)

    override val authState: StateFlow<AuthState> = mutableAuthState.asStateFlow()

    override suspend fun initialize() {
        operationMutex.withLock {
            if (!config.isConfigured) {
                mutableAuthState.value = AuthState.Unconfigured
                return
            }

            mutableAuthState.value = AuthState.Initializing
            when (val result = gateway.initialize()) {
                is GatewayResult.Success -> updateAccountState(result.value)
                is GatewayResult.Failure -> mutableAuthState.value = AuthState.Error(result.category)
                GatewayResult.Canceled -> mutableAuthState.value = AuthState.Error(AuthErrorCategory.Canceled)
                GatewayResult.InteractionRequired -> mutableAuthState.value = AuthState.SignedOut
            }
        }
    }

    override suspend fun refreshCurrentAccount() {
        operationMutex.withLock {
            if (!config.isConfigured) {
                mutableAuthState.value = AuthState.Unconfigured
                return
            }

            when (val result = gateway.refreshCurrentAccount()) {
                is GatewayResult.Success -> updateAccountState(result.value)
                is GatewayResult.Failure -> mutableAuthState.value = AuthState.Error(result.category)
                GatewayResult.Canceled -> mutableAuthState.value = AuthState.Error(AuthErrorCategory.Canceled)
                GatewayResult.InteractionRequired -> mutableAuthState.value = AuthState.SignedOut
            }
        }
    }

    override suspend fun signIn(activity: Activity) {
        operationMutex.withLock {
            if (!config.isConfigured) {
                mutableAuthState.value = AuthState.Unconfigured
                return
            }

            when (val result = gateway.signIn(activity, listOf(config.notesApiScope))) {
                is GatewayResult.Success -> updateAccountState(result.value)
                is GatewayResult.Failure -> mutableAuthState.value = AuthState.Error(result.category)
                GatewayResult.Canceled -> mutableAuthState.value = AuthState.Error(AuthErrorCategory.Canceled)
                GatewayResult.InteractionRequired -> mutableAuthState.value = AuthState.Error(AuthErrorCategory.InteractionRequired)
            }
        }
    }

    override suspend fun signOut() {
        operationMutex.withLock {
            if (!config.isConfigured) {
                mutableAuthState.value = AuthState.Unconfigured
                return
            }

            when (val result = gateway.signOut()) {
                is GatewayResult.Success -> mutableAuthState.value = AuthState.SignedOut
                is GatewayResult.Failure -> mutableAuthState.value = AuthState.Error(result.category)
                GatewayResult.Canceled -> mutableAuthState.value = AuthState.Error(AuthErrorCategory.Canceled)
                GatewayResult.InteractionRequired -> mutableAuthState.value = AuthState.SignedOut
            }
        }
    }

    override suspend fun acquireNotesApiAccessTokenSilently(): SilentTokenResult {
        return operationMutex.withLock {
            if (!config.isConfigured) {
                return@withLock SilentTokenResult.Failure(AuthErrorCategory.Configuration)
            }
            if (mutableAuthState.value !is AuthState.SignedIn) {
                return@withLock SilentTokenResult.SignedOut
            }

            when (val result = gateway.acquireTokenSilently(listOf(config.notesApiScope))) {
                is GatewayResult.Success -> SilentTokenResult.Success(result.value)
                is GatewayResult.Failure -> SilentTokenResult.Failure(result.category)
                GatewayResult.Canceled -> SilentTokenResult.Failure(AuthErrorCategory.Canceled)
                GatewayResult.InteractionRequired -> SilentTokenResult.InteractionRequired
            }
        }
    }

    private fun updateAccountState(account: AuthAccount?) {
        mutableAuthState.value = if (account == null) {
            AuthState.SignedOut
        } else {
            AuthState.SignedIn(
                accountKey = AccountKeyDeriver.derive(
                    authority = account.authority,
                    accountId = account.accountId
                )
            )
        }
    }
}
