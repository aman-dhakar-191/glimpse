package com.glimpse.app.ui.heartrate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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
            samplesCollected = 0
        )
    }

    fun stop() {
        _uiState.value = _uiState.value.copy(measuring = false)
    }

    // Called from the camera analysis thread, once per frame.
    fun onFrame(luma: Double, timestampMillis: Long) {
        if (!_uiState.value.measuring) return
        analyzer.add(luma, timestampMillis)
        val reading = analyzer.analyze()
        _uiState.value = _uiState.value.copy(
            bpm = reading.bpm,
            confidence = reading.confidence,
            waveform = reading.waveform,
            meanLevel = reading.meanLevel,
            samplesCollected = _uiState.value.samplesCollected + 1
        )
    }
}
