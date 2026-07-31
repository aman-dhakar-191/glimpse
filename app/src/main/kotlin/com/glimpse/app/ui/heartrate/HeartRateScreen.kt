package com.glimpse.app.ui.heartrate

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.glimpse.app.R
import com.glimpse.app.ui.theme.BlobShapeSoftC
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

// Long enough for the torch to reach full brightness and auto-exposure to
// stop hunting, short enough not to feel like the app has hung.
private const val EXPOSURE_SETTLE_MILLIS = 1_500L

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
    onFrame: (com.glimpse.app.data.heartrate.CameraLuma.Frame, Long) -> Unit,
    onSettled: () -> Unit,
    onSetSharing: (Boolean) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
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
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(Unit) { onProbeSensors() }

    // Listening runs for the whole time this screen is open, not only while
    // measuring — otherwise a heart offered would only ever arrive if the
    // other person happened to be measuring at the same moment.
    DisposableEffect(Unit) {
        onStartListening()
        onDispose { onStopListening() }
    }

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
                        com.glimpse.app.data.heartrate.CameraLuma.analyse(image),
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
                boundCamera = camera
            } catch (_: Exception) {
                // Camera unavailable (in use elsewhere, or no back camera).
                // The screen stays on its "no reading" state rather than
                // crashing someone's phone mid-measurement.
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            boundCamera = null
            boundProvider?.unbindAll()
            executor.shutdown()
        }
    }

    // The single most important thing this screen does, and it isn't
    // obvious: auto-exposure has to be switched off before measuring.
    //
    // A camera's whole job is to hold brightness constant, so it quietly
    // compensates for exactly the fluctuation a pulse consists of — left on,
    // it erases the signal as fast as the finger produces it. Auto white
    // balance does the same thing per colour channel.
    //
    // Locking is deliberately delayed rather than applied at bind time: the
    // torch has just switched on and the sensor needs a moment to settle,
    // and locking immediately would freeze a wildly wrong exposure from
    // before the light arrived. Samples gathered during that settling are
    // discarded, since they contain the auto-exposure ramp rather than a
    // heartbeat.
    LaunchedEffect(boundCamera) {
        val camera = boundCamera ?: return@LaunchedEffect
        delay(EXPOSURE_SETTLE_MILLIS)
        lockExposure(camera)
        onSettled()
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
                    // Centred, and that isn't only cosmetic: the card's blob
                    // shape curves inward at its corners, and left-aligned
                    // headline text ran straight into that curve and lost its
                    // first characters. The middle of the blob is its widest
                    // point, so text centred there always fits.
                    Text(
                        text = when {
                            !uiState.measuring && uiState.bpm == null -> stringResource(R.string.heart_rate_idle)
                            uiState.settling -> stringResource(R.string.heart_rate_settling)
                            uiState.measuring && !uiState.fingerDetected -> stringResource(R.string.heart_rate_no_finger)
                            uiState.bpm != null -> stringResource(R.string.heart_rate_bpm, uiState.bpm)
                            else -> stringResource(R.string.heart_rate_searching)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.heart_rate_confidence, (uiState.confidence * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = if (uiState.trustworthy) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
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

            // Sharing is a separate switch from measuring on purpose: a
            // reading taken to check the app works is not the same as
            // handing someone your pulse.
            if (uiState.measuring) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.heart_rate_share_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            stringResource(R.string.heart_rate_share_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = uiState.sharing, onCheckedChange = onSetSharing)
                }
            }

            if (uiState.partnerLive) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = BlobShapeSoftC,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = uiState.partnerBpm?.let {
                                stringResource(R.string.heart_rate_partner_live_bpm, it)
                            } ?: stringResource(R.string.heart_rate_partner_connecting),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            stringResource(R.string.heart_rate_partner_live_hint),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }
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
                        uiState.meanLevel.toInt(),
                        uiState.spread.toInt(),
                        uiState.beatsFelt
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Wrapped because Camera2 interop is best-effort: some devices reject these
// keys outright, and a phone that won't lock its exposure should still get a
// degraded reading rather than no camera at all.
@OptIn(ExperimentalCamera2Interop::class)
private fun lockExposure(camera: Camera) {
    runCatching {
        Camera2CameraControl.from(camera.cameraControl).setCaptureRequestOptions(
            CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
                .build()
        )
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
