package dev.vvanttinen.notes.auth.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vvanttinen.notes.auth.NotesAuthController
import kotlinx.coroutines.launch

@Composable
fun AuthRoute(
    authController: NotesAuthController,
    activity: Activity,
    showSilentTokenSmokeAction: Boolean
) {
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

    AuthScreen(
        authState = authState,
        silentTokenMessage = silentTokenMessage,
        showSilentTokenSmokeAction = showSilentTokenSmokeAction,
        onSignIn = {
            silentTokenMessage = null
            coroutineScope.launch {
                authController.signIn(activity)
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
