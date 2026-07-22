package com.glimpse.app.ui.drawing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.glimpse.app.R
import com.glimpse.app.data.model.LiveStroke

private const val STROKE_WIDTH_FRACTION = 0.012f
private const val DOT_RADIUS_FRACTION = 0.008f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(
    uiState: DrawingUiState,
    onStart: () -> Unit,
    onSetColor: (String) -> Unit,
    onStrokeStart: (Float, Float) -> Unit,
    onStrokeMove: (Float, Float) -> Unit,
    onStrokeEnd: () -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onSend: () -> Unit,
    onSendStateHandled: () -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) { onStart() }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val sentMessage = stringResource(R.string.drawing_sent)

    LaunchedEffect(uiState.sendState) {
        when (val state = uiState.sendState) {
            is DrawingSendState.Sent -> {
                snackbarHostState.showSnackbar(sentMessage)
                onSendStateHandled()
            }
            is DrawingSendState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                onSendStateHandled()
            }
            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drawing_title), style = MaterialTheme.typography.titleMedium) },
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
                .padding(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DrawingColors.PALETTE.forEach { hex ->
                    val isSelected = hex == uiState.selectedColor
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 40.dp else 32.dp)
                            .clip(CircleShape)
                            .background(parseColorOrBlack(hex))
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape
                            )
                            .clickable { onSetColor(hex) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Square, so the normalized 0f..1f point coordinates mean the
            // same thing regardless of either device's actual screen size
            // — no separate x/y aspect ratio to track when converting.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        // Low-level awaitFirstDown/drag (not
                        // detectDragGestures) specifically so a plain tap
                        // with no movement still registers as a stroke —
                        // detectDragGestures only fires once movement
                        // crosses the touch-slop threshold, which would
                        // silently drop single-dot taps.
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val start = normalize(down.position, canvasSize)
                            onStrokeStart(start.first, start.second)
                            drag(down.id) { change ->
                                val point = normalize(change.position, canvasSize)
                                onStrokeMove(point.first, point.second)
                                change.consume()
                            }
                            onStrokeEnd()
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    uiState.strokes.values.forEach { stroke -> drawLiveStroke(stroke, size) }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(onClick = onUndo) {
                    Text(stringResource(R.string.drawing_undo))
                }
                OutlinedButton(onClick = { showClearConfirm = true }) {
                    Text(stringResource(R.string.drawing_clear))
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onSend,
                    enabled = uiState.strokes.isNotEmpty() && uiState.sendState !is DrawingSendState.Sending
                ) {
                    if (uiState.sendState is DrawingSendState.Sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.drawing_send))
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.drawing_clear_confirm_title)) },
            text = { Text(stringResource(R.string.drawing_clear_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    showClearConfirm = false
                }) {
                    Text(stringResource(R.string.drawing_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.guide_dismiss))
                }
            }
        )
    }
}

private fun normalize(offset: Offset, canvasSize: IntSize): Pair<Float, Float> {
    if (canvasSize.width == 0 || canvasSize.height == 0) return 0f to 0f
    return (offset.x / canvasSize.width).coerceIn(0f, 1f) to (offset.y / canvasSize.height).coerceIn(0f, 1f)
}

private fun DrawScope.drawLiveStroke(stroke: LiveStroke, canvasSize: Size) {
    val points = stroke.points
    if (points.size < 2) return
    val color = parseColorOrBlack(stroke.color)

    if (points.size == 2) {
        drawCircle(
            color = color,
            radius = canvasSize.minDimension * DOT_RADIUS_FRACTION,
            center = Offset((points[0] * canvasSize.width).toFloat(), (points[1] * canvasSize.height).toFloat())
        )
        return
    }

    val path = Path().apply {
        moveTo((points[0] * canvasSize.width).toFloat(), (points[1] * canvasSize.height).toFloat())
        var i = 2
        while (i + 1 < points.size) {
            lineTo((points[i] * canvasSize.width).toFloat(), (points[i + 1] * canvasSize.height).toFloat())
            i += 2
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = canvasSize.minDimension * STROKE_WIDTH_FRACTION, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun parseColorOrBlack(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: IllegalArgumentException) {
    Color.Black
}
