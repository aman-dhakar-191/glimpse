package com.glimpse.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.glimpse.app.R

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onSignInClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(horizontal = 32.dp)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        when (uiState) {
            is LoginUiState.Loading -> CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
            is LoginUiState.AccessDenied -> Text(
                stringResource(R.string.login_access_denied),
                color = Color.Red,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            is LoginUiState.Error -> Text(
                uiState.message,
                color = Color.Red,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            LoginUiState.Idle -> Unit
        }

        Button(
            onClick = onSignInClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState !is LoginUiState.Loading
        ) {
            Text(stringResource(R.string.login_button))
        }
    }
}
