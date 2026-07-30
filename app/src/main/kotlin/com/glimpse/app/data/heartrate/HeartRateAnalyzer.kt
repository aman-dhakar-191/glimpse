package com.glimpse.app.data.heartrate

import kotlin.math.abs
import kotlin.math.sqrt

// Turns a stream of brightness samples into a pulse.
//
// The physical trick (photoplethysmography): with a fingertip pressed over
// the lens and the torch on, every heartbeat pushes blood through the
// capillaries under the skin, and that blood absorbs light. The image gets
// imperceptibly darker and lighter roughly once per beat. The camera can't
// see a heart, but it can see that flicker — and the flicker IS the pulse.
//
// Deliberately pure Kotlin with no Android imports: this is the part most
// likely to be wrong, and keeping it free of the camera lets it be tested
// against synthetic signals of known rate (see HeartRateAnalyzerTest)
// rather than only by holding a finger on a phone and squinting.
class HeartRateAnalyzer(
    // Long enough to hold several beats even for a slow resting pulse, short
    // enough that the reading follows a changing rate instead of averaging
    // across a minute of history.
    private val windowMillis: Long = 10_000
) {

    data class Reading(
        // Null until there's enough consistent signal to be worth showing.
        // A confidently wrong number is worse than an honest "keep still".
        val bpm: Int?,
        val confidence: Float,
        // Normalised to roughly -1..1 for drawing. Seeing the waveform is
        // what makes a bad reading diagnosable — a flat line means the
        // finger isn't sealed, noise means it's moving.
        val waveform: List<Float>,
        val beatsDetected: Int,
        // Raw mean brightness, surfaced purely so a device that behaves
        // unexpectedly can be diagnosed without a debugger attached.
        val meanLevel: Double
    )

    private val values = ArrayDeque<Double>()
    private val timestamps = ArrayDeque<Long>()

    fun reset() {
        values.clear()
        timestamps.clear()
    }

    fun add(value: Double, timestampMillis: Long) {
        values.addLast(value)
        timestamps.addLast(timestampMillis)
        while (timestamps.isNotEmpty() && timestampMillis - timestamps.first() > windowMillis) {
            values.removeFirst()
            timestamps.removeFirst()
        }
    }

    fun analyze(): Reading {
        val raw = values.toList()
        val times = timestamps.toList()
        val mean = if (raw.isEmpty()) 0.0 else raw.average()
        if (raw.size < MIN_SAMPLES || times.size < 2) {
            return Reading(null, 0f, emptyList(), 0, mean)
        }

        val millisPerSample = (times.last() - times.first()).toDouble() / (times.size - 1)
        if (millisPerSample <= 0.0) return Reading(null, 0f, emptyList(), 0, mean)

        // Subtracting a moving average removes the slow drift that dwarfs
        // the pulse — finger pressure easing, the sensor's auto-exposure
        // hunting. The window is wider than one beat, so a beat survives it
        // while anything slower is flattened away.
        val detrendWindow = (DETREND_WINDOW_MILLIS / millisPerSample).toInt().coerceAtLeast(3)
        val detrended = subtractMovingAverage(raw, detrendWindow)
        val smoothed = movingAverage(detrended, SMOOTHING_WINDOW)

        val amplitude = smoothed.maxOfOrNull { abs(it) } ?: 0.0
        val waveform = if (amplitude <= 0.0) {
            List(smoothed.size) { 0f }
        } else {
            smoothed.map { (it / amplitude).toFloat() }
        }

        val minPeakGap = (MIN_BEAT_GAP_MILLIS / millisPerSample).toInt().coerceAtLeast(1)
        val peaks = findPeaks(smoothed, minPeakGap)
        if (peaks.size < MIN_PEAKS) {
            return Reading(null, 0f, waveform, peaks.size, mean)
        }

        // Intervals outside a plausible human range are noise, not beats.
        // Dropping them rather than the whole reading means one spurious
        // spike doesn't discard four good seconds of signal.
        val intervals = peaks.zipWithNext { a, b -> (times[b] - times[a]).toDouble() }
            .filter { it in MIN_INTERVAL_MILLIS..MAX_INTERVAL_MILLIS }
        if (intervals.size < MIN_PEAKS - 1) {
            return Reading(null, 0f, waveform, peaks.size, mean)
        }

        // Median, not mean: a single missed or doubled beat shifts a mean
        // enough to matter, and barely moves a median.
        val median = intervals.sorted()[intervals.size / 2]
        val bpm = (60_000.0 / median).toInt()

        // A real pulse is regular, so how tightly the intervals agree is a
        // good proxy for whether this is a pulse at all. Noise produces
        // scattered intervals and scores near zero on its own.
        val intervalMean = intervals.average()
        val deviation = sqrt(intervals.sumOf { (it - intervalMean) * (it - intervalMean) } / intervals.size)
        val variation = if (intervalMean > 0) deviation / intervalMean else 1.0
        val steadiness = (1.0 - variation * VARIATION_PENALTY).coerceIn(0.0, 1.0)
        // Ramps in as beats accumulate, so a reading built on the bare
        // minimum of three intervals never claims full confidence.
        val evidence = (intervals.size.toDouble() / CONFIDENT_INTERVAL_COUNT).coerceAtMost(1.0)
        val confidence = (steadiness * evidence).toFloat()

        return Reading(
            bpm = if (bpm in MIN_BPM..MAX_BPM) bpm else null,
            confidence = confidence,
            waveform = waveform,
            beatsDetected = peaks.size,
            meanLevel = mean
        )
    }

    // A peak is a local maximum standing clear of the noise floor, with
    // enough space since the last one that it can't be the same beat counted
    // twice. The threshold is relative to the signal's own spread, since
    // absolute brightness varies wildly between phones and skin tones.
    private fun findPeaks(signal: List<Double>, minGap: Int): List<Int> {
        if (signal.size < 3) return emptyList()
        val positive = signal.filter { it > 0 }
        if (positive.isEmpty()) return emptyList()
        val threshold = positive.average() * PEAK_THRESHOLD_FACTOR

        val peaks = mutableListOf<Int>()
        for (i in 1 until signal.lastIndex) {
            val v = signal[i]
            if (v < threshold) continue
            if (v < signal[i - 1] || v < signal[i + 1]) continue
            val previous = peaks.lastOrNull()
            if (previous != null && i - previous < minGap) {
                // Same beat seen twice — keep whichever is actually taller.
                if (v > signal[previous]) peaks[peaks.lastIndex] = i
                continue
            }
            peaks.add(i)
        }
        return peaks
    }

    private fun subtractMovingAverage(signal: List<Double>, window: Int): List<Double> {
        val baseline = movingAverage(signal, window)
        return signal.mapIndexed { i, v -> v - baseline[i] }
    }

    // Centred so the output doesn't lag the input, which would shift every
    // detected peak and skew the intervals between them.
    private fun movingAverage(signal: List<Double>, window: Int): List<Double> {
        if (window <= 1 || signal.isEmpty()) return signal
        val half = window / 2
        return signal.indices.map { i ->
            val from = (i - half).coerceAtLeast(0)
            val to = (i + half).coerceAtMost(signal.lastIndex)
            var sum = 0.0
            for (j in from..to) sum += signal[j]
            sum / (to - from + 1)
        }
    }

    private companion object {
        const val MIN_SAMPLES = 60
        const val MIN_PEAKS = 4
        const val SMOOTHING_WINDOW = 3
        const val DETREND_WINDOW_MILLIS = 750.0
        // 300ms between beats is 200bpm — above anything a resting finger
        // on a lens will produce, so anything closer together is noise.
        const val MIN_BEAT_GAP_MILLIS = 300.0
        const val MIN_INTERVAL_MILLIS = 300.0
        const val MAX_INTERVAL_MILLIS = 1_714.0
        const val MIN_BPM = 35
        const val MAX_BPM = 200
        const val PEAK_THRESHOLD_FACTOR = 0.6
        const val VARIATION_PENALTY = 3.0
        const val CONFIDENT_INTERVAL_COUNT = 6.0
    }
}
