package com.glimpse.app.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.glimpse.app.R
import com.glimpse.app.ui.countdown.CountdownUiState
import com.glimpse.app.ui.mood.MoodUiState
import com.glimpse.app.ui.nickname.NicknameUiState
import com.glimpse.app.ui.pairing.PairingUiState
import com.glimpse.app.ui.quiethours.QuietHoursUiState
import com.glimpse.app.ui.theme.BlobButtonShape
import com.glimpse.app.ui.theme.BlobChipShapeA
import com.glimpse.app.ui.theme.BlobChipShapeB
import com.glimpse.app.ui.theme.BlobShapeSoftA
import com.glimpse.app.ui.theme.BlobShapeSoftB
import com.glimpse.app.ui.theme.BlobShapeSoftC
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Generous, matching the same reasoning as the login/compose blob buttons —
// these shapes pinch inward at the edges more than a plain pill would.
private val BlobButtonPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)

@Composable
fun WidgetGuideScreen(
    pairingUiState: PairingUiState,
    onGenerateCode: () -> Unit,
    nicknameUiState: NicknameUiState,
    onLoadNickname: () -> Unit,
    onSaveNickname: (String) -> Unit,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    moodUiState: MoodUiState,
    onLoadMood: () -> Unit,
    onSetMood: (String) -> Unit,
    countdownUiState: CountdownUiState,
    onLoadCountdown: () -> Unit,
    onSetCountdown: (label: String, month: Int, day: Int) -> Unit,
    onClearCountdown: () -> Unit,
    quietHoursUiState: QuietHoursUiState,
    onLoadQuietHours: () -> Unit,
    onSetQuietHoursEnabled: (Boolean) -> Unit,
    onSetQuietHoursStart: (Int) -> Unit,
    onSetQuietHoursEnd: (Int) -> Unit
) {
    LaunchedEffect(Unit) {
        onLoadNickname()
        onLoadMood()
        onLoadCountdown()
        onLoadQuietHours()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.guide_title),
            style = MaterialTheme.typography.headlineSmall
        )

        StepCard(1, stringResource(R.string.guide_step_1_title), stringResource(R.string.guide_step_1_desc), BlobShapeSoftA, -0.6f)
        StepCard(2, stringResource(R.string.guide_step_2_title), stringResource(R.string.guide_step_2_desc), BlobShapeSoftB, 0.5f)
        StepCard(3, stringResource(R.string.guide_step_3_title), stringResource(R.string.guide_step_3_desc), BlobShapeSoftC, -0.4f)
        StepCard(4, stringResource(R.string.guide_step_4_title), stringResource(R.string.guide_step_4_desc), BlobShapeSoftA, 0.6f)
        StepCard(5, stringResource(R.string.guide_step_5_title), stringResource(R.string.guide_step_5_desc), BlobShapeSoftB, -0.5f)

        InviteCard(pairingUiState, onGenerateCode)

        NicknameCard(nicknameUiState, onSaveNickname)

        MoodCard(moodUiState, onSetMood)

        CountdownCard(countdownUiState, onSetCountdown, onClearCountdown)

        QuietHoursCard(quietHoursUiState, onSetQuietHoursEnabled, onSetQuietHoursStart, onSetQuietHoursEnd)

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape = BlobButtonShape,
            contentPadding = BlobButtonPadding
        ) {
            Text(stringResource(R.string.guide_dismiss))
        }

        TextButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.guide_logout))
        }
    }
}

@Composable
private fun StepCard(number: Int, title: String, description: String, shape: Shape, tiltDegrees: Float) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .rotate(tiltDegrees),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(28.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("$number", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineSmall)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall)
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

// Shared status line — see FirebaseSync.setMood/MoodViewModel for why this
// one (unlike nicknames and the background photo) is visible to your
// partner.
private val MOOD_EMOJIS = listOf("😊", "🥰", "😴", "😢", "😡", "😐", "🤒", "🎉")

@Composable
private fun MoodCard(uiState: MoodUiState, onSetMood: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .rotate(0.5f),
        shape = BlobShapeSoftC,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(28.dp)) {
            Text(
                stringResource(R.string.guide_mood_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.guide_mood_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            if (uiState is MoodUiState.Loaded) {
                if (uiState.error != null) {
                    Text(
                        uiState.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                val rows = MOOD_EMOJIS.chunked(4)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEachIndexed { index, emoji ->
                                val shape = if (index % 2 == 0) BlobChipShapeA else BlobChipShapeB
                                val isSelected = emoji == uiState.currentEmoji
                                OutlinedButton(
                                    onClick = { onSetMood(emoji) },
                                    enabled = !uiState.isSaving,
                                    shape = shape,
                                    contentPadding = PaddingValues(14.dp),
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
                }
            } else {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
        }
    }
}

// Shared, single countdown for the pair — see FirebaseSync.setSpecialDate/
// CountdownViewModel for why either of you setting this changes it for both.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountdownCard(
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
        shape = BlobShapeSoftB,
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
                        contentPadding = BlobButtonPadding
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
                            contentPadding = BlobButtonPadding
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
                        // DatePicker's selectedDateMillis is UTC midnight for the
                        // picked calendar day — reading it back with UTC (not the
                        // device's own zone) is what keeps the day that was
                        // visually tapped from shifting by one in a timezone west
                        // of UTC.
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
