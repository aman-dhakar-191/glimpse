package com.glimpse.app.ui.heartrate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.glimpse.app.data.heartrate.CameraLuma
import com.glimpse.app.data.heartrate.HeartRateAnalyzer
import com.glimpse.app.data.heartrate.HeartRateSensorProbe
import com.glimpse.app.data.heartrate.LiveBeatScheduler
import com.glimpse.app.data.heartrate.LubDubHaptics
import com.glimpse.app.data.heartrate.PulseHaptics
import com.glimpse.app.data.firebase.FirebaseSync
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HeartRateUiState(
    val measuring: Boolean = false,
    // True from the moment the torch comes on until auto-exposure has been
    // locked. Samples taken during it are the exposure ramp, not a pulse.
    val settling: Boolean = false,
    val bpm: Int? = null,
    val confidence: Float = 0f,
    val waveform: List<Float> = emptyList(),
    val meanLevel: Double = 0.0,
    val spread: Double = 0.0,
    // False means the camera isn't looking at a fingertip. Distinguishing
    // that from "measuring badly" is the difference between an app that can
    // be debugged and one that just shows a wiggling line forever.
    val fingerDetected: Boolean = false,
    val samplesCollected: Int = 0,
    val beatsFelt: Int = 0,
    // Whether your beats are being streamed to your partner right now.
    val sharing: Boolean = false,
    // Their live rate, when they are the one sharing.
    val partnerBpm: Int? = null,
    val partnerLive: Boolean = false,
    // Filled once on entry — see HeartRateSensorProbe for why this is
    // reported rather than silently used.
    val sensorReport: String = ""
) {
    // Deliberately strict. A number shown with a shrug still gets believed,
    // and this one is going to be sent to another person as "my heartbeat".
    val trustworthy: Boolean get() = bpm != null && confidence >= 0.6f
}

class HeartRateViewModel(application: Application) : AndroidViewModel(application) {

    private val analyzer = HeartRateAnalyzer()
    private val beatScheduler = LiveBeatScheduler()
    private var beatSequence = 0L
    private var presenceListener: ValueEventListener? = null
    private var beatListener: ValueEventListener? = null
    // Beats echo back from the database to the sender as well. Ignoring your
    // own uid is what stops you buzzing along to your own heart.
    private val myUid: String? get() = FirebaseAuth.getInstance().currentUser?.uid
    private val _uiState = MutableStateFlow(HeartRateUiState())
    val uiState: StateFlow<HeartRateUiState> = _uiState.asStateFlow()

    fun probeSensors() {
        val capability = HeartRateSensorProbe.probe(getApplication())
        val report = buildString {
            if (capability.hasStandardHeartRateSensor) {
                append("This phone exposes a standard heart-rate sensor.")
            } else {
                append("No standard heart-rate sensor — using the camera.")
            }
            if (capability.vendorCandidates.isNotEmpty()) {
                append("\nVendor sensors worth investigating: ")
                append(capability.vendorCandidates.joinToString(", "))
            }
        }
        _uiState.value = _uiState.value.copy(sensorReport = report)
    }

    fun start() {
        analyzer.reset()
        _uiState.value = _uiState.value.copy(
            measuring = true,
            settling = true,
            bpm = null,
            confidence = 0f,
            waveform = emptyList(),
            fingerDetected = false,
            samplesCollected = 0,
            beatsFelt = 0
        )
    }

    fun stop() {
        stopSharing()
        _uiState.value = _uiState.value.copy(measuring = false, settling = false)
    }

    // Streaming is opt-in and separate from measuring: a reading taken to
    // check the app still works is not the same as deliberately putting your
    // pulse in someone else's hand.
    fun setSharing(enabled: Boolean) {
        if (enabled) {
            beatSequence = 0
            FirebaseSync.markHeartbeatSharing()
        } else {
            FirebaseSync.clearHeartbeatSharing()
        }
        _uiState.value = _uiState.value.copy(sharing = enabled)
    }

    private fun stopSharing() {
        if (!_uiState.value.sharing) return
        FirebaseSync.clearHeartbeatSharing()
        _uiState.value = _uiState.value.copy(sharing = false)
    }

