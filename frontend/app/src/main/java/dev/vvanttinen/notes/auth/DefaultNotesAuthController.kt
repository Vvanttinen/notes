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
    private var stateGeneration = 0L

    override val authState: StateFlow<AuthState> = mutableAuthState.asStateFlow()

    override suspend fun initialize() {
        operationMutex.withLock {
            if (!config.isConfigured) {
                setAuthState(AuthState.Unconfigured)
                return
            }

            setAuthState(AuthState.Initializing)
            when (val result = gateway.initialize()) {
                is GatewayResult.Success -> updateAccountState(result.value)
                is GatewayResult.Failure -> setAuthState(AuthState.Error(result.category))
                GatewayResult.Canceled -> setAuthState(AuthState.Error(AuthErrorCategory.Canceled))
                GatewayResult.InteractionRequired -> setAuthState(AuthState.SignedOut)
            }
        }
    }

    override suspend fun refreshCurrentAccount() {
        val refreshStartedAtGeneration = stateGeneration
        operationMutex.withLock {
            if (!config.isConfigured) {
                setAuthState(AuthState.Unconfigured)
                return
            }
            if (stateGeneration != refreshStartedAtGeneration) {
                return
            }

            when (val result = gateway.refreshCurrentAccount()) {
                is GatewayResult.Success -> updateAccountState(result.value)
                is GatewayResult.Failure -> setAuthState(AuthState.Error(result.category))
                GatewayResult.Canceled -> setAuthState(AuthState.Error(AuthErrorCategory.Canceled))
                GatewayResult.InteractionRequired -> setAuthState(AuthState.SignedOut)
            }
        }
    }

    override suspend fun signIn(activity: Activity) {
        operationMutex.withLock {
            if (!config.isConfigured) {
                setAuthState(AuthState.Unconfigured)
                return
            }

            when (val result = gateway.signIn(activity, listOf(config.notesApiScope))) {
                is GatewayResult.Success -> updateAccountState(result.value)
                is GatewayResult.Failure -> setAuthState(AuthState.Error(result.category))
                GatewayResult.Canceled -> setAuthState(AuthState.Error(AuthErrorCategory.Canceled))
                GatewayResult.InteractionRequired -> setAuthState(AuthState.Error(AuthErrorCategory.InteractionRequired))
            }
        }
    }

    override suspend fun signOut() {
        operationMutex.withLock {
            if (!config.isConfigured) {
                setAuthState(AuthState.Unconfigured)
                return
            }

            when (val result = gateway.signOut()) {
                is GatewayResult.Success -> setAuthState(AuthState.SignedOut)
                is GatewayResult.Failure -> setAuthState(AuthState.Error(result.category))
                GatewayResult.Canceled -> setAuthState(AuthState.Error(AuthErrorCategory.Canceled))
                GatewayResult.InteractionRequired -> setAuthState(AuthState.SignedOut)
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
        if (account == null) {
            setAuthState(AuthState.SignedOut)
        } else {
            val accountKey = runCatching {
                AccountKeyDeriver.derive(
                    authority = account.authority,
                    accountId = account.accountId
                )
            }.getOrElse {
                setAuthState(AuthState.Error(AuthErrorCategory.Configuration))
                return
            }
            setAuthState(AuthState.SignedIn(accountKey = accountKey))
        }
    }

    private fun setAuthState(authState: AuthState) {
        mutableAuthState.value = authState
        stateGeneration += 1
    }
}
