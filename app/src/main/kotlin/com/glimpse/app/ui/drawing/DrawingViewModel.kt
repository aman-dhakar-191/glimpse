package com.glimpse.app.ui.drawing

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glimpse.app.data.firebase.FirebaseSync
import com.glimpse.app.data.model.LiveStroke
import com.glimpse.app.service.PhotoSendResults
import com.glimpse.app.service.PhotoSendService
import com.glimpse.app.util.CrashLogger
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface DrawingSendState {
    data object Idle : DrawingSendState
    data object Sending : DrawingSendState
    data object Sent : DrawingSendState
    data class Error(val message: String) : DrawingSendState
}

enum class DrawingMode { Draw, Select }

data class DrawingUiState(
    val myUid: String = "",
    // Keyed by stroke id — both of you can add to this map at once, each
    // only ever touching your own key (see FirebaseSync.updateLiveStroke),
    // so this is always the merged, up-to-date joint canvas.
    val strokes: Map<String, LiveStroke> = emptyMap(),
    val selectedColor: String = DrawingColors.DEFAULT,
    val selectedWidth: Float = DrawingColors.DEFAULT_WIDTH_FRACTION,
    val sendState: DrawingSendState = DrawingSendState.Idle,
    val mode: DrawingMode = DrawingMode.Draw,
    // Which strokes are currently selected in Select mode — anyone's, not
    // just your own; it's a joint canvas, so rearranging anything on it is
    // fair game the same way drawing on it already is.
    val selectedStrokeIds: Set<String> = emptySet(),
    // Is your PARTNER (not you) currently on the Draw screen too — see
    // FirebaseSync.listenToDrawingPresence.
    val partnerPresent: Boolean = false,
    // Who most recently opened the Draw screen, and when — 0 means neither
    // of you has yet (a brand new pairing). See FirebaseSync.listenToDrawingLastActive.
    val lastActiveAt: Long = 0L,
    val lastActiveByMe: Boolean = false
)

