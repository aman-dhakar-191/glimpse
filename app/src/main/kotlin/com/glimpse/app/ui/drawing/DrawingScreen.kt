package com.glimpse.app.ui.drawing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.glimpse.app.R
import com.glimpse.app.data.model.LiveStroke

// A tapped dot's radius, relative to the current pen width — matches
// DrawingRasterizer's DOT_RADIUS_TO_WIDTH_RATIO so a lone tap looks the
// same live as it does in the sent image.
private const val DOT_RADIUS_TO_WIDTH_RATIO = 0.6f

// Never below 1 — there's no reason to shrink the canvas smaller than its
// own box, only to zoom in on it for detail work.
private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(
    uiState: DrawingUiState,
    onStart: () -> Unit,
    onSetColor: (String) -> Unit,
    onSetWidth: (Float) -> Unit,
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

    // Purely a local view transform — never synced. Zooming in to work on
    // detail is a per-device choice; it doesn't change the underlying
    // normalized stroke coordinates, so your partner's view (and their own
    // zoom level) is completely unaffected by yours.
    var scale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

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
        // The canvas fills essentially the whole remaining screen (just a
        // small margin) instead of sharing space with dedicated toolbar
        // rows — every control lives as a floating overlay on top of it
        // instead, so drawing area is maximized.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        // A single manual gesture loop (not detectDragGestures
                        // or detectTransformGestures alone) so one finger can
                        // draw while a second finger joining mid-gesture
                        // switches to pinch-zoom/pan instead — Compose has no
                        // built-in combinator for "route by pointer count."
                        // Touch positions here are RAW/untransformed (the
                        // pointerInput modifier lives on this outer Box,
                        // which never itself gets scaled — only the Canvas
                        // content below it does, via graphicsLayer), so
                        // toContent() below inverts scale+panOffset by hand
                        // before normalizing to the same 0f..1f stroke space
                        // every device shares regardless of its own zoom.
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var transforming = false
                            var drawing = true

                            val start = toContent(down.position, canvasSize, scale, panOffset)
                            onStrokeStart(start.first, start.second)

                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }

                                if (pressed.size >= 2) {
                                    if (drawing) {
                                        // A second finger joined mid-stroke —
                                        // abandon the partial draw; a pinch
                                        // means the intent was never to draw.
                                        onStrokeEnd()
                                        drawing = false
                                    }
                                    transforming = true
                                }

                                if (transforming) {
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    scale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
                                    panOffset += panChange
                                    event.changes.forEach { it.consume() }
                                    if (pressed.isEmpty()) break
                                    continue
                                }

                                if (pressed.isEmpty()) break

                                val change = pressed.first()
                                val point = toContent(change.position, canvasSize, scale, panOffset)
                                onStrokeMove(point.first, point.second)
                                change.consume()
                            }

                            if (drawing) onStrokeEnd()
                        }
                    }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = panOffset.x,
                            translationY = panOffset.y,
                            // Anchor scaling at the top-left corner instead
                            // of graphicsLayer's own default (center) — the
                            // inverse transform in toContent() assumes
                            // screenPos = contentPos * scale + panOffset,
                            // which only holds true if scaling doesn't also
                            // shift the origin out from under it.
                            transformOrigin = TransformOrigin(0f, 0f)
                        )
                ) {
                    uiState.strokes.values.forEach { stroke -> drawLiveStroke(stroke, size) }
                }
            }

            // Top overlay: color + pen size. Semi-transparent so the canvas
            // stays visible/legible underneath rather than a hard-edged bar
            // permanently claiming screen space.
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .fillMaxWidth(0.95f),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DrawingColors.PALETTE.forEach { hex ->
                            val isSelected = hex == uiState.selectedColor
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 32.dp else 26.dp)
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
                        Spacer(Modifier.weight(1f))
                        // Live preview of the current pen — color at
                        // (roughly) its actual relative size.
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size((10 + uiState.selectedWidth * 300).dp)
                                    .clip(CircleShape)
                                    .background(parseColorOrBlack(uiState.selectedColor))
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    HueSlider(
                        selectedColor = uiState.selectedColor,
                        onHueSelected = { hue -> onSetColor(DrawingColors.hueToHex(hue)) }
                    )

                    Spacer(Modifier.height(8.dp))

                    PenSizeSlider(width = uiState.selectedWidth, onWidthChange = onSetWidth)
                }
            }

            // Bottom overlay: Undo / Clear / Send.
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 4.dp
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(10.dp)
                ) {
                    OutlinedButton(onClick = onUndo) {
                        Text(stringResource(R.string.drawing_undo))
                    }
                    OutlinedButton(onClick = { showClearConfirm = true }) {
                        Text(stringResource(R.string.drawing_clear))
                    }
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

// A horizontal rainbow gradient bar — drag anywhere on it to pick a hue.
// Fixed saturation/value (see DrawingColors.hueToHex) so this is a single
// one-dimensional control rather than a full color square, deliberately
// traded for simplicity over covering every possible shade.
@Composable
private fun HueSlider(selectedColor: String, onHueSelected: (Float) -> Unit) {
    val hueGradientColors = remember {
        (0..360 step 30).map { hue -> Color(android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.85f, 0.85f))) }
    }
    var barWidth by remember { mutableStateOf(0) }
    val currentHue = remember(selectedColor) { hexToHue(selectedColor) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(hueGradientColors))
            .onSizeChanged { barWidth = it.width }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (barWidth > 0) onHueSelected((down.position.x / barWidth).coerceIn(0f, 1f) * 360f)
                    drag(down.id) { change ->
                        if (barWidth > 0) onHueSelected((change.position.x / barWidth).coerceIn(0f, 1f) * 360f)
                        change.consume()
                    }
                }
            }
    ) {
        // A small marker showing where the CURRENTLY selected color's hue
        // falls on the bar — otherwise there'd be no feedback on the slider
        // itself after picking a preset swatch or dragging elsewhere.
        if (currentHue != null && barWidth > 0) {
            val markerX = (currentHue / 360f) * barWidth
            Box(
                modifier = Modifier
                    .offset { IntOffset(markerX.toInt() - 3.dp.roundToPx(), 0) }
                    .fillMaxHeight()
                    .width(6.dp)
                    .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(3.dp))
                    .border(1.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
            )
        }
    }
}

