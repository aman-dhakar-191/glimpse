package com.glimpse.app.ui.onthisday

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.glimpse.app.R

@Composable
fun OnThisDayBanner(
    uiState: OnThisDayUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (uiState !is OnThisDayUiState.Found) return

    val preview = when (uiState.message.type) {
        "photo" -> stringResource(R.string.on_this_day_photo_preview)
        "drawing" -> stringResource(R.string.on_this_day_drawing_preview)
        else -> uiState.message.content
    }
    val periodLabel = stringResource(
        when (uiState.period) {
            LookbackPeriod.WEEK -> R.string.on_this_day_week
            LookbackPeriod.MONTH -> R.string.on_this_day_month
            LookbackPeriod.THREE_MONTHS -> R.string.on_this_day_three_months
            LookbackPeriod.SIX_MONTHS -> R.string.on_this_day_six_months
            LookbackPeriod.YEAR -> R.string.on_this_day_year
        }
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                stringResource(R.string.on_this_day_title, periodLabel),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                preview,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_dismiss)) }
            }
        }
    }
}
