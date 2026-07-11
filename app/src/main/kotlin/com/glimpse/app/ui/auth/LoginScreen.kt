package com.glimpse.app.ui.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.glimpse.app.R

@Composable
fun LoginScreen(
    uiState: LoginUiState,
    onSignInClick: () -> Unit,
    onRedeemCode: (String) -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(horizontal = 32.dp)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // A one-shot bouncy scale-in on first appearance, layered with a
        // slow, gentle continuous "breathing" pulse — deliberately calmer
        // (smaller amplitude, slower period) than the photo-upload pulse in
        // ComposeMessageScreen, which is meant to read as active progress
        // rather than a quiet welcoming hero.
        val entranceScale = remember { Animatable(0.6f) }
        LaunchedEffect(Unit) {
            entranceScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        val breathTransition = rememberInfiniteTransition(label = "login-heart-breathe")
        val breathScale by breathTransition.animateFloat(
            initialValue = 0.97f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breath"
        )

        Box(
            modifier = Modifier
                .size(64.dp)
                .scale(entranceScale.value * breathScale)
                .background(
                    brush = Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                    ),
                    shape = RoundedCornerShape(22.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_heart),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Text(
            stringResource(R.string.login_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        if (uiState is LoginUiState.Error) {
            Text(
                uiState.message,
                color = Color.Red,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        if (uiState is LoginUiState.NeedsPairing) {
            PairingCodeEntry(
                isSubmitting = uiState.isSubmitting,
                error = uiState.error,
                onRedeemCode = onRedeemCode,
                onLogout = onLogout
            )
        } else {
            if (uiState is LoginUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
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
}

@Composable
private fun PairingCodeEntry(
    isSubmitting: Boolean,
    error: String?,
    onRedeemCode: (String) -> Unit,
    onLogout: () -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }

    Text(
        stringResource(R.string.login_needs_pairing_title),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    Text(
        stringResource(R.string.login_needs_pairing_desc),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 16.dp)
    )
    if (error != null) {
        Text(
            error,
            color = Color.Red,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.filter(Char::isDigit).take(6) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.login_pairing_code_placeholder)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    Button(
        onClick = { onRedeemCode(code) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        enabled = !isSubmitting && code.length == 6
    ) {
        if (isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(stringResource(R.string.login_join))
        }
    }
    TextButton(onClick = onLogout, modifier = Modifier.padding(top = 4.dp)) {
        Text(stringResource(R.string.guide_logout))
    }
}
