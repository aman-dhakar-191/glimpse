package com.glimpse.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.glimpse.app.ui.auth.LoginScreen
import com.glimpse.app.ui.auth.LoginViewModel
import com.glimpse.app.ui.countdown.CountdownBanner
import com.glimpse.app.ui.countdown.CountdownViewModel
import com.glimpse.app.ui.dailyprompt.DailyPromptViewModel
import com.glimpse.app.ui.guide.WidgetGuideScreen
import com.glimpse.app.ui.history.MessageHistoryScreen
import com.glimpse.app.ui.history.MessageHistoryViewModel
import com.glimpse.app.ui.message.ComposeMessageScreen
import com.glimpse.app.ui.message.ComposeMessageViewModel
import com.glimpse.app.ui.mood.MoodViewModel
import com.glimpse.app.ui.nickname.NicknameViewModel
import com.glimpse.app.ui.onthisday.OnThisDayBanner
import com.glimpse.app.ui.onthisday.OnThisDayViewModel
import com.glimpse.app.ui.pairing.PairingViewModel
import com.glimpse.app.ui.quiethours.QuietHoursViewModel
import com.glimpse.app.ui.reaction.ReactionPickerScreen
import com.glimpse.app.ui.reaction.ReactionPickerViewModel
import com.glimpse.app.ui.stats.StatsScreen
import com.glimpse.app.ui.stats.StatsViewModel
import com.glimpse.app.ui.theme.GlimpseTheme
import com.glimpse.app.ui.update.UpdateBanner
import com.glimpse.app.ui.update.UpdateUiState
import com.glimpse.app.ui.update.UpdateViewModel
import com.glimpse.app.data.update.UpdateChecker
import com.glimpse.app.work.StreakCheckWorker
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

private enum class AppScreen { Login, Compose, Guide, React, History, Stats }

class MainActivity : ComponentActivity() {
    private val loginViewModel: LoginViewModel by viewModels()
    private val composeMessageViewModel: ComposeMessageViewModel by viewModels()
    private val reactionPickerViewModel: ReactionPickerViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()
    private val historyViewModel: MessageHistoryViewModel by viewModels()
    private val statsViewModel: StatsViewModel by viewModels()
    private val pairingViewModel: PairingViewModel by viewModels()
    private val nicknameViewModel: NicknameViewModel by viewModels()
    private val onThisDayViewModel: OnThisDayViewModel by viewModels()
    private val moodViewModel: MoodViewModel by viewModels()
    private val countdownViewModel: CountdownViewModel by viewModels()
    private val dailyPromptViewModel: DailyPromptViewModel by viewModels()
    private val quietHoursViewModel: QuietHoursViewModel by viewModels()

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

    // POST_NOTIFICATIONS is a runtime (dangerous) permission on API 33+ —
    // declaring it in the manifest alone isn't enough, and without this both
    // the widget-sync foreground service notification and push notifications
    // silently fail to show.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op either way — nothing to react to */ }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private var screen by mutableStateOf(AppScreen.Login)

    // checkPairingStatus's Firebase callback is always asynchronous (posted
    // via a Handler even when "cached"), so it reliably fires *after*
    // handleOpenComposeIntent/handleOpenReactIntent already ran synchronously
    // in onCreate below — except when it doesn't beat them to the punch, in
    // which case unconditionally jumping to Compose here would clobber a
    // React deep link that already landed. Only defaulting to Compose when
    // we're not already sitting on the screen a widget tap explicitly
    // requested keeps a cold-started React-button tap from bouncing back to
    // the compose screen a moment after it opens.
    private fun onSignedIn() {
        if (screen != AppScreen.React) {
            screen = AppScreen.Compose
        }
        requestNotificationPermissionIfNeeded()
        loginViewModel.ensureFcmTokenRegistered()
        updateViewModel.checkForUpdate()
        StreakCheckWorker.schedule(this)
    }

