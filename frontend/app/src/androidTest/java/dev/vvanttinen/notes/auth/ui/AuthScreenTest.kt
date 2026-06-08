package dev.vvanttinen.notes.auth.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import dev.vvanttinen.notes.auth.AuthState
import dev.vvanttinen.notes.ui.theme.NotesTheme
import org.junit.Rule
import org.junit.Test

class AuthScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun signedInScreenHidesSilentSmokeActionWhenDisabled() {
        composeRule.setContent {
            NotesTheme {
                AuthScreen(
                    authState = AuthState.SignedIn(accountKey = "redacted-test-key"),
                    silentTokenMessage = null,
                    showSilentTokenSmokeAction = false,
                    onSignIn = {},
                    onSignOut = {},
                    onRetry = {},
                    onTestSilentToken = {}
                )
            }
        }

        composeRule.onAllNodesWithText("Test silent token acquisition").assertCountEquals(0)
    }

    @Test
    fun signedInScreenShowsSilentSmokeActionWhenEnabled() {
        composeRule.setContent {
            NotesTheme {
                AuthScreen(
                    authState = AuthState.SignedIn(accountKey = "redacted-test-key"),
                    silentTokenMessage = null,
                    showSilentTokenSmokeAction = true,
                    onSignIn = {},
                    onSignOut = {},
                    onRetry = {},
                    onTestSilentToken = {}
                )
            }
        }

        composeRule.onNodeWithText("Test silent token acquisition").assertIsDisplayed()
    }
}
