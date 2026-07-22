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

data class DrawingUiState(
    val myUid: String = "",
    // Keyed by stroke id — both of you can add to this map at once, each
    // only ever touching your own key (see FirebaseSync.updateLiveStroke),
    // so this is always the merged, up-to-date joint canvas.
    val strokes: Map<String, LiveStroke> = emptyMap(),
    val selectedColor: String = DrawingColors.DEFAULT,
    val selectedWidth: Float = DrawingColors.DEFAULT_WIDTH_FRACTION,
    val sendState: DrawingSendState = DrawingSendState.Idle
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
    }

    fun setColor(color: String) {
        _uiState.value = _uiState.value.copy(selectedColor = color)
    }

    fun setWidth(width: Float) {
        _uiState.value = _uiState.value.copy(selectedWidth = width)
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
                _uiState.value = _uiState.value.copy(sendState = DrawingSendState.Error("Couldn't prepare the drawing. Try again."))
                return@launch
            }
            FirebaseSync.clearLiveDrawing()
            myStrokeStack.clear()
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
        super.onCleared()
    }

    companion object {
        private const val POINTS_PER_PUSH = 3
        private const val RENDER_SIZE_PX = 720
    }
}