    private fun onUpdateClick() {
        if (UpdateChecker.canRequestInstalls(this)) {
            updateViewModel.downloadAndInstall()
        } else {
            UpdateChecker.openUnknownSourcesSettings(this)
        }
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

    // Same reasoning as handleOpenComposeIntent, but for the React button —
    // RemoteViews can't host an emoji picker, so tapping it opens the app
    // straight to one instead.
    private fun handleOpenReactIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_REACT, false) == true && loginViewModel.isSignedIn) {
            reactionPickerViewModel.setTarget(intent.getStringExtra(EXTRA_REACT_MESSAGE_ID).orEmpty())
            screen = AppScreen.React
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenComposeIntent(intent)
        handleOpenReactIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, signInOptions)

        // Being signed in to Firebase Auth no longer implies being allowed
        // into the app — a paired-but-not-yet-redeemed account stays on
        // AppScreen.Login rendering LoginUiState.NeedsPairing instead, so
        // this always starts there and lets checkPairingStatus decide.
        screen = AppScreen.Login
        if (loginViewModel.isSignedIn) {
            loginViewModel.checkPairingStatus(onPaired = { onSignedIn() })
        }
        handleOpenComposeIntent(intent)
        handleOpenReactIntent(intent)

        setContent {
            GlimpseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val loginUiState by loginViewModel.uiState.collectAsState()
                    val composeUiState by composeMessageViewModel.uiState.collectAsState()
                    val reactUiState by reactionPickerViewModel.uiState.collectAsState()
                    val updateUiState by updateViewModel.uiState.collectAsState()
                    val historyUiState by historyViewModel.uiState.collectAsState()
                    val statsUiState by statsViewModel.uiState.collectAsState()
                    val pairingUiState by pairingViewModel.uiState.collectAsState()
                    val nicknameUiState by nicknameViewModel.uiState.collectAsState()
                    val onThisDayUiState by onThisDayViewModel.uiState.collectAsState()
                    val moodUiState by moodViewModel.uiState.collectAsState()
                    val countdownUiState by countdownViewModel.uiState.collectAsState()
                    val dailyPromptUiState by dailyPromptViewModel.uiState.collectAsState()
                    val quietHoursUiState by quietHoursViewModel.uiState.collectAsState()

                    when (screen) {
                        AppScreen.Login -> LoginScreen(
                            uiState = loginUiState,
                            onSignInClick = { signInLauncher.launch(googleSignInClient.signInIntent) },
                            onRedeemCode = { code ->
                                loginViewModel.redeemPairingCode(code, onPaired = { onSignedIn() })
                            },
                            onLogout = { onLogout() }
                        )

                        AppScreen.Compose -> Column(modifier = Modifier.fillMaxSize()) {
                            LaunchedEffect(Unit) {
                                onThisDayViewModel.check()
                                countdownViewModel.load()
                            }
                            if (updateUiState !is UpdateUiState.Idle && updateUiState !is UpdateUiState.ReadyToInstall) {
                                UpdateBanner(
                                    uiState = updateUiState,
                                    onUpdateClick = { onUpdateClick() },
                                    onDismiss = { updateViewModel.dismiss() },
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                            OnThisDayBanner(
                                uiState = onThisDayUiState,
                                onDismiss = { onThisDayViewModel.dismiss() },
                                modifier = Modifier.padding(16.dp)
                            )
                            CountdownBanner(
                                uiState = countdownUiState,
                                modifier = Modifier.padding(16.dp)
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                ComposeMessageScreen(
                                    uiState = composeUiState,
                                    onSend = { text, unlockAt -> composeMessageViewModel.sendMessage(text, unlockAt) },
                                    onSendPhoto = { uri, caption, unlockAt ->
                                        composeMessageViewModel.sendPhotoMessage(uri, caption, unlockAt)
                                    },
                                    onSentHandled = { composeMessageViewModel.consumeSentState() },
                                    onOpenGuide = { screen = AppScreen.Guide },
                                    onOpenHistory = { screen = AppScreen.History },
                                    onLogout = { onLogout() },
                                    onSendNudge = { composeMessageViewModel.sendNudge() },
                                    dailyPromptUiState = dailyPromptUiState,
                                    onLoadDailyPrompt = { dailyPromptViewModel.check() },
                                    onDismissDailyPrompt = { dailyPromptViewModel.dismiss() }
                                )
                            }
                        }

                        AppScreen.Guide -> WidgetGuideScreen(
                            pairingUiState = pairingUiState,
                            onGenerateCode = { pairingViewModel.generateCode() },
                            nicknameUiState = nicknameUiState,
                            onLoadNickname = { nicknameViewModel.load() },
                            onSaveNickname = { name -> nicknameViewModel.save(name) },
                            onDismiss = { screen = AppScreen.Compose },
                            onLogout = { onLogout() },
                            moodUiState = moodUiState,
                            onLoadMood = { moodViewModel.load() },
                            onSetMood = { emoji -> moodViewModel.setMood(emoji) },
                            countdownUiState = countdownUiState,
                            onLoadCountdown = { countdownViewModel.load() },
                            onSetCountdown = { label, month, day -> countdownViewModel.setDate(label, month, day) },
                            onClearCountdown = { countdownViewModel.clearDate() },
                            quietHoursUiState = quietHoursUiState,
                            onLoadQuietHours = { quietHoursViewModel.load() },
                            onSetQuietHoursEnabled = { enabled -> quietHoursViewModel.setEnabled(enabled) },
                            onSetQuietHoursStart = { minutes -> quietHoursViewModel.setStartMinutes(minutes) },
                            onSetQuietHoursEnd = { minutes -> quietHoursViewModel.setEndMinutes(minutes) }
                        )

                        AppScreen.React -> ReactionPickerScreen(
                            uiState = reactUiState,
                            onReact = { emoji -> reactionPickerViewModel.sendReaction(emoji) },
                            onSentHandled = { reactionPickerViewModel.consumeSentState() },
                            onDismiss = { screen = AppScreen.Compose }
                        )

                        AppScreen.History -> {
                            LaunchedEffect(Unit) { historyViewModel.start() }
                            MessageHistoryScreen(
                                uiState = historyUiState,
                                onBack = { screen = AppScreen.Compose },
                                onDownloadImage = { url -> historyViewModel.downloadImage(this@MainActivity, url) },
                                onDownloadResultHandled = { historyViewModel.consumeDownloadResult() },
                                onOpenStats = { screen = AppScreen.Stats },
                                onSearch = { query -> historyViewModel.search(query) }
                            )
                        }

                        AppScreen.Stats -> StatsScreen(
                            uiState = statsUiState,
                            onLoad = { statsViewModel.load() },
                            onBack = { screen = AppScreen.History }
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_COMPOSE = "com.glimpse.app.EXTRA_OPEN_COMPOSE"
        const val EXTRA_OPEN_REACT = "com.glimpse.app.EXTRA_OPEN_REACT"
        const val EXTRA_REACT_MESSAGE_ID = "com.glimpse.app.EXTRA_REACT_MESSAGE_ID"
    }
}