    // Listening runs for as long as the screen is open, regardless of
    // whether you are measuring — the point is to be reachable when they
    // decide to share, not only while you happen to be measuring too.
    fun startListening() {
        if (beatListener != null) return
        beatScheduler.reset()
        presenceListener = FirebaseSync.listenToHeartbeatPresence { uids ->
            val partnerSharing = uids.any { it != myUid }
            if (!partnerSharing) beatScheduler.reset()
            _uiState.value = _uiState.value.copy(
                partnerLive = partnerSharing,
                partnerBpm = if (partnerSharing) _uiState.value.partnerBpm else null
            )
        }
        beatListener = FirebaseSync.listenToHeartbeat { uid, beatAtMillis, bpm ->
            if (uid == myUid) return@listenToHeartbeat
            onPartnerBeat(beatAtMillis, bpm)
        }
    }

    fun stopListening() {
        presenceListener?.let { FirebaseSync.removeHeartbeatPresenceListener(it) }
        beatListener?.let { FirebaseSync.removeHeartbeatListener(it) }
        presenceListener = null
        beatListener = null
    }

    // Held back and replayed at its true spacing rather than played on
    // arrival — see LiveBeatScheduler for why that matters.
    private fun onPartnerBeat(beatAtMillis: Long, bpm: Int) {
        val now = System.currentTimeMillis()
        val playAt = beatScheduler.schedule(beatAtMillis, now)
        _uiState.value = _uiState.value.copy(partnerBpm = bpm, partnerLive = true)
        viewModelScope.launch {
            val wait = playAt - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            LubDubHaptics.play(getApplication())
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
        stopSharing()
    }

    // Called once auto-exposure has been locked. Everything gathered before
    // this point was measured under a moving exposure, so it is thrown away
    // rather than blended into the reading.
    fun markSettled() {
        analyzer.reset()
        _uiState.value = _uiState.value.copy(
            settling = false,
            bpm = null,
            confidence = 0f,
            waveform = emptyList(),
            samplesCollected = 0,
            beatsFelt = 0
        )
    }

    // Called from the camera analysis thread, once per frame.
    fun onFrame(frame: CameraLuma.Frame, timestampMillis: Long) {
        if (!_uiState.value.measuring) return

        // Frames without a fingertip are thrown away rather than fed in.
        // Letting them through poisons the window with whatever the room
        // looks like, and the analyzer would spend the next ten seconds
        // digesting that instead of a pulse.
        if (!frame.fingerDetected) {
            analyzer.reset()
            _uiState.value = _uiState.value.copy(
                bpm = null,
                confidence = 0f,
                waveform = emptyList(),
                meanLevel = frame.mean,
                spread = frame.spread,
                fingerDetected = false
            )
            return
        }

        analyzer.add(frame.mean, timestampMillis)
        val reading = analyzer.analyze()

        // Ticked here rather than from the screen so it fires on the frame
        // the beat was found, with no recomposition in between — a haptic
        // that lags the pulse it is reporting is worse than none.
        if (reading.beatNow) {
            PulseHaptics.tick(getApplication())
            // Only stream beats once the rate is trustworthy. Publishing
            // during the first noisy seconds would have their phone beating
            // out a number this device does not yet believe.
            val bpm = reading.bpm
            if (_uiState.value.sharing && bpm != null && reading.confidence >= SHARE_CONFIDENCE) {
                FirebaseSync.publishBeat(++beatSequence, timestampMillis, bpm)
            }
        }

        _uiState.value = _uiState.value.copy(
            bpm = reading.bpm,
            confidence = reading.confidence,
            waveform = reading.waveform,
            meanLevel = reading.meanLevel,
            spread = frame.spread,
            fingerDetected = true,
            samplesCollected = _uiState.value.samplesCollected + 1,
            beatsFelt = reading.beatsDetected
        )
    }

    private companion object {
        // Matches the bar the screen uses before it will show a number at
        // all — nothing gets sent that this device would not itself display.
        const val SHARE_CONFIDENCE = 0.6f
    }
}
