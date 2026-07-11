package com.glimpse.app.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.glimpse.app.R
import com.glimpse.app.ui.nickname.NicknameUiState
import com.glimpse.app.ui.pairing.PairingUiState

@Composable
fun WidgetGuideScreen(
    pairingUiState: PairingUiState,
    onGenerateCode: () -> Unit,
    nicknameUiState: NicknameUiState,
    onLoadNickname: () -> Unit,
    onSaveNickname: (String) -> Unit,
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    LaunchedEffect(Unit) { onLoadNickname() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.guide_title),
            style = MaterialTheme.typography.headlineSmall
        )

        StepCard(1, stringResource(R.string.guide_step_1_title), stringResource(R.string.guide_step_1_desc))
        StepCard(2, stringResource(R.string.guide_step_2_title), stringResource(R.string.guide_step_2_desc))
        StepCard(3, stringResource(R.string.guide_step_3_title), stringResource(R.string.guide_step_3_desc))
        StepCard(4, stringResource(R.string.guide_step_4_title), stringResource(R.string.guide_step_4_desc))
        StepCard(5, stringResource(R.string.guide_step_5_title), stringResource(R.string.guide_step_5_desc))

        InviteCard(pairingUiState, onGenerateCode)

        NicknameCard(nicknameUiState, onSaveNickname)

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
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
private fun StepCard(number: Int, title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        modifier = Modifier.fillMaxWidth()
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
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) {
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
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                    enabled = !uiState.isSaving && nickname.trim() != uiState.nickname
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
