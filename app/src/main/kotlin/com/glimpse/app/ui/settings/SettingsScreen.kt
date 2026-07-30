package com.glimpse.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.glimpse.app.R
import com.glimpse.app.notification.MorseVibration
import com.glimpse.app.data.CarouselSettingsStore
import com.glimpse.app.data.VideoLimitStore
import com.glimpse.app.ui.carousel.CarouselSettingsUiState
import com.glimpse.app.ui.drawing.DrawingColors
import com.glimpse.app.ui.nickname.NicknameUiState
import com.glimpse.app.ui.pairing.PairingUiState
import com.glimpse.app.ui.quiethours.QuietHoursUiState
import com.glimpse.app.ui.theme.BlobButtonShape
import com.glimpse.app.ui.theme.BlobShapeSoftB
import com.glimpse.app.ui.theme.BlobShapeSoftC
import com.glimpse.app.ui.videolimit.VideoLimitUiState
import com.glimpse.app.ui.widgetaccent.WidgetAccentColorUiState
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Generous, matching the same reasoning as the login/compose blob buttons —
// these shapes pinch inward at the edges more than a plain pill would.
private val BlobButtonPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)

// Everything that isn't "how to add the widget" (see WidgetGuideScreen) and
// isn't directly on the compose screen (mood — tap the emoji next to the
// title; the special-date countdown — below the Send button) — pairing,
// nickname, quiet hours, and log out.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    pairingUiState: PairingUiState,
    onGenerateCode: () -> Unit,
    nicknameUiState: NicknameUiState,
    onLoadNickname: () -> Unit,
    onSaveNickname: (String) -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    quietHoursUiState: QuietHoursUiState,
    onLoadQuietHours: () -> Unit,
    onSetQuietHoursEnabled: (Boolean) -> Unit,
    onSetQuietHoursStart: (Int) -> Unit,
    onSetQuietHoursEnd: (Int) -> Unit,
    carouselSettingsUiState: CarouselSettingsUiState,
    onLoadCarouselSettings: () -> Unit,
    onSetCarouselSize: (Int) -> Unit,
    onSetCarouselAutoAdvanceMinutes: (Int) -> Unit,
    widgetAccentColorUiState: WidgetAccentColorUiState,
    onLoadWidgetAccentColor: () -> Unit,
    onSetWidgetAccentColor: (String?) -> Unit,
    videoLimitUiState: VideoLimitUiState,
    onLoadVideoLimit: () -> Unit,
    onSetVideoLimitSeconds: (Int) -> Unit,
    myDisplayName: String,
    onOpenUpdate: () -> Unit
) {
    LaunchedEffect(Unit) {
        onLoadNickname()
        onLoadQuietHours()
        onLoadCarouselSettings()
        onLoadWidgetAccentColor()
        onLoadVideoLimit()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InviteCard(pairingUiState, onGenerateCode)

            NicknameCard(nicknameUiState, onSaveNickname)

            QuietHoursCard(quietHoursUiState, onSetQuietHoursEnabled, onSetQuietHoursStart, onSetQuietHoursEnd)

            CarouselSizeCard(carouselSettingsUiState, onSetCarouselSize, onSetCarouselAutoAdvanceMinutes)

            WidgetAccentColorCard(widgetAccentColorUiState, onSetWidgetAccentColor)

            VideoLimitCard(videoLimitUiState, onSetVideoLimitSeconds)

            MorseCard(myDisplayName)

            // The update-available notification dedupes per version and
            // won't come back once dismissed for a release you haven't
            // installed yet — this is the one always-available way back to
            // the update screen regardless of notification state.
            TextButton(
                onClick = onOpenUpdate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.update_check_button))
            }

            TextButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.guide_logout))
            }
        }
    }
}

