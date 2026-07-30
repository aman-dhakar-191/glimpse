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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
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
private const val ZOOM_STEP = 0.5f

// Raw screen-space pixels a single finger can drift before a Select-mode
// touch stops counting as "just a tap" and starts counting as a drag —
// independent of zoom, since it's about finger wobble, not content space.
private const val TAP_SLOP_PX = 12f

// Selection highlight halo, drawn under the stroke itself.
private val SELECTION_HALO_COLOR = Color(0xFF2196F3).copy(alpha = 0.35f)
private const val SELECTION_HALO_FRACTION = 0.01f

// Local-only (non-synced) choice of HOW a Select-mode drag over empty
// selection carves out a multi-select region — same "per-device UI
// preference" reasoning as zoom/pan below.
private enum class RegionSelectMode { Rectangle, Lasso }

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
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onSend: () -> Unit,
    onSendStateHandled: () -> Unit,
    onSetMode: (DrawingMode) -> Unit,
    onSetBrush: (String) -> Unit,
    onSelectTap: (Float, Float) -> Unit,
    onSelectRegion: (List<Pair<Float, Float>>) -> Unit,
    onBeginMove: () -> Unit,
    onMoveSelectionBy: (Float, Float) -> Unit,
    onEndMove: () -> Unit,
    onMarkPresent: () -> Unit,
    onClearPresent: () -> Unit,
    onDeleteSelected: () -> Unit,
    onFillAt: (Float, Float) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) { onStart() }

    // Tied to this composable actually being on screen (not the ViewModel's
    // Activity-long lifetime like onStart() above) — presence needs to flip
    // off the moment you navigate away, not just when the Activity dies.
    DisposableEffect(Unit) {
        onMarkPresent()
        onDispose { onClearPresent() }
    }

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

    // Also local-only — which shape a Select-mode drag over empty selection
    // draws, and its live raw-screen-space preview while the drag is
    // in-flight (null when no drag is happening).
    var regionSelectMode by remember { mutableStateOf(RegionSelectMode.Rectangle) }
    var regionPreview by remember { mutableStateOf<List<Offset>?>(null) }

    // Whether the single controls card (color/pen/brush plus mode/undo/
    // redo/clear/send) is showing its contents or collapsed to just its
    // handle — collapsing hands the whole screen back to the canvas.
    var controlsExpanded by remember { mutableStateOf(true) }

    // The gesture-handling coroutine below (pointerInput(Unit)) is launched
    // once and never restarted — a plain captured `uiState` reference would
    // freeze at whatever it was on that first launch. rememberUpdatedState
    // gives it a handle that always reflects the latest value instead.
    val currentUiState by rememberUpdatedState(uiState)

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
                title = {
                    Column {
                        Text(stringResource(R.string.drawing_title), style = MaterialTheme.typography.titleMedium)
                        // Always shows SOMETHING (never blank) — live presence
                        // takes priority while your partner is actually here,
                        // otherwise this is the only place "when did either of
                        // you last open this" is visible at all, same idea as
                        // a chat app's "last seen" line under a contact's name.
                        Text(
                            text = when {
                                uiState.partnerPresent -> stringResource(R.string.drawing_partner_present)
                                uiState.lastActiveAt > 0L -> stringResource(
                                    if (uiState.lastActiveByMe) R.string.drawing_last_active_you else R.string.drawing_last_active_partner,
                                    relativeTimeText(uiState.lastActiveAt)
                                )
                                else -> stringResource(R.string.drawing_no_activity_yet)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.partnerPresent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                // Undo / Redo / Clear / Send live up here rather than in the
                // controls card below, so they stay reachable even with the
                // card collapsed — they're the actions you reach for while
                // drawing, not settings you adjust and leave alone.
                actions = {
                    IconButton(onClick = onUndo) {
                        Text("↩️", style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = onRedo) {
                        Text("↪️", style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(onClick = { showClearConfirm = true }) {
                        Text("🗑️", style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton(
                        onClick = onSend,
                        enabled = uiState.strokes.isNotEmpty() && uiState.sendState !is DrawingSendState.Sending
                    ) {
                        if (uiState.sendState is DrawingSendState.Sending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                "➤",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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
                            val downContent = toContent(down.position, canvasSize, scale, panOffset)

                            when (currentUiState.mode) {
                            DrawingMode.Select -> {
                                var transforming = false
                                var moving = false
                                var regionSelecting = false
                                var totalRawMovement = 0f
                                var lastContent = downContent
                                val lassoRaw = mutableListOf(down.position)
                                // Fixed at gesture-start — a drag that begins
                                // over an empty selection carves out a
                                // marquee/lasso region instead of moving.
                                val canMove = currentUiState.selectedStrokeIds.isNotEmpty()

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }

                                    if (pressed.size >= 2) {
                                        if (moving) {
                                            onEndMove()
                                            moving = false
                                        }
                                        if (regionSelecting) {
                                            regionSelecting = false
                                            regionPreview = null
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
                                    totalRawMovement += (change.position - change.previousPosition).getDistance()
                                    val point = toContent(change.position, canvasSize, scale, panOffset)

                                    if (canMove && totalRawMovement > TAP_SLOP_PX) {
                                        if (!moving) {
                                            onBeginMove()
                                            moving = true
                                        }
                                        onMoveSelectionBy(point.first - lastContent.first, point.second - lastContent.second)
                                    } else if (!canMove && totalRawMovement > TAP_SLOP_PX) {
                                        regionSelecting = true
                                        if (regionSelectMode == RegionSelectMode.Lasso) lassoRaw.add(change.position)
                                        regionPreview = if (regionSelectMode == RegionSelectMode.Rectangle) {
                                            listOf(
                                                down.position,
                                                Offset(change.position.x, down.position.y),
                                                change.position,
                                                Offset(down.position.x, change.position.y)
                                            )
                                        } else {
                                            lassoRaw.toList()
                                        }
                                    }
                                    lastContent = point
                                    change.consume()
                                }

                                if (moving) {
                                    onEndMove()
                                } else if (regionSelecting) {
                                    val polygon = regionPreview.orEmpty().map { toContent(it, canvasSize, scale, panOffset) }
                                    onSelectRegion(polygon)
                                    regionPreview = null
                                } else if (totalRawMovement <= TAP_SLOP_PX) {
                                    // A plain tap: toggle whatever's under it,
                                    // or clear the selection if nothing is.
                                    onSelectTap(downContent.first, downContent.second)
                                }
                            }
                            DrawingMode.Fill -> {
                                var transforming = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.size >= 2) transforming = true
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
                                    pressed.first().consume()
                                }
                                if (!transforming) onFillAt(downContent.first, downContent.second)
                            }
                            DrawingMode.Draw -> {
                                var transforming = false
                                var drawing = true
                                onStrokeStart(downContent.first, downContent.second)

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
                    // Flood-fill regions first, so they sit UNDER the strokes
                    // bounding them — matches DrawingRasterizer's own order,
                    // so the live canvas and the sent PNG agree.
                    uiState.strokes.entries
                        .sortedBy { if (it.value.fillRects.isEmpty()) 1 else 0 }
                        .forEach { (id, stroke) ->
                            drawLiveStroke(stroke, size, isSelected = id in uiState.selectedStrokeIds)
                        }
                }

                // Live marquee/lasso preview, drawn in the SAME raw screen
                // space the gesture loop collects it in — a separate,
                // untransformed Canvas layered on top (not inside the one
                // above, which is scaled by graphicsLayer) so the region
                // outline always matches exactly where your finger is.
                regionPreview?.let { points ->
                    if (points.size >= 2) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val path = Path().apply {
                                moveTo(points[0].x, points[0].y)
                                points.drop(1).forEach { lineTo(it.x, it.y) }
                                close()
                            }
                            drawPath(
                                path = path,
                                color = SELECTION_HALO_COLOR.copy(alpha = 0.9f),
                                style = Stroke(
                                    width = 3f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
                                )
                            )
                        }
                    }
                }
            }

            // Floating zoom controls — dedicated in/out buttons alongside
            // the existing pinch-to-zoom gesture, for when a second finger
            // isn't convenient.
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZoomButton(label = "+") { scale = (scale + ZOOM_STEP).coerceIn(MIN_SCALE, MAX_SCALE) }
                ZoomButton(label = "−") {
                    scale = (scale - ZOOM_STEP).coerceIn(MIN_SCALE, MAX_SCALE)
                    if (scale <= MIN_SCALE) panOffset = Offset.Zero
                }
            }

            // Contextual delete — floats right above whichever selected
            // stroke is currently topmost, instead of a permanent toolbar
            // button that's disabled most of the time.
            if (uiState.mode == DrawingMode.Select && uiState.selectedStrokeIds.isNotEmpty() && canvasSize.width > 0) {
                // A flood-fill region carries rectangles instead of path
                // points, so read whichever the stroke actually has — and
                // skip anything with neither rather than indexing into it.
                val anchor = uiState.strokes
                    .filterKeys { it in uiState.selectedStrokeIds }
                    .values
                    .mapNotNull { stroke ->
                        val coords = if (stroke.fillRects.isNotEmpty()) stroke.fillRects else stroke.points
                        if (coords.size < 2) null else coords[0].toFloat() to coords[1].toFloat()
                    }
                    .minByOrNull { it.second }
                if (anchor != null) {
                    val anchorX = (anchor.first * canvasSize.width * scale + panOffset.x).toInt()
                    val anchorY = (anchor.second * canvasSize.height * scale + panOffset.y).toInt()
                    Surface(
                        modifier = Modifier.offset { IntOffset(anchorX - 20.dp.roundToPx(), (anchorY - 52.dp.roundToPx()).coerceAtLeast(0)) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer,
                        shadowElevation = 6.dp
                    ) {
                        IconButton(onClick = onDeleteSelected) {
                            Text("✕", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            // A single collapsible controls card — color/hue/pen size/brush
            // plus mode/undo/redo/clear/send — instead of two permanently
            // visible bars. Collapsing it to just its handle hands the whole
            // screen back to the canvas.
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .fillMaxWidth(0.95f),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 4.dp
            ) {
                Column {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { controlsExpanded = !controlsExpanded }
                            .padding(vertical = 6.dp)
                    ) {
                        Text(
                            if (controlsExpanded) "▾" else "▴",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (controlsExpanded) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
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

                            Spacer(Modifier.height(8.dp))

                            BrushPicker(selectedBrush = uiState.selectedBrush, onSelectBrush = onSetBrush)

                            Spacer(Modifier.height(8.dp))
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            // Scrolls rather than silently clipping if the
                            // button count ever outgrows the screen width.
                            modifier = Modifier
                                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            // All three modes shown at once (not one icon that
                            // swaps) so which one is active is always visible,
                            // not hidden behind a single toggle button.
                            ModeButton(
                                label = "✏️",
                                selected = uiState.mode == DrawingMode.Draw,
                                onClick = { onSetMode(DrawingMode.Draw) }
                            )
                            ModeButton(
                                label = "👆",
                                selected = uiState.mode == DrawingMode.Select,
                                onClick = { onSetMode(DrawingMode.Select) }
                            )
                            ModeButton(
                                label = "🪣",
                                selected = uiState.mode == DrawingMode.Fill,
                                onClick = { onSetMode(DrawingMode.Fill) }
                            )
                            // Only meaningful in Select mode — picks whether an
                            // empty-selection drag draws a rectangle marquee or
                            // a freehand lasso (see RegionSelectMode/regionPreview).
                            if (uiState.mode == DrawingMode.Select) {
                                OutlinedButton(
                                    onClick = {
                                        regionSelectMode = if (regionSelectMode == RegionSelectMode.Rectangle) {
                                            RegionSelectMode.Lasso
                                        } else {
                                            RegionSelectMode.Rectangle
                                        }
                                    }
                                ) {
                                    Text(
                                        if (regionSelectMode == RegionSelectMode.Rectangle) {
                                            stringResource(R.string.drawing_region_rectangle)
                                        } else {
                                            stringResource(R.string.drawing_region_lasso)
                                        }
                                    )
                                }
                            }
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

// A coarse "5m ago"/"2h ago" rendering of when the last_active timestamp
// was written — recomputed on whatever recomposition happens to catch it,
// not a live ticking clock (the presence line above it already covers the
// truly real-time case, so this only needs to be roughly right).
@Composable
private fun relativeTimeText(epochMillis: Long): String {
    val diffMs = (System.currentTimeMillis() - epochMillis).coerceAtLeast(0)
    val minutes = diffMs / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> stringResource(R.string.drawing_time_just_now)
        minutes < 60 -> stringResource(R.string.drawing_time_minutes_ago, minutes.toInt())
        hours < 24 -> stringResource(R.string.drawing_time_hours_ago, hours.toInt())
        else -> stringResource(R.string.drawing_time_days_ago, days.toInt())
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

// A filled Button when active, outlined otherwise — shown alongside its
// siblings (not swapped in/out of view) so which mode is active is always
// visible at a glance.
@Composable
private fun ModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun ZoomButton(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(40.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun BrushPicker(selectedBrush: String, onSelectBrush: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        DrawingColors.BrushTypes.ALL.forEach { brush ->
            val labelRes = when (brush) {
                DrawingColors.BrushTypes.SQUARE -> R.string.drawing_brush_square
                DrawingColors.BrushTypes.MARKER -> R.string.drawing_brush_marker
                DrawingColors.BrushTypes.DASHED -> R.string.drawing_brush_dashed
                else -> R.string.drawing_brush_round
            }
            val selected = brush == selectedBrush
            Surface(
                modifier = Modifier.clickable { onSelectBrush(brush) },
                shape = RoundedCornerShape(10.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
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
// Deliberately NOT coerced into 0f..1f — while zoomed in, a drag that goes
// past the visible edge of the content is still a real position just
// off-canvas; clamping it would pin every following point to exactly x=0/1
// (or y=0/1), drawing a dead-straight "wall" along that edge instead of
// just letting the stroke run past it, where the Box's own clip already
// hides whatever falls outside the canvas.
private fun toContent(rawPosition: Offset, canvasSize: IntSize, scale: Float, panOffset: Offset): Pair<Float, Float> {
    if (canvasSize.width == 0 || canvasSize.height == 0 || scale == 0f) return 0f to 0f
    val contentX = (rawPosition.x - panOffset.x) / scale
    val contentY = (rawPosition.y - panOffset.y) / scale
    return (contentX / canvasSize.width) to (contentY / canvasSize.height)
}

private fun DrawScope.drawLiveStroke(stroke: LiveStroke, canvasSize: Size, isSelected: Boolean) {
    val color = parseColorOrBlack(stroke.color)
    val strokeWidthPx = canvasSize.minDimension * stroke.width.toFloat()
    val haloWidthPx = canvasSize.minDimension * SELECTION_HALO_FRACTION

    // A flood-filled region — solid rectangles covering the area the bucket
    // spread across, rather than a path to stroke. See DrawingFloodFill.
    if (stroke.fillRects.isNotEmpty()) {
        var i = 0
        while (i + 3 < stroke.fillRects.size) {
            val left = (stroke.fillRects[i] * canvasSize.width).toFloat()
            val top = (stroke.fillRects[i + 1] * canvasSize.height).toFloat()
            val right = (stroke.fillRects[i + 2] * canvasSize.width).toFloat()
            val bottom = (stroke.fillRects[i + 3] * canvasSize.height).toFloat()
            drawRect(
                color = if (isSelected) SELECTION_HALO_COLOR.compositeOver(color) else color,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top)
            )
            i += 4
        }
        return
    }

    val points = stroke.points
    if (points.size < 2) return

    if (points.size == 2) {
        val center = Offset((points[0] * canvasSize.width).toFloat(), (points[1] * canvasSize.height).toFloat())
        val radius = strokeWidthPx * DOT_RADIUS_TO_WIDTH_RATIO
        if (isSelected) {
            drawCircle(color = SELECTION_HALO_COLOR, radius = radius + haloWidthPx, center = center)
        }
        drawCircle(color = color, radius = radius, center = center)
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

    // Fill mode auto-closes the path (last point back to the first)
    // regardless of whether the original stroke naturally looped, then
    // fills it solid — see LiveStroke.isFilled/DrawingViewModel.fillStrokeAt.
    if (stroke.isFilled) {
        val fillPath = Path().apply { addPath(path) }.apply { close() }
        if (isSelected) {
            drawPath(path = fillPath, color = SELECTION_HALO_COLOR, style = Stroke(width = haloWidthPx * 2))
        }
        drawPath(path = fillPath, color = color)
        return
    }

    val brushStyle = when (stroke.brushType) {
        DrawingColors.BrushTypes.SQUARE -> Stroke(width = strokeWidthPx, cap = StrokeCap.Square, join = StrokeJoin.Miter)
        DrawingColors.BrushTypes.MARKER -> Stroke(width = strokeWidthPx * 1.7f, cap = StrokeCap.Square, join = StrokeJoin.Round)
        DrawingColors.BrushTypes.DASHED -> Stroke(
            width = strokeWidthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(strokeWidthPx * 2.2f, strokeWidthPx * 1.6f))
        )
        else -> Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    // A marker reads as a translucent highlighter stroke rather than an
    // opaque pen line — the width bump above already gives it the broader
    // marker "footprint."
    val brushColor = if (stroke.brushType == DrawingColors.BrushTypes.MARKER) color.copy(alpha = 0.55f) else color

    if (isSelected) {
        drawPath(
            path = path,
            color = SELECTION_HALO_COLOR,
            style = Stroke(width = brushStyle.width + haloWidthPx * 2, cap = brushStyle.cap, join = brushStyle.join)
        )
    }
    drawPath(path = path, color = brushColor, style = brushStyle)
}

private fun parseColorOrBlack(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: IllegalArgumentException) {
    Color.Black
}
