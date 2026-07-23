package com.glimpse.app.ui.message

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.glimpse.app.R
import com.glimpse.app.data.VideoLimitStore
import com.glimpse.app.ui.countdown.CountdownUiState
import com.glimpse.app.util.CrashLogger
import com.glimpse.app.ui.dailyprompt.DailyPromptUiState
import com.glimpse.app.ui.theme.BlobButtonShape
import com.glimpse.app.ui.theme.BlobChipShapeA
import com.glimpse.app.ui.theme.BlobChipShapeB
import com.glimpse.app.ui.theme.BlobShapeSoftB
import com.glimpse.app.ui.theme.BlobShapeSoftC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun createCameraImageUri(context: Context): Uri {
    val imagesDir = File(context.cacheDir, "camera").apply { mkdirs() }
    val imageFile = File(imagesDir, "IMG_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
}

private fun createCameraVideoUri(context: Context): Uri {
    val videosDir = File(context.cacheDir, "camera_video").apply { mkdirs() }
    val videoFile = File(videosDir, "VID_${System.currentTimeMillis()}.mp4")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", videoFile)
}

// The stock CaptureVideo contract has no way to add extra Intent extras —
// this just adds the one duration hint on top of what it already builds.
// A hint, not a guarantee (some camera apps ignore it entirely) — the
// actual enforcement is ComposeMessageViewModel.sendVideoMessage's
// post-recording duration check, which reads this same VideoLimitStore
// setting so the two never disagree.
private class CaptureVideoWithDurationLimit : ActivityResultContracts.CaptureVideo() {
    override fun createIntent(context: Context, input: Uri): Intent =
        super.createIntent(context, input).putExtra(MediaStore.EXTRA_DURATION_LIMIT, VideoLimitStore.load(context))
}

private val QUICK_EMOJIS = listOf("❤️", "😊", "👍", "😂", "🎉")
private val MOOD_EMOJIS = listOf("😊", "🥰", "😴", "😢", "😡", "😐", "🤒", "🎉")

// Slightly more generous and symmetric than OutlinedButton's default —
// keeps single-emoji content clear of the blob chip shapes' edge pinches.
private val BlobChipPadding = PaddingValues(14.dp)

// A dedicated animation for photo sends (not text — those are near-instant,
// so a plain spinner is enough) since a photo upload can take a few seconds
// and a pulsing heart over the preview reads as "your glimpse is on its way"
// instead of a generic loading state.
@Composable
private fun PhotoUploadingOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "photo-upload-pulse")
    val scale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "💌",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.scale(scale)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.compose_uploading_photo),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// A single decoded frame (via MediaMetadataRetriever, off the main thread)
