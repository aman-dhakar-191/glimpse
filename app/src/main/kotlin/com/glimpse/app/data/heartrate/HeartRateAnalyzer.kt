package com.glimpse.app.data.heartrate

import kotlin.math.abs
import kotlin.math.roundToInt

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
        // True on the single frame a heartbeat was just detected. Drives a
        // haptic tick, because a finger on the rear lens means the screen is
        // face down and nothing on it can be seen while measuring.
        val beatNow: Boolean,
        // Raw mean brightness, surfaced purely so a device that behaves
        // unexpectedly can be diagnosed without a debugger attached.
        val meanLevel: Double
    )

    private val values = ArrayDeque<Double>()
    private val timestamps = ArrayDeque<Long>()
    // Nullable rather than a sentinel like Long.MIN_VALUE. Subtracting that
    // sentinel from a timestamp overflows to a negative number, which then
    // compares as "too soon" against every refractory period — so no beat
    // ever fired. Unbounded arithmetic hides this, which is exactly why it
    // survived being modelled outside the JVM and was caught only by a test.
    private var lastBeatMillis: Long? = null
    private var beatCount = 0

    fun reset() {
        values.clear()
        timestamps.clear()
        lastBeatMillis = null
        beatCount = 0
    }

    fun add(value: Double, timestampMillis: Long) {
        values.addLast(value)
        timestamps.addLast(timestampMillis)
        while (timestamps.isNotEmpty() && timestampMillis - timestamps.first() > windowMillis) {
            values.removeFirst()
            timestamps.removeFirst()
        }
    }

    // Expected to be called exactly once per new sample: beat detection is
    // stateful and inspects the newest analysable point each time.
    fun analyze(): Reading {
        val raw = values.toList()
        val times = timestamps.toList()
        val mean = if (raw.isEmpty()) 0.0 else raw.average()
        if (raw.size < MIN_SAMPLES || times.size < 2) {
            return Reading(null, 0f, emptyList(), beatCount, false, mean)
        }

        val millisPerSample = (times.last() - times.first()).toDouble() / (times.size - 1)
        if (millisPerSample <= 0.0) return Reading(null, 0f, emptyList(), beatCount, false, mean)

        // Subtracting a moving average removes the slow drift that dwarfs
        // the pulse — finger pressure easing, the sensor's auto-exposure
        // hunting. The window is wider than one beat, so a beat survives it
        // while anything slower is flattened away.
        val detrendWindow = (DETREND_WINDOW_MILLIS / millisPerSample).toInt().coerceAtLeast(3)
        val detrended = subtractMovingAverage(raw, detrendWindow)
        val smoothed = movingAverage(detrended, SMOOTHING_WINDOW)

        val sortedMagnitudes = smoothed.map { abs(it) }.sorted()
        // The 95th percentile, not the maximum: scaling by the largest value
        // lets one movement spike flatten everything else, and not the 75th
        // either — that clips a quarter of every waveform by construction,
        // which turned a real pulse into a square wave on screen.
        val displayScale = maxOf(percentile(sortedMagnitudes, DISPLAY_PERCENTILE), MIN_DISPLAY_AMPLITUDE)
        val waveform = smoothed.map { (it / displayScale).toFloat().coerceIn(-1f, 1f) }
        val typicalAmplitude = percentile(sortedMagnitudes, BEAT_AMPLITUDE_PERCENTILE)

        // Edges are where a centred moving average has fewest samples to work
        // with, so a steep baseline leaves large residuals there. Left in,
        // those residuals dominate and drag the correlation down on exactly
        // the drifting signals detrending was supposed to rescue.
        val trim = detrendWindow / 2
        val core = if (smoothed.size > 2 * trim + MIN_SAMPLES) {
            smoothed.subList(trim, smoothed.size - trim)
        } else {
            smoothed
        }

        val estimate = estimateRate(core, millisPerSample)
        val beatNow = detectBeat(smoothed, times, typicalAmplitude, estimate?.periodMillis, trim)

        return Reading(
            bpm = estimate?.bpm?.takeIf { it in MIN_BPM..MAX_BPM },
            confidence = estimate?.confidence ?: 0f,
            waveform = waveform,
            beatsDetected = beatCount,
            beatNow = beatNow,
            meanLevel = mean
        )
    }

    private data class Estimate(val bpm: Int, val confidence: Float, val periodMillis: Double)

    // Autocorrelation rather than timing the gaps between peaks.
    //
    // A real pulse wave has a second, smaller bump partway down its falling
    // edge — the dicrotic notch, the aortic valve closing. Peak-based timing
    // counts that as an extra beat, which alternates the intervals and makes
    // a textbook-clean trace look wildly irregular; on a 55bpm signal it
    // reported 75. Autocorrelation asks "how similar is this signal to itself
    // one beat later", which the notch doesn't disturb because it repeats
    // right along with everything else.
    private fun estimateRate(signal: List<Double>, millisPerSample: Double): Estimate? {
        val n = signal.size
        val zeroLag = signal.sumOf { it * it } / n
        if (zeroLag <= 0.0) return null

        val minLag = (MIN_INTERVAL_MILLIS / millisPerSample).toInt().coerceAtLeast(1)
        val maxLag = (MAX_INTERVAL_MILLIS / millisPerSample).toInt().coerceAtMost(n - 2)
        if (maxLag <= minLag) return null

        val correlations = HashMap<Int, Double>(maxLag - minLag + 1)
        for (lag in minLag..maxLag) {
            var sum = 0.0
            for (i in 0 until n - lag) sum += signal[i] * signal[i + lag]
            correlations[lag] = sum / (n - lag) / zeroLag
        }
        val strongest = correlations.values.max()
        if (strongest <= 0.0) return null

        // The fundamental, not the strongest. A periodic signal correlates
        // just as well at two or three times its period, and picking the
        // global maximum let a 140bpm pulse be reported as 47 — the same
        // waveform, measured three beats at a time.
        var chosen = correlations.entries.maxByOrNull { it.value }?.key ?: return null
        for (lag in minLag..maxLag) {
            val r = correlations.getValue(lag)
            if (r < HARMONIC_TOLERANCE * strongest) continue
            val previous = correlations[lag - 1] ?: Double.NEGATIVE_INFINITY
            val next = correlations[lag + 1] ?: Double.NEGATIVE_INFINITY
            if (r >= previous && r >= next) {
                chosen = lag
                break
            }
        }

        // One sample of lag is ~4% of a beat at 30fps, enough to shift the
        // reported rate by a couple of bpm. Fitting a parabola through the
        // peak and its neighbours recovers the fraction between samples.
        var refinedLag = chosen.toDouble()
        val before = correlations[chosen - 1]
        val after = correlations[chosen + 1]
        if (before != null && after != null) {
            val curvature = before - 2 * correlations.getValue(chosen) + after
            if (curvature != 0.0) {
                val offset = 0.5 * (before - after) / curvature
                if (offset > -1.0 && offset < 1.0) refinedLag = chosen + offset
            }
        }

        val periodMillis = refinedLag * millisPerSample
        if (periodMillis <= 0.0) return null
        val correlation = correlations.getValue(chosen)
        val confidence = ((correlation - CONFIDENCE_FLOOR) / CONFIDENCE_SPAN).coerceIn(0.0, 1.0)
        return Estimate(
            bpm = (60_000.0 / periodMillis).roundToInt(),
            confidence = confidence.toFloat(),
            periodMillis = periodMillis
        )
    }

    // Fires as the newest analysable sample turns out to have been a peak.
    // Separate from the rate estimate above, which describes the last ten
    // seconds rather than this instant.
    private fun detectBeat(
        smoothed: List<Double>,
        times: List<Long>,
        typicalAmplitude: Double,
        periodMillis: Double?,
        trim: Int
    ): Boolean {
        if (smoothed.size < 3 || typicalAmplitude <= 0.0) return false
        // Backed off from the newest sample by the same distance the rate
        // estimate trims. A centred moving average has fewest samples to
        // work with at the very end of the buffer, so the detrended signal
        // there is compressed toward zero and real peaks fail the threshold
        // — checking the newest point found two beats in ten seconds
        // instead of ten. The cost is a fixed few hundred milliseconds of
        // lag, which leaves the rhythm intact even though it shifts the
        // phase, and rhythm is the whole point of a tick you can feel.
        val index = smoothed.size - 1 - trim.coerceAtLeast(1)
        if (index < 1 || index >= smoothed.lastIndex) return false
        val value = smoothed[index]
        if (value < typicalAmplitude * BEAT_THRESHOLD_FACTOR) return false
        if (value < smoothed[index - 1] || value < smoothed[index + 1]) return false

        val now = times[index]
        // Scaled to the current rate so the dicrotic notch, which lands about
        // a third of the way into a beat, falls inside the refractory period
        // instead of being ticked as a beat of its own.
        val refractory = maxOf(
            MIN_BEAT_GAP_MILLIS,
            (periodMillis ?: 0.0) * BEAT_REFRACTORY_FRACTION
        )
        val previousBeat = lastBeatMillis
        if (previousBeat != null && now - previousBeat < refractory) return false

        lastBeatMillis = now
        beatCount++
        return true
    }

    // Linear interpolation would be more precise, but this runs on every
    // frame and the index is only ever used to pick a scale — a fraction of
    // one sample either way changes nothing downstream.
    private fun percentile(sorted: List<Double>, fraction: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val index = (fraction * (sorted.size - 1)).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
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
        const val SMOOTHING_WINDOW = 3
        const val DETREND_WINDOW_MILLIS = 750.0
        // 300ms between beats is 200bpm and 1714ms is 35bpm — the bounds of
        // what a fingertip resting on a lens will ever legitimately produce,
        // and therefore the only lags worth searching.
        const val MIN_INTERVAL_MILLIS = 300.0
        const val MAX_INTERVAL_MILLIS = 1_714.0
        const val MIN_BPM = 35
        const val MAX_BPM = 200
        // How close to the strongest correlation a shorter lag has to come
        // before it is preferred as the fundamental. Loose enough to catch a
        // true period whose harmonic scores marginally higher, tight enough
        // not to lock onto noise well below the peak.
        const val HARMONIC_TOLERANCE = 0.9
        // Correlation below the floor scores zero confidence; the span sets
        // where it reaches full. Noise lands around 0.1-0.3, a real pulse
        // above 0.8.
        const val CONFIDENCE_FLOOR = 0.25
        const val CONFIDENCE_SPAN = 0.55
        const val DISPLAY_PERCENTILE = 0.95
        const val BEAT_AMPLITUDE_PERCENTILE = 0.75
        // In raw luma units (0..255). Sensor noise sits well under this; a
        // real pulse reaches it. Worth re-tuning against real measurements.
        const val MIN_DISPLAY_AMPLITUDE = 1.0
        const val BEAT_THRESHOLD_FACTOR = 0.5
        const val MIN_BEAT_GAP_MILLIS = 300.0
        const val BEAT_REFRACTORY_FRACTION = 0.5
    }
}
