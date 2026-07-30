package com.glimpse.app.ui.heartrate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.glimpse.app.data.heartrate.CameraLuma
import com.glimpse.app.data.heartrate.HeartRateAnalyzer
import com.glimpse.app.data.heartrate.HeartRateSensorProbe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HeartRateUiState(
    val measuring: Boolean = false,
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
            bpm = null,
            confidence = 0f,
            waveform = emptyList(),
            fingerDetected = false,
            samplesCollected = 0
        )
    }

    fun stop() {
        _uiState.value = _uiState.value.copy(measuring = false)
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
        _uiState.value = _uiState.value.copy(
            bpm = reading.bpm,
            confidence = reading.confidence,
            waveform = reading.waveform,
            meanLevel = reading.meanLevel,
            spread = frame.spread,
            fingerDetected = true,
            samplesCollected = _uiState.value.samplesCollected + 1
        )
    }
}
