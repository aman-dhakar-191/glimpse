package com.glimpse.app.ui.garden

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.glimpse.app.R
import com.glimpse.app.data.GardenStage

// A healthy stem/leaf green, independent of the theme (the plant should
// read as a plant regardless of light/dark mode or accent color) — dulled
// toward WILTED_GREEN when isWilting, same idea as a real plant browning.
private val HEALTHY_GREEN = Color(0xFF4CAF50)
private val WILTED_GREEN = Color(0xFF9C8A5E)
private val POT_COLOR = Color(0xFFB0704A)
private val SOIL_COLOR = Color(0xFF6B4A34)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GardenScreen(
    uiState: GardenUiState,
    onLoad: () -> Unit,
    onNameGarden: (String) -> Unit,
    onNameErrorHandled: () -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) { onLoad() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.garden_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            when (uiState) {
                is GardenUiState.Loading -> CircularProgressIndicator()
                is GardenUiState.Loaded -> GardenContent(uiState)
            }
        }
    }

    val loaded = uiState as? GardenUiState.Loaded
    if (loaded != null && !loaded.isNamed) {
        NameGardenDialog(
            isNaming = loaded.isNaming,
            error = loaded.nameError,
            onConfirm = onNameGarden,
            onErrorHandled = onNameErrorHandled
        )
    }
}

@Composable
private fun GardenContent(uiState: GardenUiState.Loaded) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(24.dp)
    ) {
        if (uiState.isNamed) {
            Text(
                "\"${uiState.gardenName}\"",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                stringResource(
                    if (uiState.namedByMe) R.string.garden_named_by_you else R.string.garden_named_by_partner
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
        }

        Canvas(modifier = Modifier.size(240.dp)) {
            drawGarden(stage = uiState.stage, isWilting = uiState.isWilting)
        }

        Text(
            stageLabel(uiState.stage),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            stringResource(R.string.garden_streak_days, uiState.streakDays),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (uiState.isWilting) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    stringResource(R.string.garden_wilting_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun stageLabel(stage: GardenStage): String = stringResource(
    when (stage) {
        GardenStage.Seed -> R.string.garden_stage_seed
        GardenStage.Sprout -> R.string.garden_stage_sprout
        GardenStage.Budding -> R.string.garden_stage_budding
        GardenStage.Blooming -> R.string.garden_stage_blooming
        GardenStage.Flourishing -> R.string.garden_stage_flourishing
    }
)

@Composable
private fun NameGardenDialog(
    isNaming: Boolean,
    error: String?,
    onConfirm: (String) -> Unit,
    onErrorHandled: () -> Unit
) {
    var name by remember { mutableStateOf("") }

    // Not dismissible by tapping outside/back — naming it is the one thing
    // that has to happen before anything else here means anything, same
    // "can't skip this" reasoning as the pairing-code screen.
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.garden_name_prompt_title)) },
        text = {
            Column {
                Text(stringResource(R.string.garden_name_prompt_message))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (error != null) onErrorHandled()
                    },
                    placeholder = { Text(stringResource(R.string.garden_name_placeholder)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank() && !isNaming
            ) {
                if (isNaming) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text(stringResource(R.string.garden_name_confirm))
                }
            }
        }
    )
}

// Pot + soil are fixed; the stem/leaves/bloom scale up with stage and droop
// sideways (rather than just shrinking) when wilting, since a wilted plant
// still HAS whatever it grew — it just isn't standing up straight anymore.
private fun DrawScope.drawGarden(stage: GardenStage, isWilting: Boolean) {
    val w = size.width
    val h = size.height
    val potTop = h * 0.78f
    val potWidth = w * 0.5f
    val potLeft = (w - potWidth) / 2f

    drawRoundRect(
        color = POT_COLOR,
        topLeft = Offset(potLeft, potTop),
        size = androidx.compose.ui.geometry.Size(potWidth, h * 0.18f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f)
    )
    val soilCenter = Offset(w / 2f, potTop)
    drawOval(
        color = SOIL_COLOR,
        topLeft = Offset(soilCenter.x - potWidth * 0.42f, soilCenter.y - h * 0.03f),
        size = androidx.compose.ui.geometry.Size(potWidth * 0.84f, h * 0.06f)
    )

    if (stage == GardenStage.Seed) return

    val stemColor = if (isWilting) WILTED_GREEN else HEALTHY_GREEN
    val stemHeight = h * (0.12f + 0.11f * stage.ordinal)
    // A gentle sideways lean, exaggerated when wilting — a straight-up
    // stem when healthy, visibly drooping to one side when it isn't.
    val leanX = if (isWilting) w * 0.14f else w * 0.03f
    val stemTop = Offset(soilCenter.x + leanX, soilCenter.y - stemHeight)

    drawLine(
        color = stemColor,
        start = soilCenter,
        end = stemTop,
        strokeWidth = w * 0.03f,
        cap = StrokeCap.Round,
        pathEffect = if (isWilting) PathEffect.cornerPathEffect(w * 0.1f) else null
    )

    val leafPairs = 1 + stage.ordinal
    for (i in 1..leafPairs) {
        val t = i / (leafPairs + 1f)
        val jointY = soilCenter.y - stemHeight * t
        val jointX = soilCenter.x + leanX * t
        drawLeaf(center = Offset(jointX, jointY), pointingRight = i % 2 == 0, color = stemColor, scale = w * 0.09f)
    }

    if (stage == GardenStage.Blooming || stage == GardenStage.Flourishing) {
        val petalCount = if (stage == GardenStage.Flourishing) 8 else 5
        drawBloom(center = stemTop, radius = w * (if (stage == GardenStage.Flourishing) 0.11f else 0.08f), petals = petalCount)
    } else if (stage == GardenStage.Budding) {
        drawOval(
            color = stemColor,
            topLeft = Offset(stemTop.x - w * 0.035f, stemTop.y - w * 0.045f),
            size = androidx.compose.ui.geometry.Size(w * 0.07f, w * 0.09f)
        )
    }
}

private fun DrawScope.drawLeaf(center: Offset, pointingRight: Boolean, color: Color, scale: Float) {
    val dx = if (pointingRight) scale else -scale
    drawOval(
        color = color,
        topLeft = Offset(center.x + minOf(dx, 0f), center.y - scale * 0.35f),
        size = androidx.compose.ui.geometry.Size(kotlin.math.abs(dx), scale * 0.7f)
    )
}

private fun DrawScope.drawBloom(center: Offset, radius: Float, petals: Int) {
    val petalColor = Color(0xFFF48FB1)
    repeat(petals) { i ->
        val angle = (2 * Math.PI * i / petals)
        val petalCenter = Offset(
            center.x + (radius * 1.1f * kotlin.math.cos(angle)).toFloat(),
            center.y + (radius * 1.1f * kotlin.math.sin(angle)).toFloat()
        )
        drawCircle(color = petalColor, radius = radius * 0.55f, center = petalCenter)
    }
    drawCircle(color = Color(0xFFFFC107), radius = radius * 0.5f, center = center)
}
