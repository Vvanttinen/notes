package dev.vvanttinen.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vvanttinen.notes.auth.AuthErrorCategory
import dev.vvanttinen.notes.auth.AuthState
import dev.vvanttinen.notes.auth.NotesAuthController
import dev.vvanttinen.notes.auth.SilentTokenResult
import dev.vvanttinen.notes.ui.theme.NotesTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val authController = (application as NotesApplication).authController

        enableEdgeToEdge()
        setContent {
            NotesTheme {
                val coroutineScope = rememberCoroutineScope()
                val lifecycleOwner = LocalLifecycleOwner.current
                val authState by authController.authState.collectAsStateWithLifecycle()
                var silentTokenMessage by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(authController) {
                    authController.initialize()
                }

                DisposableEffect(lifecycleOwner, authController) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_START) {
                            coroutineScope.launch {
                                authController.refreshCurrentAccount()
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                AuthHarnessScreen(
                    authState = authState,
                    silentTokenMessage = silentTokenMessage,
                    onSignIn = {
                        silentTokenMessage = null
                        coroutineScope.launch {
                            authController.signIn(this@MainActivity)
                        }
                    },
                    onSignOut = {
                        silentTokenMessage = null
                        coroutineScope.launch {
                            authController.signOut()
                        }
                    },
                    onRetry = {
                        silentTokenMessage = null
                        coroutineScope.launch {
                            authController.initialize()
                        }
                    },
                    onTestSilentToken = {
                        coroutineScope.launch {
                            silentTokenMessage = authController
                                .acquireNotesApiAccessTokenSilently()
                                .toSafeStatusMessage()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AuthHarnessScreen(
    authState: AuthState,
    silentTokenMessage: String?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRetry: () -> Unit,
    onTestSilentToken: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(24.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Notes authentication",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    AuthStateContent(
                        authState = authState,
                        silentTokenMessage = silentTokenMessage,
                        onSignIn = onSignIn,
                        onSignOut = onSignOut,
                        onRetry = onRetry,
                        onTestSilentToken = onTestSilentToken
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthStateContent(
    authState: AuthState,
    silentTokenMessage: String?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRetry: () -> Unit,
    onTestSilentToken: () -> Unit
) {
    when (authState) {
        AuthState.Initializing -> {
            CircularProgressIndicator()
            Text("Checking cached Microsoft sign-in state.")
        }

        AuthState.Unconfigured -> {
            Text("Microsoft sign-in is not configured for this local build.")
            Text("Set the documented NOTES_ENTRA_* environment variables or matching untracked Gradle properties, then rebuild.")
            OutlinedButton(onClick = onRetry) {
                Text("Retry configuration check")
            }
        }

        AuthState.SignedOut -> {
            Text("No Microsoft account is signed in.")
            Button(onClick = onSignIn) {
                Text("Sign in with Microsoft")
            }
        }

        is AuthState.SignedIn -> {
            Text("A Microsoft account is signed in.")
            Button(onClick = onTestSilentToken) {
                Text("Test silent token acquisition")
            }
            OutlinedButton(onClick = onSignOut) {
                Text("Sign out")
            }
        }

        is AuthState.Error -> {
            Text("Authentication needs attention: ${authState.category.safeLabel()}.")
            OutlinedButton(onClick = onRetry) {
                Text("Retry")
            }
            OutlinedButton(onClick = onSignOut) {
                Text("Sign out")
            }
        }
    }

    if (silentTokenMessage != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(silentTokenMessage)
    }
}

private fun SilentTokenResult.toSafeStatusMessage(): String = when (this) {
    is SilentTokenResult.Success -> "Silent token acquisition succeeded."
    SilentTokenResult.InteractionRequired -> "Silent token acquisition requires user interaction."
    SilentTokenResult.SignedOut -> "Silent token acquisition skipped because no account is signed in."
    is SilentTokenResult.Failure -> "Silent token acquisition failed: ${category.safeLabel()}."
}

private fun AuthErrorCategory.safeLabel(): String = when (this) {
    AuthErrorCategory.Canceled -> "canceled"
    AuthErrorCategory.Client -> "client error"
    AuthErrorCategory.Configuration -> "configuration error"
    AuthErrorCategory.InteractionRequired -> "interaction required"
    AuthErrorCategory.Network -> "network error"
    AuthErrorCategory.Service -> "service error"
    AuthErrorCategory.Unknown -> "unknown error"
}

@Preview(showBackground = true)
@Composable
fun AuthHarnessPreview() {
    NotesTheme {
        AuthHarnessScreen(
            authState = AuthState.SignedOut,
            silentTokenMessage = null,
            onSignIn = {},
            onSignOut = {},
            onRetry = {},
            onTestSilentToken = {}
        )
    }
}
