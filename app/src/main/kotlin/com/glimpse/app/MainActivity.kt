package com.glimpse.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.glimpse.app.ui.auth.LoginScreen
import com.glimpse.app.ui.auth.LoginViewModel
import com.glimpse.app.ui.guide.WidgetGuideScreen
import com.glimpse.app.ui.message.ComposeMessageScreen
import com.glimpse.app.ui.message.ComposeMessageViewModel
import com.glimpse.app.ui.theme.GlimpseTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

private enum class AppScreen { Login, Compose, Guide }

class MainActivity : ComponentActivity() {
    private val loginViewModel: LoginViewModel by viewModels()
    private val composeMessageViewModel: ComposeMessageViewModel by viewModels()

    private lateinit var googleSignInClient: GoogleSignInClient

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        val account = try {
            task.getResult(ApiException::class.java)
        } catch (e: ApiException) {
            null
        }
        loginViewModel.onGoogleSignInResult(account) { onSignedIn() }
    }

    private var screen by mutableStateOf(AppScreen.Login)

    private fun onSignedIn() {
        screen = AppScreen.Compose
    }

    private fun onLogout() {
        googleSignInClient.signOut()
        loginViewModel.signOut()
        screen = AppScreen.Login
    }

    // Widgets can't contain a text input field, so tapping the widget's
    // message area (see ReactionActionBinder.bindOpenComposeAction) opens
    // the app straight to Compose instead of wherever it was left —
    // MainActivity is singleTask, so a tap while it's already running comes
    // through here rather than onCreate.
    private fun handleOpenComposeIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_COMPOSE, false) == true && loginViewModel.isSignedIn) {
            screen = AppScreen.Compose
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenComposeIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, signInOptions)

        screen = if (loginViewModel.isSignedIn) AppScreen.Compose else AppScreen.Login
        handleOpenComposeIntent(intent)

        setContent {
            GlimpseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val loginUiState by loginViewModel.uiState.collectAsState()
                    val composeUiState by composeMessageViewModel.uiState.collectAsState()

                    when (screen) {
                        AppScreen.Login -> LoginScreen(
                            uiState = loginUiState,
                            onSignInClick = { signInLauncher.launch(googleSignInClient.signInIntent) }
                        )

                        AppScreen.Compose -> ComposeMessageScreen(
                            uiState = composeUiState,
                            onSend = { text -> composeMessageViewModel.sendMessage(text) },
                            onSendPhoto = { uri, caption ->
                                composeMessageViewModel.sendPhotoMessage(uri, caption)
                            },
                            onSentHandled = { composeMessageViewModel.consumeSentState() },
                            onOpenGuide = { screen = AppScreen.Guide },
                            onLogout = { onLogout() }
                        )

                        AppScreen.Guide -> WidgetGuideScreen(
                            onDismiss = { screen = AppScreen.Compose },
                            onLogout = { onLogout() }
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_COMPOSE = "com.glimpse.app.EXTRA_OPEN_COMPOSE"
    }
}
