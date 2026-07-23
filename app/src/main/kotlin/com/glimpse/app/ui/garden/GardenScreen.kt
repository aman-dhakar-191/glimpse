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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.glimpse.app.R
import com.glimpse.app.data.GardenStage
import com.glimpse.app.data.GardenWeather
import kotlin.random.Random

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
                is GardenUiState.Loaded -> {
                    GardenSky(weather = uiState.weather, modifier = Modifier.fillMaxSize())
                    GardenContent(uiState)
                }
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
        // The sky behind this ranges from near-black (Starry/Stormy) to
        // pale pastel (Sunny/Festive) — fixed theme text color alone can't
        // stay readable across that whole range, so the text (not the
        // plant itself, which is saturated enough to read against any of
        // them) gets its own opaque card, same idea as DrawingScreen's
        // floating overlay panels.
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shape = MaterialTheme.shapes.large,
            shadowElevation = 4.dp
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
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
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Text(
                    stageLabel(uiState.stage),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    stringResource(R.string.garden_streak_days, uiState.streakDays),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Canvas(modifier = Modifier.size(240.dp).padding(top = 12.dp)) {
            drawGarden(stage = uiState.stage, isWilting = uiState.isWilting, seedCount = uiState.pendingSeeds.size)
        }

        if (uiState.isWilting) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    stringResource(R.string.garden_wilting_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        if (uiState.pendingSeeds.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = MaterialTheme.shapes.large,
                shadowElevation = 4.dp,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text(
                        stringResource(R.string.garden_seeds_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    uiState.pendingSeeds.forEach { seed ->
                        Text(
                            seedLabel(seed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun seedLabel(seed: GardenSeed): String = if (seed.daysUntilBloom <= 0) {
    stringResource(if (seed.plantedByMe) R.string.garden_seed_you_today else R.string.garden_seed_partner_today)
} else {
    stringResource(
        if (seed.plantedByMe) R.string.garden_seed_you else R.string.garden_seed_partner,
        seed.daysUntilBloom
    )
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

// A full-screen gradient + a handful of small decorations behind the
// plant — your OWN current mood (see GardenViewModel), not a second mood
// picker. Deliberately static, not animated (no rain falling, no
// twinkling stars) — enough to read as "sunny"/"rainy"/etc. from the
// gradient + iconography alone, matching how simply everything else in
// this app is drawn.
@Composable
private fun GardenSky(weather: GardenWeather, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val (topColor, bottomColor) = skyColors(weather)
        drawRect(brush = Brush.verticalGradient(listOf(topColor, bottomColor)))
        drawWeatherDecoration(weather)
    }
}

private fun skyColors(weather: GardenWeather): Pair<Color, Color> = when (weather) {
    GardenWeather.Sunny -> Color(0xFF74B9FF) to Color(0xFFFFF3B0)
    GardenWeather.Starry -> Color(0xFF0D1B4C) to Color(0xFF3A2B6E)
    GardenWeather.Rainy -> Color(0xFF5C7A99) to Color(0xFF8DA3B8)
    GardenWeather.Stormy -> Color(0xFF2B2B36) to Color(0xFF4A2E3A)
    GardenWeather.Cloudy -> Color(0xFFB8C2CC) to Color(0xFFE8ECEF)
    GardenWeather.Foggy -> Color(0xFFC9D1CB) to Color(0xFFDDE3DD)
    GardenWeather.Festive -> Color(0xFFFF9A76) to Color(0xFFFFD3E0)
    GardenWeather.Clear -> Color(0xFFBBDDEE) to Color(0xFFF5F9FB)
}

private fun DrawScope.drawWeatherDecoration(weather: GardenWeather) {
    when (weather) {
        GardenWeather.Sunny -> drawSun()
        GardenWeather.Starry -> drawStarsAndMoon()
        GardenWeather.Rainy -> {
            drawClouds(color = Color.White, alpha = 0.7f)
            drawRain()
        }
        GardenWeather.Stormy -> drawStormClouds()
        GardenWeather.Cloudy -> drawClouds()
        GardenWeather.Foggy -> drawFogHaze()
        GardenWeather.Festive -> drawConfetti()
        GardenWeather.Clear -> Unit
    }
}

private fun DrawScope.drawSun() {
    val center = Offset(size.width * 0.8f, size.height * 0.12f)
    val r = size.width * 0.08f
    repeat(8) { i ->
        val angle = 2 * Math.PI * i / 8
        val start = Offset(
            center.x + (r * 1.3f * kotlin.math.cos(angle)).toFloat(),
            center.y + (r * 1.3f * kotlin.math.sin(angle)).toFloat()
        )
        val end = Offset(
            center.x + (r * 1.8f * kotlin.math.cos(angle)).toFloat(),
            center.y + (r * 1.8f * kotlin.math.sin(angle)).toFloat()
        )
        drawLine(color = Color(0xFFFFD54F), start = start, end = end, strokeWidth = size.width * 0.01f, cap = StrokeCap.Round)
    }
    drawCircle(color = Color(0xFFFFD54F), radius = r, center = center)
}

private fun DrawScope.drawStarsAndMoon() {
    drawCircle(
        color = Color(0xFFF5F0DC),
        radius = size.width * 0.06f,
        center = Offset(size.width * 0.78f, size.height * 0.14f)
    )
    val random = Random(42)
    repeat(18) {
        val point = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height * 0.6f)
        drawCircle(color = Color.White.copy(alpha = 0.8f), radius = size.width * 0.006f, center = point)
    }
}

private fun DrawScope.drawRain() {
    val random = Random(7)
    repeat(24) {
        val x = random.nextFloat() * size.width
        val yStart = random.nextFloat() * size.height * 0.7f
        drawLine(
            color = Color(0xFFAFC9E0).copy(alpha = 0.8f),
            start = Offset(x, yStart),
            end = Offset(x - size.width * 0.02f, yStart + size.height * 0.05f),
            strokeWidth = size.width * 0.006f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawStormClouds() {
    drawClouds(color = Color(0xFF3A3A45), alpha = 1f)
    val boltPath = Path().apply {
        moveTo(size.width * 0.55f, size.height * 0.1f)
        lineTo(size.width * 0.48f, size.height * 0.28f)
        lineTo(size.width * 0.58f, size.height * 0.3f)
        lineTo(size.width * 0.5f, size.height * 0.5f)
    }
    drawPath(boltPath, color = Color(0xFFFFEB3B), style = Stroke(width = size.width * 0.012f, cap = StrokeCap.Round))
}

private fun DrawScope.drawClouds(color: Color = Color.White, alpha: Float = 0.85f) {
    val positions = listOf(0.2f to 0.12f, 0.5f to 0.08f, 0.75f to 0.16f)
    positions.forEach { (fx, fy) ->
        val cx = size.width * fx
        val cy = size.height * fy
        val r = size.width * 0.07f
        val tinted = color.copy(alpha = alpha)
        drawCircle(color = tinted, radius = r, center = Offset(cx, cy))
        drawCircle(color = tinted, radius = r * 0.7f, center = Offset(cx + r, cy + r * 0.2f))
        drawCircle(color = tinted, radius = r * 0.7f, center = Offset(cx - r, cy + r * 0.2f))
    }
}

private fun DrawScope.drawFogHaze() {
    repeat(4) { i ->
        val y = size.height * (0.15f + i * 0.12f)
        drawRect(
            color = Color.White.copy(alpha = 0.35f),
            topLeft = Offset(0f, y),
            size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.05f)
        )
    }
}

private fun DrawScope.drawConfetti() {
    val colors = listOf(Color(0xFFFF7597), Color(0xFFFFD166), Color(0xFF6FCF97), Color(0xFF56CCF2))
    val random = Random(3)
    repeat(20) {
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height * 0.7f
        val pieceColor = colors[random.nextInt(colors.size)]
        rotate(degrees = random.nextFloat() * 360f, pivot = Offset(x, y)) {
            drawRect(
                color = pieceColor,
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(size.width * 0.015f, size.width * 0.03f)
            )
        }
    }
}

// Pot + soil are fixed; the stem/leaves/bloom scale up with stage and droop
// sideways (rather than just shrinking) when wilting, since a wilted plant
// still HAS whatever it grew — it just isn't standing up straight anymore.
// seedCount (capped at 3 dots — the text list below covers the exact
// count) plants a few small still-buried dots off to one side of the
// stem, standing in for time-capsule messages waiting to unlock.
private fun DrawScope.drawGarden(stage: GardenStage, isWilting: Boolean, seedCount: Int = 0) {
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

    repeat(seedCount.coerceAtMost(3)) { i ->
        val dotX = soilCenter.x + potWidth * (0.22f + i * 0.1f)
        drawCircle(color = Color(0xFF3E2A1A), radius = w * 0.012f, center = Offset(dotX, soilCenter.y))
    }

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
