package com.glimpse.app.ui.reaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.glimpse.app.R

// Shown as one-tap shortcuts above the free-form field — typing/picking any
// other emoji still works via the keyboard's own emoji tab, this list just
// covers the common cases without needing to switch keyboards.
private val SUGGESTED_EMOJIS = listOf(
    "❤️", "😊", "👍", "😂", "🎉", "😢", "😮", "🔥", "👏", "💯", "🥰", "😍"
)

@Composable
fun ReactionPickerScreen(
    uiState: ReactUiState,
    onReact: (String) -> Unit,
    onSentHandled: () -> Unit,
    onDismiss: () -> Unit
) {
    var emoji by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val sentMessage = stringResource(R.string.react_sent)

    LaunchedEffect(uiState) {
        if (uiState is ReactUiState.Sent) {
            emoji = ""
            snackbarHostState.showSnackbar(sentMessage)
            onSentHandled()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.react_title), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }

            Spacer(Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(SUGGESTED_EMOJIS) { suggestion ->
                    OutlinedButton(
                        onClick = { onReact(suggestion) },
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text(suggestion, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = emoji,
                onValueChange = { emoji = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.react_placeholder)) },
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            if (uiState is ReactUiState.Error) {
                Text(
                    uiState.message,
                    color = Color.Red,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (uiState is ReactUiState.Queued) {
                Text(
                    stringResource(R.string.react_queued),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Button(
                onClick = { onReact(emoji) },
                enabled = uiState !is ReactUiState.Sending && uiState !is ReactUiState.Queued && emoji.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState is ReactUiState.Sending || uiState is ReactUiState.Queued) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.react_send))
                }
            }
        }
    }
}
