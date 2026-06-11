package dev.vvanttinen.notes.auth.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.vvanttinen.notes.auth.AuthErrorCategory
import dev.vvanttinen.notes.auth.AuthState
import dev.vvanttinen.notes.auth.SilentTokenResult
import dev.vvanttinen.notes.ui.theme.NotesTheme

@Composable
fun AuthScreen(
    authState: AuthState,
    silentTokenMessage: String?,
    showSilentTokenSmokeAction: Boolean,
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
                        showSilentTokenSmokeAction = showSilentTokenSmokeAction,
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
    showSilentTokenSmokeAction: Boolean,
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
            if (showSilentTokenSmokeAction) {
                Button(onClick = onTestSilentToken) {
                    Text("Test silent token acquisition")
                }
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

internal fun SilentTokenResult.toSafeStatusMessage(): String = when (this) {
    is SilentTokenResult.Success -> "Silent token acquisition succeeded."
    SilentTokenResult.InteractionRequired -> "Silent token acquisition requires user interaction."
    SilentTokenResult.SignedOut -> "Silent token acquisition skipped because no account is signed in."
    is SilentTokenResult.Failure -> "Silent token acquisition failed: ${category.safeLabel()}."
}

internal fun AuthErrorCategory.safeLabel(): String = when (this) {
    AuthErrorCategory.AccessDenied -> "access denied"
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
private fun AuthScreenPreview() {
    NotesTheme {
        AuthScreen(
            authState = AuthState.SignedOut,
            silentTokenMessage = null,
            showSilentTokenSmokeAction = true,
            onSignIn = {},
            onSignOut = {},
            onRetry = {},
            onTestSilentToken = {}
        )
    }
}
