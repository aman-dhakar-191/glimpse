package com.glimpse.app.ui.countdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.glimpse.app.R

// Ambient status, not a one-time surprise like OnThisDayBanner — shown
// plainly whenever a special date is set and within its lead-up window,
// with no dismiss button, since it's meant to build anticipation across
// multiple visits rather than being acknowledged once.
private const val LEAD_UP_WINDOW_DAYS = 30

@Composable
fun CountdownBanner(uiState: CountdownUiState, modifier: Modifier = Modifier) {
    if (uiState !is CountdownUiState.Loaded) return
    val date = uiState.specialDate ?: return
    val daysUntil = uiState.daysUntil ?: return
    if (daysUntil > LEAD_UP_WINDOW_DAYS) return

    val text = when (daysUntil) {
        0 -> stringResource(R.string.countdown_banner_today, date.label)
        1 -> stringResource(R.string.countdown_banner_tomorrow, date.label)
        else -> stringResource(R.string.countdown_banner_days, date.label, daysUntil)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )
    }
}