// Null for colors a pure hue slider can't represent (near-black/white/gray
// presets, where saturation is ~0) — no meaningful marker position for
// those rather than a misleading one at hue 0.
private fun hexToHue(hex: String): Float? {
    val colorInt = try {
        android.graphics.Color.parseColor(hex)
    } catch (e: IllegalArgumentException) {
        return null
    }
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(colorInt, hsv)
    return if (hsv[1] < 0.15f) null else hsv[0]
}

@Composable
private fun PenSizeSlider(width: Float, onWidthChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.drawing_pen_size), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(8.dp))
        Slider(
            value = width,
            onValueChange = onWidthChange,
            valueRange = DrawingColors.MIN_WIDTH_FRACTION..DrawingColors.MAX_WIDTH_FRACTION,
            modifier = Modifier.weight(1f)
        )
    }
}

// Touch positions arrive in the outer Box's raw, untransformed coordinate
// space (see the pointerInput comment above) — this inverts the SAME
// scale+translate the Canvas's own graphicsLayer applies for rendering
// (contentPos = (screenPos - panOffset) / scale) before normalizing to the
// 0f..1f stroke space every device shares regardless of its own zoom.
private fun toContent(rawPosition: Offset, canvasSize: IntSize, scale: Float, panOffset: Offset): Pair<Float, Float> {
    if (canvasSize.width == 0 || canvasSize.height == 0 || scale == 0f) return 0f to 0f
    val contentX = (rawPosition.x - panOffset.x) / scale
    val contentY = (rawPosition.y - panOffset.y) / scale
    return (contentX / canvasSize.width).coerceIn(0f, 1f) to (contentY / canvasSize.height).coerceIn(0f, 1f)
}

private fun DrawScope.drawLiveStroke(stroke: LiveStroke, canvasSize: Size) {
    val points = stroke.points
    if (points.size < 2) return
    val color = parseColorOrBlack(stroke.color)
    val strokeWidthPx = canvasSize.minDimension * stroke.width.toFloat()

    if (points.size == 2) {
        drawCircle(
            color = color,
            radius = strokeWidthPx * DOT_RADIUS_TO_WIDTH_RATIO,
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
        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun parseColorOrBlack(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: IllegalArgumentException) {
    Color.Black
}
