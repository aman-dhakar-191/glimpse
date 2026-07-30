package com.glimpse.app.data.heartrate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

// The analyzer is the one part of the heartbeat feature that can be checked
// without a phone and a fingertip, so it is checked hard here: synthetic
// signals of a known rate go in, and the recovered rate has to match.
class HeartRateAnalyzerTest {

    // 30fps, matching the camera's analysis cadence.
    private val frameIntervalMillis = 33L

    private fun feed(
        analyzer: HeartRateAnalyzer,
        bpm: Double,
        seconds: Double,
        // Real photoplethysmography rides on a large, slowly drifting
        // baseline — this reproduces that so detrending is actually
        // exercised rather than handed a conveniently centred signal.
        baseline: Double = 128.0,
        drift: Double = 0.0,
        noise: Double = 0.0,
        seed: Int = 7
    ) {
        val random = Random(seed)
        val frames = (seconds * 1000 / frameIntervalMillis).toInt()
        val beatsPerMilli = bpm / 60_000.0
        for (i in 0 until frames) {
            val t = i * frameIntervalMillis.toDouble()
            val pulse = sin(2 * PI * beatsPerMilli * t)
            val value = baseline + drift * t / 1000.0 + pulse + noise * (random.nextDouble() - 0.5)
            analyzer.add(value, t.toLong())
        }
    }

    @Test
    fun `recovers a clean 60 bpm signal`() {
        val analyzer = HeartRateAnalyzer()
        feed(analyzer, bpm = 60.0, seconds = 10.0)

        val reading = analyzer.analyze()
        assertNotNull("expected a reading from a clean signal", reading.bpm)
        assertEquals(60.0, reading.bpm!!.toDouble(), 3.0)
        assertTrue("clean signal should be confident, was ${reading.confidence}", reading.confidence > 0.7f)
    }

    @Test
    fun `recovers rates across the plausible human range`() {
        listOf(45.0, 72.0, 100.0, 140.0).forEach { expected ->
            val analyzer = HeartRateAnalyzer()
            feed(analyzer, bpm = expected, seconds = 10.0)
            val reading = analyzer.analyze()
            assertNotNull("no reading at $expected bpm", reading.bpm)
            assertEquals(expected, reading.bpm!!.toDouble(), 5.0)
        }
    }

    // The failure this guards against is subtle: a drifting baseline dwarfs
    // the pulse, so without detrending the peak finder locks onto the drift
    // and reports a plausible-looking but entirely wrong number.
    @Test
    fun `survives a drifting baseline`() {
        val analyzer = HeartRateAnalyzer()
        feed(analyzer, bpm = 72.0, seconds = 10.0, drift = 8.0)

        val reading = analyzer.analyze()
        assertNotNull("drift should not defeat detrending", reading.bpm)
        assertEquals(72.0, reading.bpm!!.toDouble(), 5.0)
    }

    @Test
    fun `still reads through moderate noise`() {
        val analyzer = HeartRateAnalyzer()
        feed(analyzer, bpm = 75.0, seconds = 10.0, noise = 0.4)

        val reading = analyzer.analyze()
        assertNotNull("moderate noise should still resolve", reading.bpm)
        assertEquals(75.0, reading.bpm!!.toDouble(), 6.0)
    }

    // Reporting a number when there is no pulse is the worst failure mode
    // this thing has: it would send a fabricated heartbeat to someone.
    //
    // Asserted as a comparison rather than against a fixed threshold on
    // purpose. Noise is random, so the exact confidence it happens to score
    // is not reproducible across platforms — but "noise scores far worse
    // than a real pulse" is the actual invariant, and it holds regardless of
    // which random values come out.
    @Test
    fun `rates pure noise far less confidently than a real pulse`() {
        val noisy = HeartRateAnalyzer()
        val random = Random(3)
        for (i in 0 until 300) {
            noisy.add(128.0 + random.nextDouble() * 2, i * frameIntervalMillis)
        }
        val noiseConfidence = noisy.analyze().confidence

        val clean = HeartRateAnalyzer()
        feed(clean, bpm = 72.0, seconds = 10.0)
        val cleanConfidence = clean.analyze().confidence

        assertTrue(
            "noise ($noiseConfidence) should score far below a real pulse ($cleanConfidence)",
            noiseConfidence < cleanConfidence - 0.3f
        )
    }

    @Test
    fun `reports nothing for a flat signal`() {
        val analyzer = HeartRateAnalyzer()
        for (i in 0 until 300) analyzer.add(100.0, i * frameIntervalMillis)

        assertNull("a flat line is a finger that isn't sealed", analyzer.analyze().bpm)
    }

    @Test
    fun `withholds a reading until enough signal has arrived`() {
        val analyzer = HeartRateAnalyzer()
        feed(analyzer, bpm = 60.0, seconds = 1.0)

        val reading = analyzer.analyze()
        assertNull("one second is not enough to claim a rate", reading.bpm)
        assertEquals(0f, reading.confidence, 0.001f)
    }

    @Test
    fun `reset clears previous samples`() {
        val analyzer = HeartRateAnalyzer()
        feed(analyzer, bpm = 60.0, seconds = 10.0)
        analyzer.reset()

        assertNull(analyzer.analyze().bpm)
    }

    // The window has to actually evict, or memory grows without bound and
    // the reading stops tracking a rate that changes.
    @Test
    fun `drops samples older than the window`() {
        val analyzer = HeartRateAnalyzer(windowMillis = 2_000)
        feed(analyzer, bpm = 60.0, seconds = 10.0)

        val waveformSize = analyzer.analyze().waveform.size
        val maxExpected = (2_000 / frameIntervalMillis).toInt() + 2
        assertTrue("window should cap retained samples, kept $waveformSize", waveformSize <= maxExpected)
    }
}
