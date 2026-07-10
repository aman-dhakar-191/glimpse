package com.glimpse.app.ui.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

private val QUICK_EMOJIS = listOf("❤️", "😊", "👍", "😂", "🎉")

@Composable
fun ComposeMessageScreen(
    uiState: ComposeUiState,
    onSend: (String) -> Unit,
    onSentHandled: () -> Unit,
    onOpenGuide: () -> Unit,
    onLogout: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val sentMessage = stringResource(R.string.compose_sent)

    LaunchedEffect(uiState) {
        if (uiState is ComposeUiState.Sent) {
            text = ""
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
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Row {
                    TextButton(onClick = onOpenGuide) {
                        Text(stringResource(R.string.compose_widget_guide_link))
                    }
                    TextButton(onClick = onLogout) {
                        Text(stringResource(R.string.guide_logout))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(stringResource(R.string.compose_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.compose_placeholder)) },
                minLines = 3
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_EMOJIS.forEach { emoji ->
                    OutlinedButton(onClick = { text += emoji }) {
                        Text(emoji)
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

            Button(
                onClick = { onSend(text) },
                enabled = text.isNotBlank() && uiState !is ComposeUiState.Sending,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState is ComposeUiState.Sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.compose_send))
                }
            }
        }
    }
}
