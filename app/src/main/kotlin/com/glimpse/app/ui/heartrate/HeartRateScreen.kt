package com.glimpse.app.ui.heartrate

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.glimpse.app.R
import com.glimpse.app.ui.theme.BlobShapeSoftC
import java.util.concurrent.Executors

// Measures a pulse by watching a fingertip through the rear camera. This is
// the standalone reader: it proves the signal is good on real hardware
// before any of it gets sent to anyone.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateScreen(
    uiState: HeartRateUiState,
    onProbeSensors: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onFrame: (Double, Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) { onProbeSensors() }

    // Binding is tied to whether we're measuring, so the torch and camera
    // are released the moment the reading stops rather than staying lit
    // against someone's finger for as long as the screen is open.
    DisposableEffect(uiState.measuring, hasCameraPermission) {
        if (!uiState.measuring || !hasCameraPermission) return@DisposableEffect onDispose { }

        val executor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var boundProvider: ProcessCameraProvider? = null

        providerFuture.addListener({
            val provider = providerFuture.get()
            boundProvider = provider
            val analysis = ImageAnalysis.Builder()
                // Only the newest frame matters; a backlog would make the
                // sample timestamps lie about when the light was measured.
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { image ->
                try {
                    onFrame(
                        com.glimpse.app.data.heartrate.CameraLuma.averageCentreLuma(image),
                        System.currentTimeMillis()
                    )
                } finally {
                    image.close()
                }
            }
            try {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    analysis
                )
                // Without the torch there is nothing to see through a
                // finger — the light source is half the measurement.
                camera.cameraControl.enableTorch(true)
            } catch (_: Exception) {
                // Camera unavailable (in use elsewhere, or no back camera).
                // The screen stays on its "no reading" state rather than
                // crashing someone's phone mid-measurement.
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            boundProvider?.unbindAll()
            executor.shutdown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.heart_rate_title), style = MaterialTheme.typography.titleMedium) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.heart_rate_instructions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = BlobShapeSoftC,
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = uiState.bpm?.let { stringResource(R.string.heart_rate_bpm, it) }
                            ?: stringResource(R.string.heart_rate_searching),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = stringResource(R.string.heart_rate_confidence, (uiState.confidence * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.trustworthy) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    LinearProgressIndicator(
                        progress = { uiState.confidence.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )

                    // The waveform is the whole diagnostic. A clean run of
                    // evenly spaced humps means it's working; a flat line
                    // means the finger isn't sealing the lens, and chaos
                    // means it's moving. None of that is knowable from a
                    // number alone.
                    Waveform(
                        samples = uiState.waveform,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(top = 16.dp)
                    )
                }
            }

            if (!hasCameraPermission) {
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.heart_rate_grant_camera))
                }
            } else if (uiState.measuring) {
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.heart_rate_stop))
                }
            } else {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.heart_rate_start))
                }
            }

            if (uiState.sensorReport.isNotBlank()) {
                Text(
                    text = uiState.sensorReport,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (uiState.measuring) {
                Text(
                    text = stringResource(
                        R.string.heart_rate_debug,
                        uiState.samplesCollected,
                        uiState.meanLevel.toInt()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Waveform(samples: List<Float>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        if (samples.size < 2) return@Canvas
        val midY = size.height / 2f
        val stepX = size.width / (samples.size - 1)
        val path = Path()
        samples.forEachIndexed { index, value ->
            // Padded slightly so a full-scale sample doesn't clip against
            // the very edge of the canvas.
            val y = midY - value.coerceIn(-1f, 1f) * midY * 0.9f
            val point = Offset(index * stepX, y)
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 3f))
    }
}