// with a play glyph on top — a real live-playing preview here would need a
// whole video player just for a few seconds of compose-screen preview,
// which is a lot of machinery for something the History screen's own
// full player (see MessageHistoryScreen) already does properly once sent.
@Composable
private fun VideoThumbnail(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val frame by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            // release() (not the AutoCloseable close()/use{}, only added in
            // API 29) — minSdk here is 26, and release() has always existed.
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.frameAtTime
            } catch (e: Exception) {
                // Only a preview thumbnail failing, not the send itself —
                // but still worth a record, since a corrupt/inaccessible
                // video file here would also fail the actual upload right
                // after, and this fires first.
                CrashLogger.recordException("VideoThumbnail: frame decode failed (uri=$uri)", e)
                null
            } finally {
                retriever.release()
            }
        }
    }

    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        frame?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text("▶", color = Color.White, style = MaterialTheme.typography.displaySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeMessageScreen(
    uiState: ComposeUiState,
    onSend: (String, Long) -> Unit,
    onSendPhoto: (Uri, String, Long) -> Unit,
    onSendVideo: (Uri, String, Long) -> Unit,
    onSentHandled: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDrawing: () -> Unit,
    onSendNudge: () -> Unit,
    thinkingOfYouBurst: Boolean,
    onThinkingOfYouBurstHandled: () -> Unit,
    reactionBurstEmoji: String?,
    onReactionBurstHandled: () -> Unit,
    dailyPromptUiState: DailyPromptUiState,
    onLoadDailyPrompt: () -> Unit,
    onDismissDailyPrompt: () -> Unit,
    partnerMoodEmoji: String,
    onLoadPartnerMood: () -> Unit,
    myMoodEmoji: String,
    onLoadMyMood: () -> Unit,
    onSetMood: (String) -> Unit,
    countdownUiState: CountdownUiState,
    onSetCountdown: (label: String, month: Int, day: Int) -> Unit,
    onClearCountdown: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    var selectedImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    // Kept as its own separate pair of vars (not folded into
    // selectedImageUri) rather than one "selected media Uri + is-it-a-video
    // flag" — keeps every existing photo-only reference below unchanged and
    // makes the two mutually-exclusive-by-construction (see the launcher
    // callbacks below, which always clear the other one).
    var selectedVideoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingCameraVideoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var showSentBurst by remember { mutableStateOf(false) }
    var showThinkingOfYouBurst by remember { mutableStateOf(false) }
    var activeReactionEmoji by remember { mutableStateOf<String?>(null) }
    // 0L = not a time capsule.
    var lockUntilMillis by rememberSaveable { mutableStateOf(0L) }
    var showLockDatePicker by remember { mutableStateOf(false) }
    val lockDatePickerState = rememberDatePickerState()
    val snackbarHostState = remember { SnackbarHostState() }
    val sentMessage = stringResource(R.string.compose_sent)
    val context = LocalContext.current

    // ImageAndVideo (not ImageOnly) so this one "pick from gallery" button
    // covers both — the callback below tells them apart by MIME type
    // rather than needing a second, video-only picker button.
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true
            if (isVideo) {
                selectedVideoUri = uri
                selectedImageUri = null
            } else {
                selectedImageUri = uri
                selectedVideoUri = null
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> if (success) { selectedImageUri = pendingCameraUri; selectedVideoUri = null } }

    val captureVideoLauncher = rememberLauncherForActivityResult(
        contract = CaptureVideoWithDurationLimit()
    ) { success -> if (success) { selectedVideoUri = pendingCameraVideoUri; selectedImageUri = null } }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createCameraImageUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    // Both CAMERA and RECORD_AUDIO — some camera apps' ACTION_VIDEO_CAPTURE
    // handling checks the CALLING app's own permissions (not just its own),
    // even though it's the camera app's process actually doing the
    // recording, so requesting only CAMERA (as launchCamera does for
    // photos) isn't reliably enough for video across every OEM camera app.
    val videoCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            val uri = createCameraVideoUri(context)
            pendingCameraVideoUri = uri
            captureVideoLauncher.launch(uri)
        }
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            val uri = createCameraImageUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchVideoCamera() {
        val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (hasCamera && hasMic) {
            val uri = createCameraVideoUri(context)
            pendingCameraVideoUri = uri
            captureVideoLauncher.launch(uri)
        } else {
            videoCameraPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    LaunchedEffect(Unit) {
        onLoadDailyPrompt()
        onLoadPartnerMood()
        onLoadMyMood()
    }

    LaunchedEffect(uiState) {
        if (uiState is ComposeUiState.Sent) {
            text = ""
            selectedImageUri = null
            pendingCameraUri = null
            selectedVideoUri = null
            pendingCameraVideoUri = null
            lockUntilMillis = 0L
            showSentBurst = true
            snackbarHostState.showSnackbar(sentMessage)
            onSentHandled()
        }
    }

    // Auto-dismiss the heart burst on its own timer, independent of
    // uiState — by the time Sent fires onSentHandled() above, uiState has
    // already moved on to Idle, so this can't key off uiState the way the
    // effect above does.
    LaunchedEffect(showSentBurst) {
        if (showSentBurst) {
            delay(1100)
            showSentBurst = false
        }
    }

    // Distinct from the heart burst above — that one is about YOUR OWN
    // sends (nudge included); this is your PARTNER sending YOU a "thinking
    // of you", so it needs its own visibly different animation rather than
    // looking like an echo of your own action. Mirrors showSentBurst's own
    // local-state-plus-timer shape: the ViewModel's flag is consumed (reset)
    // immediately so a second one later can retrigger it, while the local
    // var here keeps the animation playing on its own timer regardless.
    LaunchedEffect(thinkingOfYouBurst) {
        if (thinkingOfYouBurst) {
            showThinkingOfYouBurst = true
            onThinkingOfYouBurstHandled()
        }
    }
    LaunchedEffect(showThinkingOfYouBurst) {
        if (showThinkingOfYouBurst) {
            delay(1400)
            showThinkingOfYouBurst = false
        }
    }

    // Same shape again: the ViewModel's flag is consumed immediately (so a
    // second reaction later can retrigger it) while the local var here
    // keeps the specific emoji around for its own display timer.
    LaunchedEffect(reactionBurstEmoji) {
        val emoji = reactionBurstEmoji
        if (emoji != null) {
            activeReactionEmoji = emoji
            onReactionBurstHandled()
        }
    }
    LaunchedEffect(activeReactionEmoji) {
        if (activeReactionEmoji != null) {
            delay(1400)
            activeReactionEmoji = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Was four TextButtons crammed into the header row, which wrapped
        // ("Settings" broke onto its own line) on narrower screens — a
        // bottom nav has fixed-width slots for however many items there
        // are, so it can't overflow the same way.
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenDrawing,
                    icon = { Text("🎨", style = MaterialTheme.typography.titleMedium) },
                    label = { Text(stringResource(R.string.nav_draw)) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenHistory,
                    icon = { Text("📜", style = MaterialTheme.typography.titleMedium) },
                    label = { Text(stringResource(R.string.compose_history_link)) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenGuide,
                    icon = { Text("🧩", style = MaterialTheme.typography.titleMedium) },
                    label = { Text(stringResource(R.string.compose_widget_guide_link)) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenSettings,
                    icon = { Text("⚙️", style = MaterialTheme.typography.titleMedium) },
                    label = { Text(stringResource(R.string.compose_settings_link)) }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                if (partnerMoodEmoji.isNotBlank()) {
                    Text(
                        partnerMoodEmoji,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            // The emoji up in the title row is always your PARTNER's mood,
            // never your own — this is where you set and confirm yours,
            // right on the main screen instead of buried in Settings.
            MyMoodPicker(myMoodEmoji = myMoodEmoji, onSetMood = onSetMood)

            Spacer(Modifier.height(24.dp))

            // A prompt to answer together on quiet days — same deterministic
            // question on both devices (see DailyPromptViewModel). Lives
            // here (not as a MainActivity-level sibling banner like
            // OnThisDay/Countdown) because "Use this" needs direct access
            // to this screen's own `text` field state.
            if (dailyPromptUiState is DailyPromptUiState.Visible) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.daily_prompt_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            dailyPromptUiState.prompt,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = onDismissDailyPrompt) {
                                Text(stringResource(R.string.update_dismiss))
                            }
                            TextButton(onClick = {
                                text = dailyPromptUiState.prompt
                                onDismissDailyPrompt()
                            }) {
                                Text(stringResource(R.string.daily_prompt_use))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)   // slack for the rotation excursion
                    .graphicsLayer {
                        rotationZ = 0.8f
                        clip = false
                    },
                shape = BlobShapeSoftB,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                // The content-safe soft variant — this card carries an
                // OutlinedTextField plus a row of action buttons, so it
                // needs a shape whose deepest pinch a normal padding value
                // can actually clear (see BlobShapes.kt).
                Column(modifier = Modifier.padding(28.dp)) {
                    Text(stringResource(R.string.compose_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))

                    val uploadingPhoto = uiState is ComposeUiState.Sending && selectedImageUri != null
                    val uploadingVideo = uiState is ComposeUiState.Sending && selectedVideoUri != null
                    val hasAttachment = selectedImageUri != null || selectedVideoUri != null

                    selectedImageUri?.let { uri ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            if (uploadingPhoto) {
                                PhotoUploadingOverlay(modifier = Modifier.fillMaxWidth().height(160.dp))
                            } else {
                                IconButton(
                                    onClick = { selectedImageUri = null },
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Text(
                                        "✕",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    selectedVideoUri?.let { uri ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            VideoThumbnail(
                                uri = uri,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            if (uploadingVideo) {
                                PhotoUploadingOverlay(modifier = Modifier.fillMaxWidth().height(160.dp))
                            } else {
                                IconButton(
                                    onClick = { selectedVideoUri = null },
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Text(
                                        "✕",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                stringResource(
                                    if (hasAttachment) {
                                        R.string.compose_caption_placeholder
                                    } else {
                                        R.string.compose_placeholder
                                    }
                                )
                            )
                        },
                        minLines = if (hasAttachment) 1 else 3
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Alternating blob shapes so the row of icon
                        // buttons isn't just the same chip repeated.
                        OutlinedButton(
                            onClick = { launchCamera() },
                            shape = BlobChipShapeA,
                            contentPadding = BlobChipPadding
                        ) {
                            Text("📸")
                        }
                        OutlinedButton(
                            onClick = { launchVideoCamera() },
                            shape = BlobChipShapeB,
                            contentPadding = BlobChipPadding
                        ) {
                            Text("🎥")
                        }
                        OutlinedButton(
                            onClick = {
                                // ImageAndVideo (not ImageOnly) — the launcher
                                // callback above tells the two apart by MIME
                                // type, so one button covers picking either.
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            shape = BlobChipShapeA,
                            contentPadding = BlobChipPadding
                        ) {
                            Text("🖼️")
                        }
                        QUICK_EMOJIS.forEachIndexed { index, emoji ->
                            OutlinedButton(
                                onClick = { text += emoji },
                                shape = if (index % 2 == 0) BlobChipShapeA else BlobChipShapeB,
                                contentPadding = BlobChipPadding
                            ) {
                                Text(emoji)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Time capsule: a message that stays locked/hidden
                    // (both in-app and on the widget — see Message.isLocked)
                    // until a future date, unlike a scheduled send which is
                    // just delayed. Separate row from the quick actions above
                    // so it doesn't crowd an already-full row on narrow screens.
                    if (lockUntilMillis > 0L) {
                        val formattedDate = remember(lockUntilMillis) {
                            Instant.ofEpochMilli(lockUntilMillis).atZone(ZoneId.systemDefault())
                                .toLocalDate()
                                .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.compose_locked_until, formattedDate),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { lockUntilMillis = 0L }) {
                                Text(stringResource(R.string.compose_locked_remove))
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showLockDatePicker = true },
                            shape = BlobChipShapeA,
                            contentPadding = BlobChipPadding
                        ) {
                            Text(stringResource(R.string.compose_lock_button))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (uiState is ComposeUiState.Error) {
                Text(
                    uiState.message,
                    color = Color.Red,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (uiState is ComposeUiState.Queued) {
                Text(
                    stringResource(R.string.compose_queued),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            val canSend = uiState !is ComposeUiState.Sending && uiState !is ComposeUiState.Queued &&
                (selectedImageUri != null || selectedVideoUri != null || text.isNotBlank())

            Button(
                onClick = {
                    val photoUri = selectedImageUri
                    val videoUri = selectedVideoUri
                    when {
                        photoUri != null -> onSendPhoto(photoUri, text, lockUntilMillis)
                        videoUri != null -> onSendVideo(videoUri, text, lockUntilMillis)
                        else -> onSend(text, lockUntilMillis)
                    }
                },
                enabled = canSend,
                modifier = Modifier.fillMaxWidth(),
                shape = BlobButtonShape,
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
            ) {
                if (uiState is ComposeUiState.Sending || uiState is ComposeUiState.Queued) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.compose_send))
                }
            }

            Spacer(Modifier.height(16.dp))

            CountdownEditorCard(countdownUiState, onSetCountdown, onClearCountdown)

            Spacer(Modifier.height(10.dp))

            // A separate, always-available action rather than folded into
            // the text/photo Send button above — a nudge doesn't need
            // anything composed first, it's meant to be the "no words
            // needed" option.
            OutlinedButton(
                onClick = onSendNudge,
                enabled = uiState !is ComposeUiState.Sending && uiState !is ComposeUiState.Queued,
                modifier = Modifier.fillMaxWidth(),
                shape = BlobButtonShape,
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)
            ) {
                Text(stringResource(R.string.compose_nudge_button))
            }
        }

        AnimatedVisibility(
            visible = showSentBurst,
            modifier = Modifier.align(Alignment.Center),
            enter = scaleIn(
                initialScale = 0.4f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            ) + fadeIn(),
            exit = fadeOut()
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_heart),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp)
            )
        }

        // Deliberately different from the heart-burst above (repeating
        // pulse instead of a one-shot bounce-in, a different emoji/color)
        // so it doesn't read as "you just sent something" — this is your
        // PARTNER thinking of you, not your own action echoing back.
        AnimatedVisibility(
            visible = showThinkingOfYouBurst,
            modifier = Modifier.align(Alignment.Center),
            enter = scaleIn(
                initialScale = 0.4f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            ) + fadeIn(),
            exit = fadeOut()
        ) {
            val transition = rememberInfiniteTransition(label = "thinking-of-you-pulse")
            val scale by transition.animateFloat(
                initialValue = 0.9f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 350, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )
            Text(
                "💓",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.scale(scale)
            )
        }

        // The actual emoji your partner reacted with, not a fixed icon —
        // a one-shot bounce like the sent-heart burst above (this one's
        // already a specific, varied emoji, so it doesn't need the repeating
        // pulse the other two use to read as "distinct").
        AnimatedVisibility(
            visible = activeReactionEmoji != null,
            modifier = Modifier.align(Alignment.Center),
            enter = scaleIn(
                initialScale = 0.4f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            ) + fadeIn(),
            exit = fadeOut()
        ) {
            Text(activeReactionEmoji ?: "", style = MaterialTheme.typography.displayLarge)
        }
        }
    }

    if (showLockDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showLockDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = lockDatePickerState.selectedDateMillis
                    if (millis != null) {
                        // DatePickerState's selectedDateMillis is UTC midnight
                        // for the picked calendar day — re-anchoring to local
                        // midnight of that same day is what "unlocks at" should
                        // actually mean to the person reading it later.
                        val pickedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        val unlockAt = pickedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        // A date that's already passed (or is today, since
                        // local midnight of today is always in the past by
                        // the time you're picking it) isn't a meaningful lock
                        // — silently treat it as "no lock" instead.
                        if (unlockAt > System.currentTimeMillis()) {
                            lockUntilMillis = unlockAt
                        }
                    }
                    showLockDatePicker = false
                }) {
                    Text(stringResource(R.string.guide_countdown_pick_date))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLockDatePicker = false }) {
                    Text(stringResource(R.string.guide_dismiss))
                }
            }
        ) {
            DatePicker(state = lockDatePickerState)
        }
    }
}

// Right next to the title instead of buried in Settings — the emoji up
// there is always your PARTNER's mood, so this is the only place your own
// mood is actually set and confirmed.
// Quick-pick chips cover the common cases; the free-form field underneath
// (same pattern as ReactionPickerScreen) takes any emoji at all, not just
// this fixed set.
@Composable
private fun MyMoodPicker(myMoodEmoji: String, onSetMood: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }

    TextButton(onClick = { showPicker = true }, contentPadding = PaddingValues(0.dp)) {
        Text(
            if (myMoodEmoji.isNotBlank()) {
                stringResource(R.string.compose_my_mood, myMoodEmoji)
            } else {
                stringResource(R.string.compose_my_mood_unset)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showPicker) {
        var customEmoji by rememberSaveable { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.guide_mood_title)) },
            text = {
                Column {
                    val rows = MOOD_EMOJIS.chunked(4)
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                            row.forEachIndexed { index, emoji ->
                                val isSelected = emoji == myMoodEmoji
                                OutlinedButton(
                                    onClick = {
                                        onSetMood(emoji)
                                        showPicker = false
                                    },
                                    shape = if (index % 2 == 0) BlobChipShapeA else BlobChipShapeB,
                                    contentPadding = BlobChipPadding,
                                    colors = if (isSelected) {
                                        ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    } else {
                                        ButtonDefaults.outlinedButtonColors()
                                    }
                                ) {
                                    Text(emoji)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customEmoji,
                        onValueChange = { customEmoji = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.compose_mood_custom_placeholder)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (customEmoji.isNotBlank()) {
                            onSetMood(customEmoji.trim())
                            customEmoji = ""
                        }
                        showPicker = false
                    },
                    enabled = customEmoji.isNotBlank()
                ) {
                    Text(stringResource(R.string.compose_mood_set))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.guide_dismiss))
                }
            }
        )
    }
}

// Shared, single countdown for the pair — see FirebaseSync.setSpecialDate/
// CountdownViewModel for why either of you setting this changes it for
// both. Lives here (below the Send button) rather than in Settings — same
// reasoning as the mood picker above, this is a thing you actually look at
// and change often enough to want on the main screen, not tucked away.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountdownEditorCard(
    uiState: CountdownUiState,
    onSetDate: (label: String, month: Int, day: Int) -> Unit,
    onClearDate: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var labelInput by rememberSaveable { mutableStateOf("") }
    val datePickerState = rememberDatePickerState()
    val defaultLabel = stringResource(R.string.guide_countdown_title)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .rotate(-0.5f),
        shape = BlobShapeSoftC,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(28.dp)) {
            Text(
                stringResource(R.string.guide_countdown_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.guide_countdown_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            if (uiState is CountdownUiState.Loaded) {
                if (uiState.error != null) {
                    Text(
                        uiState.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                uiState.specialDate?.let { date ->
                    Text(
                        stringResource(R.string.guide_countdown_current, date.label, date.month, date.day),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                OutlinedTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.guide_countdown_label_placeholder)) },
                    singleLine = true
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isSaving,
                        shape = BlobButtonShape,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(stringResource(R.string.guide_countdown_pick_date))
                        }
                    }

                    if (uiState.specialDate != null) {
                        OutlinedButton(
                            onClick = onClearDate,
                            enabled = !uiState.isSaving,
                            shape = BlobButtonShape,
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                        ) {
                            Text(stringResource(R.string.guide_countdown_remove))
                        }
                    }
                }
            } else {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        // Same UTC-midnight reasoning as the time-capsule date
                        // picker above.
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        val label = labelInput.ifBlank { defaultLabel }
                        onSetDate(label, picked.monthValue, picked.dayOfMonth)
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.guide_countdown_pick_date))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.guide_dismiss))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
