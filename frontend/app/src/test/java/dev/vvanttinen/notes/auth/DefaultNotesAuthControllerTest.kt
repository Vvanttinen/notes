package dev.vvanttinen.notes.auth

import android.app.Activity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultNotesAuthControllerTest {
    @Test
    fun missingLocalEntraConfigurationReportsUnconfiguredWithoutGatewayInitialization() = runTest {
        val gateway = FakeNotesAuthGateway()
        val controller = controller(configured = false, gateway = gateway)

        controller.initialize()

        assertEquals(AuthState.Unconfigured, controller.authState.value)
        assertEquals(0, gateway.initializeCalls)
    }

    @Test
    fun configuredInitializationWithNoCachedAccountReportsSignedOut() = runTest {
        val gateway = FakeNotesAuthGateway(cachedAccount = null)
        val controller = controller(gateway = gateway)

        controller.initialize()

        assertEquals(AuthState.SignedOut, controller.authState.value)
    }

    @Test
    fun configuredInitializationWithCachedAccountReportsSignedInWithDeterministicAccountKey() = runTest {
        val account = account("https://login.microsoftonline.com/tenant-a", "account-a")
        val gateway = FakeNotesAuthGateway(cachedAccount = account)
        val controller = controller(gateway = gateway)

        controller.initialize()

        assertEquals(
            AuthState.SignedIn(AccountKeyDeriver.derive(account.authority, account.accountId)),
            controller.authState.value
        )
    }

    @Test
    fun interactiveSignInSuccessSelectsCorrectAccountPartition() = runTest {
        val account = account("https://login.microsoftonline.com/tenant-a", "account-a")
        val gateway = FakeNotesAuthGateway(signInResult = GatewayResult.Success(account))
        val controller = controller(gateway = gateway)

        controller.initialize()
        controller.signIn(FakeActivity())

        assertEquals(
            AuthState.SignedIn(AccountKeyDeriver.derive(account.authority, account.accountId)),
            controller.authState.value
        )
        assertEquals(listOf(TEST_SCOPE), gateway.signInScopes)
    }

    @Test
    fun interactiveSignInCancellationMapsToSafeRecoverableState() = runTest {
        val gateway = FakeNotesAuthGateway(signInResult = GatewayResult.Canceled)
        val controller = controller(gateway = gateway)

        controller.initialize()
        controller.signIn(FakeActivity())

        assertEquals(AuthState.Error(AuthErrorCategory.Canceled), controller.authState.value)
    }

    @Test
    fun interactiveSignInErrorMapsToSanitizedErrorState() = runTest {
        val gateway = FakeNotesAuthGateway(
            signInResult = GatewayResult.Failure(AuthErrorCategory.Client)
        )
        val controller = controller(gateway = gateway)

        controller.initialize()
        controller.signIn(FakeActivity())

        assertEquals(AuthState.Error(AuthErrorCategory.Client), controller.authState.value)
    }

    @Test
    fun signOutSuccessReportsSignedOutAndDeselectsPriorPartition() = runTest {
        val account = account("https://login.microsoftonline.com/tenant-a", "account-a")
        val gateway = FakeNotesAuthGateway(
            cachedAccount = account,
            signOutResult = GatewayResult.Success(Unit)
        )
        val controller = controller(gateway = gateway)

        controller.initialize()
        assertTrue(controller.authState.value is AuthState.SignedIn)

        controller.signOut()

        assertEquals(AuthState.SignedOut, controller.authState.value)
    }

    @Test
    fun refreshAfterAccountRemovalReportsSignedOut() = runTest {
        val gateway = FakeNotesAuthGateway(
            cachedAccount = account("https://login.microsoftonline.com/tenant-a", "account-a")
        )
        val controller = controller(gateway = gateway)

        controller.initialize()
        gateway.refreshResult = GatewayResult.Success(null)
        controller.refreshCurrentAccount()

        assertEquals(AuthState.SignedOut, controller.authState.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun staleForegroundRefreshAfterNewerSignInSuccessDoesNotOverwriteSignedIn() = runTest {
        val account = account("https://login.microsoftonline.com/tenant-a", "account-a")
        val signInResult = CompletableDeferred<GatewayResult<AuthAccount>>()
        val gateway = FakeNotesAuthGateway(
            cachedAccount = null,
            signInResultDeferred = signInResult
        )
        val controller = controller(gateway = gateway)

        controller.initialize()
        gateway.refreshResult = GatewayResult.Success(null)

        val signInJob = async { controller.signIn(FakeActivity()) }
        runCurrent()
        val refreshJob = async { controller.refreshCurrentAccount() }
        runCurrent()

        signInResult.complete(GatewayResult.Success(account))
        signInJob.await()
        refreshJob.await()

        assertEquals(
            AuthState.SignedIn(AccountKeyDeriver.derive(account.authority, account.accountId)),
            controller.authState.value
        )
        assertEquals(0, gateway.refreshCalls)
    }

    @Test
    fun refreshAfterAccountReplacementSelectsNewPartition() = runTest {
        val oldAccount = account("https://login.microsoftonline.com/tenant-a", "account-a")
        val newAccount = account("https://login.microsoftonline.com/tenant-a", "account-b")
        val gateway = FakeNotesAuthGateway(cachedAccount = oldAccount)
        val controller = controller(gateway = gateway)

        controller.initialize()
        gateway.refreshResult = GatewayResult.Success(newAccount)
        controller.refreshCurrentAccount()

        assertEquals(
            AuthState.SignedIn(AccountKeyDeriver.derive(newAccount.authority, newAccount.accountId)),
            controller.authState.value
        )
    }

    @Test
    fun silentTokenSuccessReturnsTransientSuccessValue() = runTest {
        val gateway = FakeNotesAuthGateway(
            cachedAccount = account("https://login.microsoftonline.com/tenant-a", "account-a"),
            silentResult = GatewayResult.Success("access-token")
        )
        val controller = controller(gateway = gateway)

        controller.initialize()
        val result = controller.acquireNotesApiAccessTokenSilently()

        assertEquals(SilentTokenResult.Success("access-token"), result)
        assertEquals(listOf(TEST_SCOPE), gateway.silentScopes)
    }

    @Test
    fun manualSilentTokenSmokeAfterInteractiveSignInReturnsSuccess() = runTest {
        val account = account("https://login.microsoftonline.com/tenant-a", "account-a")
        val gateway = FakeNotesAuthGateway(
            cachedAccount = null,
            signInResult = GatewayResult.Success(account),
            silentResult = GatewayResult.Success("access-token")
        )
        val controller = controller(gateway = gateway)

        controller.initialize()
        controller.signIn(FakeActivity())
        val result = controller.acquireNotesApiAccessTokenSilently()

        assertEquals(
            AuthState.SignedIn(AccountKeyDeriver.derive(account.authority, account.accountId)),
            controller.authState.value
        )
        assertEquals(SilentTokenResult.Success("access-token"), result)
        assertEquals(listOf(TEST_SCOPE), gateway.silentScopes)
    }

    @Test
    fun silentTokenInteractionRequiredDoesNotLaunchInteractiveFlow() = runTest {
        val gateway = FakeNotesAuthGateway(
            cachedAccount = account("https://login.microsoftonline.com/tenant-a", "account-a"),
            silentResult = GatewayResult.InteractionRequired
        )
        val controller = controller(gateway = gateway)

        controller.initialize()
        val result = controller.acquireNotesApiAccessTokenSilently()

        assertEquals(SilentTokenResult.InteractionRequired, result)
        assertEquals(0, gateway.signInCalls)
    }

    @Test
    fun silentTokenWhileSignedOutReturnsSignedOut() = runTest {
        val gateway = FakeNotesAuthGateway(cachedAccount = null)
        val controller = controller(gateway = gateway)

        controller.initialize()
        val result = controller.acquireNotesApiAccessTokenSilently()

        assertEquals(SilentTokenResult.SignedOut, result)
        assertEquals(0, gateway.silentCalls)
    }

    @Test
    fun accountKeyDerivationFailureReportsExplicitSafeError() = runTest {
        val gateway = FakeNotesAuthGateway(
            signInResult = GatewayResult.Success(account("https://login.microsoftonline.com/tenant-a", "  "))
        )
        val controller = controller(gateway = gateway)

        controller.initialize()
        controller.signIn(FakeActivity())

        assertEquals(AuthState.Error(AuthErrorCategory.Configuration), controller.authState.value)
    }

    @Test
    fun configurationRequiresScopeWhenMarkedConfigured() {
        assertEquals(
            EntraAuthConfig(isConfigured = false, notesApiScope = "", msalAuthority = TEST_AUTHORITY),
            EntraAuthConfig.fromValues(
                isConfigured = true,
                notesApiScope = "  ",
                msalAuthority = TEST_AUTHORITY
            )
        )
        assertEquals(
            EntraAuthConfig(isConfigured = false, notesApiScope = TEST_SCOPE, msalAuthority = ""),
            EntraAuthConfig.fromValues(
                isConfigured = true,
                notesApiScope = TEST_SCOPE,
                msalAuthority = "  "
            )
        )
        assertEquals(
            EntraAuthConfig(isConfigured = true, notesApiScope = TEST_SCOPE, msalAuthority = TEST_AUTHORITY),
            EntraAuthConfig.fromValues(
                isConfigured = true,
                notesApiScope = " $TEST_SCOPE ",
                msalAuthority = " $TEST_AUTHORITY "
            )
        )
    }

    private fun controller(
        configured: Boolean = true,
        gateway: FakeNotesAuthGateway
    ): DefaultNotesAuthController =
        DefaultNotesAuthController(
            config = EntraAuthConfig.fromValues(
                isConfigured = configured,
                notesApiScope = if (configured) TEST_SCOPE else "",
                msalAuthority = if (configured) TEST_AUTHORITY else ""
            ),
            gateway = gateway
        )

    private fun account(authority: String, id: String): AuthAccount =
        AuthAccount(authority = authority, accountId = id)

    private class FakeNotesAuthGateway(
        private val cachedAccount: AuthAccount? = null,
        private val signInResult: GatewayResult<AuthAccount> = GatewayResult.Failure(AuthErrorCategory.Unknown),
        private val signInResultDeferred: CompletableDeferred<GatewayResult<AuthAccount>>? = null,
        private val signOutResult: GatewayResult<Unit> = GatewayResult.Success(Unit),
        private val silentResult: GatewayResult<String> = GatewayResult.Failure(AuthErrorCategory.Unknown)
    ) : NotesAuthGateway {
        var initializeCalls = 0
        var refreshCalls = 0
        var signInCalls = 0
        var signInScopes: List<String> = emptyList()
        var silentCalls = 0
        var silentScopes: List<String> = emptyList()
        var refreshResult: GatewayResult<AuthAccount?> = GatewayResult.Success(cachedAccount)

        override suspend fun initialize(): GatewayResult<AuthAccount?> {
            initializeCalls += 1
            return GatewayResult.Success(cachedAccount)
        }

        override suspend fun refreshCurrentAccount(): GatewayResult<AuthAccount?> {
            refreshCalls += 1
            return refreshResult
        }

        override suspend fun signIn(activity: Activity, scopes: List<String>): GatewayResult<AuthAccount> {
            signInCalls += 1
            signInScopes = scopes
            return signInResultDeferred?.await() ?: signInResult
        }

        override suspend fun signOut(): GatewayResult<Unit> = signOutResult

        override suspend fun acquireTokenSilently(scopes: List<String>): GatewayResult<String> {
            silentCalls += 1
            silentScopes = scopes
            return silentResult
        }
    }

    private class FakeActivity : Activity()

    companion object {
        private const val TEST_SCOPE = "api://notes-api-client-id/access_as_user"
        private const val TEST_AUTHORITY = "https://login.microsoftonline.com/tenant-a"
    }
}
