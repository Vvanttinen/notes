package dev.vvanttinen.notes.auth

import android.app.Activity
import android.content.Context
import androidx.annotation.RawRes
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.exception.MsalClientException
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalServiceException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class MsalSingleAccountGateway(
    private val applicationContext: Context,
    @param:RawRes private val configResourceId: Int
) : NotesAuthGateway {
    private val initMutex = Mutex()
    private var application: ISingleAccountPublicClientApplication? = null
    private var activeAccount: IAccount? = null

    override suspend fun initialize(): GatewayResult<AuthAccount?> {
        return when (val app = getApplication()) {
            is GatewayResult.Success -> loadCurrentAccount(app.value)
            is GatewayResult.Failure -> app
            GatewayResult.Canceled -> GatewayResult.Canceled
            GatewayResult.InteractionRequired -> GatewayResult.InteractionRequired
        }
    }

    override suspend fun refreshCurrentAccount(): GatewayResult<AuthAccount?> {
        return when (val app = getApplication()) {
            is GatewayResult.Success -> loadCurrentAccount(app.value)
            is GatewayResult.Failure -> app
            GatewayResult.Canceled -> GatewayResult.Canceled
            GatewayResult.InteractionRequired -> GatewayResult.InteractionRequired
        }
    }

    override suspend fun signIn(activity: Activity, scopes: List<String>): GatewayResult<AuthAccount> {
        val appResult = getApplication()
        if (appResult !is GatewayResult.Success) {
            return when (appResult) {
                is GatewayResult.Failure -> appResult
                GatewayResult.Canceled -> GatewayResult.Canceled
                GatewayResult.InteractionRequired -> GatewayResult.InteractionRequired
                is GatewayResult.Success -> error("unreachable")
            }
        }

        return suspendCancellableCoroutine { continuation ->
            val parameters = SignInParameters.builder()
                .withActivity(activity)
                .withScopes(scopes)
                .withCallback(object : AuthenticationCallback {
                    override fun onSuccess(authenticationResult: IAuthenticationResult) {
                        activeAccount = authenticationResult.account
                        continuation.resume(GatewayResult.Success(authenticationResult.account.toAuthAccount()))
                    }

                    override fun onError(exception: MsalException) {
                        continuation.resume(GatewayResult.Failure(exception.toAuthErrorCategory()))
                    }

                    override fun onCancel() {
                        continuation.resume(GatewayResult.Canceled)
                    }
                })
                .build()

            appResult.value.signIn(parameters)
        }
    }

    override suspend fun signOut(): GatewayResult<Unit> {
        val appResult = getApplication()
        if (appResult !is GatewayResult.Success) {
            return when (appResult) {
                is GatewayResult.Failure -> appResult
                GatewayResult.Canceled -> GatewayResult.Canceled
                GatewayResult.InteractionRequired -> GatewayResult.InteractionRequired
                is GatewayResult.Success -> error("unreachable")
            }
        }

        return suspendCancellableCoroutine { continuation ->
            appResult.value.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                override fun onSignOut() {
                    activeAccount = null
                    continuation.resume(GatewayResult.Success(Unit))
                }

                override fun onError(exception: MsalException) {
                    continuation.resume(GatewayResult.Failure(exception.toAuthErrorCategory()))
                }
            })
        }
    }

    override suspend fun acquireTokenSilently(scopes: List<String>): GatewayResult<String> {
        val appResult = getApplication()
        if (appResult !is GatewayResult.Success) {
            return when (appResult) {
                is GatewayResult.Failure -> appResult
                GatewayResult.Canceled -> GatewayResult.Canceled
                GatewayResult.InteractionRequired -> GatewayResult.InteractionRequired
                is GatewayResult.Success -> error("unreachable")
            }
        }

        val account = activeAccount ?: when (val current = loadCurrentAccount(appResult.value)) {
            is GatewayResult.Success -> activeAccount ?: return GatewayResult.InteractionRequired
            is GatewayResult.Failure -> return current
            GatewayResult.Canceled -> return GatewayResult.Canceled
            GatewayResult.InteractionRequired -> return GatewayResult.InteractionRequired
        }

        return suspendCancellableCoroutine { continuation ->
            val parameters = AcquireTokenSilentParameters.Builder()
                .forAccount(account)
                .fromAuthority(account.authority)
                .withScopes(scopes)
                .withCallback(object : SilentAuthenticationCallback {
                    override fun onSuccess(authenticationResult: IAuthenticationResult) {
                        activeAccount = authenticationResult.account
                        continuation.resume(GatewayResult.Success(authenticationResult.accessToken))
                    }

                    override fun onError(exception: MsalException) {
                        if (exception is MsalUiRequiredException) {
                            continuation.resume(GatewayResult.InteractionRequired)
                        } else {
                            continuation.resume(GatewayResult.Failure(exception.toAuthErrorCategory()))
                        }
                    }

                })
                .build()

            appResult.value.acquireTokenSilent(parameters)
        }
    }

    private suspend fun getApplication(): GatewayResult<ISingleAccountPublicClientApplication> =
        initMutex.withLock {
            application?.let { return@withLock GatewayResult.Success(it) }

            suspendCancellableCoroutine { continuation ->
                PublicClientApplication.createSingleAccountPublicClientApplication(
                    applicationContext,
                    configResourceId,
                    object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                        override fun onCreated(application: ISingleAccountPublicClientApplication) {
                            this@MsalSingleAccountGateway.application = application
                            continuation.resume(GatewayResult.Success(application))
                        }

                        override fun onError(exception: MsalException) {
                            continuation.resume(GatewayResult.Failure(exception.toAuthErrorCategory()))
                        }
                    }
                )
            }
        }

    private suspend fun loadCurrentAccount(
        app: ISingleAccountPublicClientApplication
    ): GatewayResult<AuthAccount?> = suspendCancellableCoroutine { continuation ->
        val resumed = AtomicBoolean(false)

        fun complete(account: IAccount?) {
            activeAccount = account
            if (resumed.compareAndSet(false, true)) {
                continuation.resume(GatewayResult.Success(account?.toAuthAccount()))
            }
        }

        app.getCurrentAccountAsync(object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
            override fun onAccountLoaded(activeAccount: IAccount?) {
                complete(activeAccount)
            }

            override fun onAccountChanged(priorAccount: IAccount?, currentAccount: IAccount?) {
                complete(currentAccount)
            }

            override fun onError(exception: MsalException) {
                if (resumed.compareAndSet(false, true)) {
                    continuation.resume(GatewayResult.Failure(exception.toAuthErrorCategory()))
                }
            }
        })
    }

    private fun IAccount.toAuthAccount(): AuthAccount =
        AuthAccount(
            authority = authority,
            accountId = id
        )

    private fun MsalException.toAuthErrorCategory(): AuthErrorCategory = when (this) {
        is MsalUiRequiredException -> AuthErrorCategory.InteractionRequired
        is MsalServiceException -> AuthErrorCategory.Service
        is MsalClientException -> AuthErrorCategory.Client
        else -> AuthErrorCategory.Unknown
    }
}
