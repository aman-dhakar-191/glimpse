package com.glimpse.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.glimpse.app.R
import com.glimpse.app.ui.theme.BlobChipShapeA
import com.glimpse.app.ui.theme.BlobChipShapeB
import com.glimpse.app.ui.theme.BlobShapeA
import com.glimpse.app.ui.theme.BlobShapeB
import com.glimpse.app.ui.theme.BlobShapeC
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    uiState: StatsUiState,
    onLoad: () -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) { onLoad() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { innerPadding ->
        when (uiState) {
            is StatsUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            is StatsUiState.Loaded -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StreakCard(uiState.streakDays)

                StatRow {
                    StatTile(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.stats_total_messages),
                        value = uiState.totalMessages.toString(),
                        shape = BlobChipShapeA
                    )
                    StatTile(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.stats_first_glimpse),
                        value = uiState.firstMessageAt?.let(::formatDate) ?: "—",
                        shape = BlobChipShapeB
                    )
                }

                if (uiState.countsByAuthor.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = BlobShapeC,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.stats_by_person), style = MaterialTheme.typography.titleMedium)
                            uiState.countsByAuthor.forEach { person ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(person.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        person.count.toString(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                uiState.topReaction?.let { (emoji, count) ->
                    StatTile(
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.stats_top_reaction),
                        value = "$emoji  ×$count",
                        shape = BlobShapeB
                    )
                }
            }
        }
    }
}

@Composable
private fun StreakCard(days: Int) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = BlobShapeA,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🔥", style = MaterialTheme.typography.headlineSmall)
            Text(
                pluralStreakLabel(days),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun pluralStreakLabel(days: Int): String =
    if (days == 1) {
        stringResource(R.string.stats_streak_one)
    } else {
        stringResource(R.string.stats_streak_many, days)
    }

@Composable
private fun StatRow(content: @Composable RowScope.() -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun StatTile(modifier: Modifier = Modifier, label: String, value: String, shape: Shape) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDate(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}