@Composable
private fun InviteCard(uiState: PairingUiState, onGenerateCode: () -> Unit) {
    val clipboardManager: ClipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .rotate(-0.5f),
        shape = BlobShapeSoftC,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(28.dp)) {
            Text(
                stringResource(R.string.guide_invite_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                stringResource(R.string.guide_invite_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            when (uiState) {
                is PairingUiState.CodeReady -> {
                    Text(
                        uiState.code,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        stringResource(R.string.guide_invite_expiry),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedButton(
                        onClick = { clipboardManager.setText(AnnotatedString(uiState.code)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = BlobButtonShape,
                        contentPadding = BlobButtonPadding
                    ) {
                        Text(stringResource(R.string.guide_invite_copy))
                    }
                }

                is PairingUiState.Error -> {
                    Text(
                        uiState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    InviteButton(isLoading = false, onClick = onGenerateCode)
                }

                is PairingUiState.Loading -> InviteButton(isLoading = true, onClick = onGenerateCode)
                is PairingUiState.Idle -> InviteButton(isLoading = false, onClick = onGenerateCode)
            }
        }
    }
}

@Composable
private fun InviteButton(isLoading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isLoading,
        shape = BlobButtonShape,
        contentPadding = BlobButtonPadding
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(stringResource(R.string.guide_invite_button))
        }
    }
}

// Purely local to this device/account — see FirebaseSync.fetchPartnerNicknameOnce
// for why this never affects what the partner sees on their own side.
@Composable
private fun NicknameCard(uiState: NicknameUiState, onSaveNickname: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .rotate(0.6f),
        shape = BlobShapeSoftB,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(28.dp)) {
            Text(
                stringResource(R.string.guide_nickname_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.guide_nickname_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            if (uiState is NicknameUiState.Loaded) {
                var nickname by rememberSaveable(uiState.nickname) { mutableStateOf(uiState.nickname) }

                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.guide_nickname_placeholder)) },
                    singleLine = true
                )

                if (uiState.error != null) {
                    Text(
                        uiState.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (uiState.justSaved) {
                    Text(
                        stringResource(R.string.guide_nickname_saved),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Button(
                    onClick = { onSaveNickname(nickname) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    enabled = !uiState.isSaving && nickname.trim() != uiState.nickname,
                    shape = BlobButtonShape,
                    contentPadding = BlobButtonPadding
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.guide_nickname_save))
                    }
                }
            } else {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
        }
    }
}

// Local-only, per-device — see QuietHoursStore/QuietHoursViewModel.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietHoursCard(
    uiState: QuietHoursUiState,
    onSetEnabled: (Boolean) -> Unit,
    onSetStart: (Int) -> Unit,
    onSetEnd: (Int) -> Unit
) {
    // null = no dialog open; true = editing start, false = editing end.
    var editingStart by remember { mutableStateOf<Boolean?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .rotate(0.4f),
        shape = BlobShapeSoftC,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(28.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.guide_quiet_hours_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.guide_quiet_hours_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Switch(checked = uiState.enabled, onCheckedChange = onSetEnabled)
            }

            if (uiState.enabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { editingStart = true },
                        modifier = Modifier.weight(1f),
                        shape = BlobButtonShape,
                        contentPadding = BlobButtonPadding
                    ) {
                        Text(stringResource(R.string.guide_quiet_hours_start, formatMinutes(uiState.startMinutes)))
                    }
                    OutlinedButton(
                        onClick = { editingStart = false },
                        modifier = Modifier.weight(1f),
                        shape = BlobButtonShape,
                        contentPadding = BlobButtonPadding
                    ) {
                        Text(stringResource(R.string.guide_quiet_hours_end, formatMinutes(uiState.endMinutes)))
                    }
                }
            }
        }
    }

    val editing = editingStart
    if (editing != null) {
        val initialMinutes = if (editing) uiState.startMinutes else uiState.endMinutes
        val timePickerState = rememberTimePickerState(
            initialHour = initialMinutes / 60,
            initialMinute = initialMinutes % 60,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { editingStart = null },
            confirmButton = {
                TextButton(onClick = {
                    val minutes = timePickerState.hour * 60 + timePickerState.minute
                    if (editing) onSetStart(minutes) else onSetEnd(minutes)
                    editingStart = null
                }) {
                    Text(stringResource(R.string.guide_quiet_hours_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingStart = null }) {
                    Text(stringResource(R.string.guide_dismiss))
                }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }
}

private fun formatMinutes(minutes: Int): String =
    LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofPattern("h:mm a"))

// Local-only, per-device — see CarouselSettingsStore/ShapedCarouselWidgetRenderer.
// The size row is always "the latest N messages" (not seen/unseen-gated),
// so it directly controls what shows up on the widget with no other state
// to reconcile. Auto-advance is a separate, independent opt-in — off by
// default, since most people would rather tap through at their own pace
// (see CarouselAutoAdvanceWorker).
@Composable
private fun CarouselSizeCard(
    uiState: CarouselSettingsUiState,
    onSetSize: (Int) -> Unit,
    onSetAutoAdvanceMinutes: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .rotate(-0.4f),
        shape = BlobShapeSoftB,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(28.dp)) {
            Text(
                stringResource(R.string.carousel_size_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.carousel_size_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            OptionButtonRow(
                options = CarouselSettingsStore.SIZE_OPTIONS,
                selected = uiState.size,
                label = { stringResource(R.string.carousel_size_option, it) },
                onSelect = onSetSize
            )

            Text(
                stringResource(R.string.carousel_auto_advance_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
            )

            OptionButtonRow(
                options = CarouselSettingsStore.AUTO_ADVANCE_MINUTES_OPTIONS,
                selected = uiState.autoAdvanceMinutes,
                label = {
                    if (it == CarouselSettingsStore.AUTO_ADVANCE_OFF) {
                        stringResource(R.string.carousel_auto_advance_off)
                    } else {
                        stringResource(R.string.carousel_auto_advance_option, it)
                    }
                },
                onSelect = onSetAutoAdvanceMinutes
            )
        }
    }
}

// Local-only, per-device — see VideoLimitStore/VideoLimitViewModel. Read by
// both the capture intent's duration-limit hint and the actual post-
// recording duration check (ComposeMessageScreen/ComposeMessageViewModel),
// so whichever one actually takes effect agrees with the other.
@Composable
private fun VideoLimitCard(uiState: VideoLimitUiState, onSetLimitSeconds: (Int) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .rotate(-0.3f),
        shape = BlobShapeSoftC,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(28.dp)) {
            Text(
                stringResource(R.string.video_limit_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.video_limit_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            OptionButtonRow(
                options = VideoLimitStore.OPTIONS,
                selected = uiState.limitSeconds,
                label = { stringResource(R.string.video_limit_option, it) },
                onSelect = onSetLimitSeconds
            )
        }
    }
}

// A "thinking of you" nudge vibrates the SENDER's name in Morse (see
// MorseVibration + NotificationChannels.thinkingOfYouChannelFor), which is
// a feature you can only otherwise discover by having someone nudge you.
// This plays any name's pattern on demand so you can learn each other's by
// feel — and pre-fills your own, since yours is the one your partner will
// be learning.
@Composable
private fun MorseCard(myDisplayName: String) {
    val context = LocalContext.current
    var name by rememberSaveable(myDisplayName) { mutableStateOf(myDisplayName) }
    val morse = MorseVibration.morseFor(name)
    val emptyLabel = stringResource(R.string.morse_empty)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .rotate(0.4f),
        shape = BlobShapeSoftC,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(28.dp)) {
            Text(
                stringResource(R.string.morse_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.morse_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.morse_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Seeing the dots and dashes is what makes the buzzing legible
            // as a name rather than just an unusual vibration.
            Text(
                text = morse.ifEmpty { emptyLabel },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )

            Button(
                onClick = { MorseVibration.play(context, name) },
                enabled = morse.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(stringResource(R.string.morse_play_button))
            }
        }
    }
}

// Local-only, per-device — see WidgetAccentColorStore/WidgetAccentColorViewModel.
// Reuses DrawingColors' palette rather than inventing a second one, so
// there's a single consistent set of "pick a color" choices across the app.
@Composable
private fun WidgetAccentColorCard(uiState: WidgetAccentColorUiState, onSetColor: (String?) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .rotate(0.5f),
        shape = BlobShapeSoftB,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(28.dp)) {
            Text(
                stringResource(R.string.widget_accent_color_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.widget_accent_color_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Resets to the widget's original color, which itself
                // already adapts to light/dark system theme — unlike the
                // fixed presets below, so it gets its own neutral swatch
                // rather than one more fixed hex value.
                val isDefault = uiState.selectedColor == null
                Box(
                    modifier = Modifier
                        .size(if (isDefault) 32.dp else 26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = if (isDefault) 2.dp else 1.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape
                        )
                        .clickable { onSetColor(null) }
                )
                DrawingColors.PALETTE.forEach { hex ->
                    val isSelected = hex == uiState.selectedColor
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 32.dp else 26.dp)
                            .clip(CircleShape)
                            .background(parseColorOrBlack(hex))
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape
                            )
                            .clickable { onSetColor(hex) }
                    )
                }
            }
        }
    }
}

private fun parseColorOrBlack(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: IllegalArgumentException) {
    Color.Black
}

@Composable
private fun OptionButtonRow(options: List<Int>, selected: Int, label: @Composable (Int) -> String, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        options.forEach { option ->
            if (selected == option) {
                Button(
                    onClick = { onSelect(option) },
                    modifier = Modifier.weight(1f),
                    shape = BlobButtonShape,
                    contentPadding = BlobButtonPadding
                ) {
                    Text(label(option))
                }
            } else {
                OutlinedButton(
                    onClick = { onSelect(option) },
                    modifier = Modifier.weight(1f),
                    shape = BlobButtonShape,
                    contentPadding = BlobButtonPadding
                ) {
                    Text(label(option))
                }
            }
        }
    }
}