// Backs the shared, joint drawing canvas (see FirebaseSync's
// shared/live_drawing functions) — every stroke either of you draws while
// this screen is open streams live to the other's screen if they have it
// open too. Listener attaches once via start() (called from a
// LaunchedEffect(Unit) the first time the screen is shown) and is only
// torn down in onCleared(), same convention MessageHistoryViewModel already
// uses — this ViewModel lives for the whole Activity, not just while the
// screen is visible.
class DrawingViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(DrawingUiState(myUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()))
    val uiState: StateFlow<DrawingUiState> = _uiState.asStateFlow()

    private var listener: ValueEventListener? = null
    private var presenceListener: ValueEventListener? = null
    private var lastActiveListener: ValueEventListener? = null

    // My own in-progress stroke — tracked locally (not derived from
    // uiState.strokes) so pointer-move handling never needs a Firebase
    // round trip to know what "my current/last stroke" is.
    private var currentStrokeId: String? = null
    private var currentStrokePoints = mutableListOf<Double>()

    // A real stack (not just "the last one") — Undo needs to be able to
    // step back through everything YOU drew this session, not just undo
    // once and then have nothing left to undo even though older strokes
    // of yours are still on the canvas.
    private val myStrokeStack = mutableListOf<String>()

    // Every point gets appended locally for instant local feedback, but
    // only every Nth one (plus stroke start/end) is actually pushed to
    // Firebase — pushing on every single pointer-move callback (up to
    // ~60/sec) would be needlessly chatty for what's only ever a live
    // preview, not data with a delivery guarantee.
    private var pointsSinceLastPush = 0

    // Snapshot of the selected strokes' ORIGINAL points, taken once when a
    // move gesture starts — every subsequent moveSelectionBy() call offsets
    // from this fixed snapshot by the gesture's total accumulated delta,
    // rather than compounding onto whatever's currently in uiState (which
    // may lag behind due to push throttling below).
    private var moveOriginalStrokes: Map<String, LiveStroke> = emptyMap()
    private var moveCumulativeDx = 0f
    private var moveCumulativeDy = 0f
    private var moveEventsSinceLastPush = 0

    init {
        viewModelScope.launch {
            PhotoSendResults.drawingResults.collect { result ->
                _uiState.value = _uiState.value.copy(
                    sendState = result.fold(
                        onSuccess = { DrawingSendState.Sent },
                        onFailure = { throwable -> DrawingSendState.Error(throwable.message ?: "Couldn't send drawing.") }
                    )
                )
            }
        }
    }

    fun start() {
        if (listener != null) return
        listener = FirebaseSync.listenToLiveDrawing { strokes ->
            _uiState.value = _uiState.value.copy(strokes = strokes)
        }
        presenceListener = FirebaseSync.listenToDrawingPresence { presentUids ->
            val partnerPresent = presentUids.any { it != _uiState.value.myUid }
            _uiState.value = _uiState.value.copy(partnerPresent = partnerPresent)
        }
        lastActiveListener = FirebaseSync.listenToDrawingLastActive { uid, at ->
            _uiState.value = _uiState.value.copy(lastActiveAt = at, lastActiveByMe = uid == _uiState.value.myUid)
        }
    }

    // Called from DrawingScreen's DisposableEffect — tied to the SCREEN
    // actually being on-screen, unlike start()'s listeners above which, once
    // attached, just keep running for the ViewModel's whole (Activity-long)
    // lifetime. Presence needs to flip off the moment you navigate away,
    // not just when the Activity itself is destroyed.
    fun markPresent() {
        FirebaseSync.markDrawingPresence()
    }

    fun clearPresent() {
        FirebaseSync.clearDrawingPresence()
    }

    fun setColor(color: String) {
        _uiState.value = _uiState.value.copy(selectedColor = color)
    }

    fun setWidth(width: Float) {
        _uiState.value = _uiState.value.copy(selectedWidth = width)
    }

    // Selection is scoped to whichever mode is active, so switching modes
    // always starts from a clean slate rather than carrying stale picks
    // (e.g. back into Select) or leaving a phantom selection highlighted
    // while you're back to drawing.
    fun setMode(mode: DrawingMode) {
        moveOriginalStrokes = emptyMap()
        _uiState.value = _uiState.value.copy(mode = mode, selectedStrokeIds = emptySet())
    }

    // A plain tap in Select mode: toggles the tapped stroke in/out of the
    // selection, or clears the whole selection if the tap didn't land on
    // any stroke. Anyone's strokes are fair game — see selectedStrokeIds.
    fun selectTapAt(x: Float, y: Float) {
        val hitId = hitTestStroke(x, y)
        val current = _uiState.value.selectedStrokeIds
        val updated = when {
            hitId == null -> emptySet()
            hitId in current -> current - hitId
            else -> current + hitId
        }
        _uiState.value = _uiState.value.copy(selectedStrokeIds = updated)
    }

    // Deletes every currently selected stroke — anyone's, same "fair game"
    // reasoning as selectTapAt below. removeAll from myStrokeStack too, so
    // Undo can't later try to remove an id that's already gone.
    fun deleteSelected() {
        val selected = _uiState.value.selectedStrokeIds
        if (selected.isEmpty()) return
        selected.forEach { FirebaseSync.removeLiveStroke(it) }
        myStrokeStack.removeAll(selected)
        _uiState.value = _uiState.value.copy(selectedStrokeIds = emptySet())
    }

    // Called once when a drag starts on top of a non-empty selection.
    fun beginMove() {
        val selected = _uiState.value.selectedStrokeIds
        if (selected.isEmpty()) return
        moveOriginalStrokes = _uiState.value.strokes.filterKeys { it in selected }
        moveCumulativeDx = 0f
        moveCumulativeDy = 0f
        moveEventsSinceLastPush = 0
    }

    // dxNorm/dyNorm are this drag step's delta in the same normalized
    // 0f..1f stroke space as everything else — throttled the same way
    // onStrokeMove() throttles drawing, so dragging a multi-stroke
    // selection doesn't flood Firebase with an update per pointer-move.
    fun moveSelectionBy(dxNorm: Float, dyNorm: Float) {
        if (moveOriginalStrokes.isEmpty()) return
        moveCumulativeDx += dxNorm
        moveCumulativeDy += dyNorm
        moveEventsSinceLastPush++
        if (moveEventsSinceLastPush >= POINTS_PER_PUSH) {
            moveEventsSinceLastPush = 0
            pushMovedStrokes()
        }
    }

    fun endMove() {
        if (moveOriginalStrokes.isNotEmpty()) pushMovedStrokes() // final flush
        moveOriginalStrokes = emptyMap()
        moveCumulativeDx = 0f
        moveCumulativeDy = 0f
        moveEventsSinceLastPush = 0
    }

    private fun pushMovedStrokes() {
        for ((id, original) in moveOriginalStrokes) {
            val moved = original.points.mapIndexed { index, value ->
                if (index % 2 == 0) value + moveCumulativeDx else value + moveCumulativeDy
            }
            FirebaseSync.updateLiveStroke(id, original.copy(points = moved))
        }
    }

    // Nearest stroke within HIT_TEST_PADDING of (x, y), or null if nothing's
    // close enough — checked against every stroke's actual path/dot, not
    // just a bounding box, since strokes are often thin relative to the
    // whole canvas.
    private fun hitTestStroke(x: Float, y: Float): String? {
        var bestId: String? = null
        var bestDistance = Float.MAX_VALUE
        for ((id, stroke) in _uiState.value.strokes) {
            val distance = distanceToStroke(x, y, stroke)
            val threshold = (stroke.width.toFloat() / 2f) + HIT_TEST_PADDING
            if (distance <= threshold && distance < bestDistance) {
                bestDistance = distance
                bestId = id
            }
        }
        return bestId
    }

    private fun distanceToStroke(x: Float, y: Float, stroke: LiveStroke): Float {
        val points = stroke.points
        if (points.size < 4) {
            return if (points.size < 2) Float.MAX_VALUE else distance(x, y, points[0].toFloat(), points[1].toFloat())
        }
        var minDistance = Float.MAX_VALUE
        var i = 0
        while (i + 3 < points.size) {
            val segmentDistance = distanceToSegment(
                x, y,
                points[i].toFloat(), points[i + 1].toFloat(),
                points[i + 2].toFloat(), points[i + 3].toFloat()
            )
            if (segmentDistance < minDistance) minDistance = segmentDistance
            i += 2
        }
        return minDistance
    }

    private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val abx = bx - ax
        val aby = by - ay
        val lengthSquared = abx * abx + aby * aby
        if (lengthSquared == 0f) return distance(px, py, ax, ay)
        val t = (((px - ax) * abx + (py - ay) * aby) / lengthSquared).coerceIn(0f, 1f)
        return distance(px, py, ax + t * abx, ay + t * aby)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    fun onStrokeStart(x: Float, y: Float) {
        val id = FirebaseSync.newLiveStrokeId()
        currentStrokeId = id
        currentStrokePoints = mutableListOf(x.toDouble(), y.toDouble())
        pointsSinceLastPush = 0
        pushCurrentStroke()
    }

    fun onStrokeMove(x: Float, y: Float) {
        if (currentStrokeId == null) return
        currentStrokePoints.add(x.toDouble())
        currentStrokePoints.add(y.toDouble())
        pointsSinceLastPush++
        if (pointsSinceLastPush >= POINTS_PER_PUSH) {
            pointsSinceLastPush = 0
            pushCurrentStroke()
        }
    }

    fun onStrokeEnd() {
        val id = currentStrokeId ?: return
        pushCurrentStroke() // final flush, so the last few sampled-out points aren't lost
        myStrokeStack.add(id)
        currentStrokeId = null
        currentStrokePoints = mutableListOf()
    }

    private fun pushCurrentStroke() {
        val id = currentStrokeId ?: return
        FirebaseSync.updateLiveStroke(
            id,
            LiveStroke(
                authorUid = _uiState.value.myUid,
                color = _uiState.value.selectedColor,
                points = currentStrokePoints.toList(),
                width = _uiState.value.selectedWidth.toDouble()
            )
        )
    }

    // Only ever undoes YOUR OWN strokes, one at a time, most recent first —
    // undoing a partner's stroke out from under them while they might still
    // be drawing isn't something a single tap should risk.
    fun undoLastStroke() {
        val id = myStrokeStack.removeLastOrNull() ?: return
        FirebaseSync.removeLiveStroke(id)
    }

    // Unlike undo, this wipes the WHOLE shared canvas — both of your work,
    // not just yours — so DrawingScreen gates it behind a confirmation
    // dialog before ever calling this.
    fun clearCanvas() {
        myStrokeStack.clear()
        moveOriginalStrokes = emptyMap()
        _uiState.value = _uiState.value.copy(selectedStrokeIds = emptySet())
        viewModelScope.launch { FirebaseSync.clearLiveDrawing() }
    }

    // Rasterizes whatever's currently on the shared canvas into a PNG and
    // hands it to PhotoSendService — the same durable, app-closure-survives
    // send path photos already use (see MessageRepository.sendPhotoMessage's
    // messageType param). The canvas then clears for both of you, win or
    // lose — if the upload fails, SendingNotifier.showDrawingSendFailed
    // still tells you, and you can just draw a new one.
    fun send() {
        val strokes = _uiState.value.strokes
        if (strokes.isEmpty()) return
        _uiState.value = _uiState.value.copy(sendState = DrawingSendState.Sending)
        viewModelScope.launch {
            val app = getApplication<Application>()
            val fresh = FirebaseSync.fetchLiveDrawingOnce().ifEmpty { strokes }
            val bitmap = DrawingRasterizer.render(fresh.values, RENDER_SIZE_PX)
            val file = try {
                saveToCacheFile(app, bitmap)
            } catch (e: Exception) {
                CrashLogger.recordException("DrawingViewModel.send: saveToCacheFile failed", e)
                _uiState.value = _uiState.value.copy(sendState = DrawingSendState.Error("Couldn't prepare the drawing. Try again."))
                return@launch
            }
            FirebaseSync.clearLiveDrawing()
            myStrokeStack.clear()
            moveOriginalStrokes = emptyMap()
            _uiState.value = _uiState.value.copy(selectedStrokeIds = emptySet())
            PhotoSendService.start(app, file, caption = "", unlockAt = 0, contentType = "image/png", messageType = "drawing")
        }
    }

    fun consumeSendState() {
        _uiState.value = _uiState.value.copy(sendState = DrawingSendState.Idle)
    }

    private fun saveToCacheFile(context: Context, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "outgoing_drawings").apply { mkdirs() }
        val file = File(dir, "drawing_${System.currentTimeMillis()}.png")
        file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
        return file
    }

    override fun onCleared() {
        listener?.let { FirebaseSync.removeLiveDrawingListener(it) }
        listener = null
        presenceListener?.let { FirebaseSync.removeDrawingPresenceListener(it) }
        presenceListener = null
        lastActiveListener?.let { FirebaseSync.removeDrawingLastActiveListener(it) }
        lastActiveListener = null
        super.onCleared()
    }

    companion object {
        private const val POINTS_PER_PUSH = 3
        private const val RENDER_SIZE_PX = 720

        // A minimum touch-target padding (normalized 0f..1f stroke space)
        // added on top of a stroke's own half-width for hit-testing — a
        // hairline-thin stroke would otherwise be nearly impossible to tap.
        private const val HIT_TEST_PADDING = 0.02f
    }
}
