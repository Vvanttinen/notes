package dev.vvanttinen.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.vvanttinen.notes.auth.ui.AuthRoute
import dev.vvanttinen.notes.ui.theme.NotesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val authFeature = (application as NotesApplication).authFeature

        enableEdgeToEdge()
        setContent {
            NotesTheme {
                AuthRoute(
                    authController = authFeature.controller,
                    activity = this@MainActivity,
                    showSilentTokenSmokeAction = BuildConfig.DEBUG
                )
            }
        }
    }
}
