package com.glimpse.app.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.glimpse.app.R
import com.glimpse.app.ui.theme.BlobShapeSoftA
import com.glimpse.app.ui.theme.BlobShapeSoftB
import com.glimpse.app.ui.theme.BlobShapeSoftC

// Just the "how to add the widget" steps — pairing, nickname, mood,
// countdown, quiet hours, and log out all live in SettingsScreen instead,
// reached from ComposeMessageScreen's own top-bar link.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetGuideScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.guide_title), style = MaterialTheme.typography.titleMedium) },
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
            StepCard(1, stringResource(R.string.guide_step_1_title), stringResource(R.string.guide_step_1_desc), BlobShapeSoftA, -0.6f)
            StepCard(2, stringResource(R.string.guide_step_2_title), stringResource(R.string.guide_step_2_desc), BlobShapeSoftB, 0.5f)
            StepCard(3, stringResource(R.string.guide_step_3_title), stringResource(R.string.guide_step_3_desc), BlobShapeSoftC, -0.4f)
            StepCard(4, stringResource(R.string.guide_step_4_title), stringResource(R.string.guide_step_4_desc), BlobShapeSoftA, 0.6f)
            StepCard(5, stringResource(R.string.guide_step_5_title), stringResource(R.string.guide_step_5_desc), BlobShapeSoftB, -0.5f)
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
