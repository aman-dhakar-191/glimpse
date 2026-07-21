package com.glimpse.app.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.glimpse.app.R
import com.glimpse.app.ui.theme.BlobButtonShape
import com.glimpse.app.ui.theme.BlobShapeSoftB

private val BlobButtonPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)

// Reached by tapping "Update" on the compose-screen banner or an
// update-available notification — the banner itself no longer downloads
// directly, it just navigates here (see MainActivity.onUpdateClick vs. the
// banner's onUpdateClick wiring).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    uiState: UpdateUiState,
    currentVersionName: String,
    currentVersionCode: Int,
    onCheckForUpdate: () -> Unit,
    onInstallClick: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.update_screen_title), style = MaterialTheme.typography.titleMedium) },
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
            Text(
                stringResource(R.string.update_current_version, currentVersionName, currentVersionCode),
                style = MaterialTheme.typography.bodyLarge
            )

            // Always available regardless of state, not just when Idle/Error
            // — lets you manually re-check any time (e.g. after installing,
            // or just to be sure) instead of only reacting to a notification.
            OutlinedButton(
                onClick = onCheckForUpdate,
                enabled = uiState !is UpdateUiState.Checking && uiState !is UpdateUiState.Downloading,
                shape = BlobButtonShape,
                contentPadding = BlobButtonPadding
            ) {
                Text(stringResource(R.string.update_check_button))
            }

            when (uiState) {
                is UpdateUiState.Idle -> Text(
                    stringResource(R.string.update_up_to_date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                is UpdateUiState.Checking -> Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.update_checking), style = MaterialTheme.typography.bodyLarge)
                }

                is UpdateUiState.Available -> {
                    Text(
                        stringResource(R.string.update_latest_version, uiState.info.versionName, uiState.info.versionCode),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Button(
                        onClick = onInstallClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = BlobButtonShape,
                        contentPadding = BlobButtonPadding
                    ) {
                        Text(stringResource(R.string.update_button))
                    }
                    if (uiState.info.releaseNotes.isNotBlank()) {
                        ReleaseNotesCard(uiState.info.releaseNotes)
                    }
                }

                is UpdateUiState.Downloading -> Column {
                    Text(stringResource(R.string.update_downloading), style = MaterialTheme.typography.bodyLarge)
                    LinearProgressIndicator(
                        progress = { uiState.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }

                is UpdateUiState.ReadyToInstall -> Text(
                    stringResource(R.string.update_installing_hint),
                    style = MaterialTheme.typography.bodyMedium
                )

                is UpdateUiState.Error -> Text(
                    uiState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ReleaseNotesCard(notes: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BlobShapeSoftB,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(stringResource(R.string.update_release_notes_title), style = MaterialTheme.typography.titleMedium)
            Text(
                notes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
