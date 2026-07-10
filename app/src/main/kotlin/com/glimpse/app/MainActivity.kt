package com.glimpse.app

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
import com.glimpse.app.ui.theme.GlimpseTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class MainActivity : ComponentActivity() {
    private val loginViewModel: LoginViewModel by viewModels()

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

    private var showGuide by mutableStateOf(false)

    private fun onSignedIn() {
        showGuide = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, signInOptions)

        showGuide = loginViewModel.isSignedIn

        setContent {
            GlimpseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by loginViewModel.uiState.collectAsState()
                    if (showGuide) {
                        WidgetGuideScreen(
                            onDismiss = { finish() },
                            onLogout = {
                                googleSignInClient.signOut()
                                loginViewModel.signOut()
                                showGuide = false
                            }
                        )
                    } else {
                        LoginScreen(
                            uiState = uiState,
                            onSignInClick = { signInLauncher.launch(googleSignInClient.signInIntent) }
                        )
                    }
                }
            }
        }
    }
}
