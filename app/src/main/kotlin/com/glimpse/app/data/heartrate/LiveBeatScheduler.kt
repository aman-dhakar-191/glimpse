package com.glimpse.app.data.heartrate

// Decides when a received heartbeat should actually be played.
//
// Playing each beat the instant it arrives sounds right and feels wrong.
// Network delivery varies by a hundred milliseconds or so either way, and
// against a beat interval of roughly a second that jitter is very
// perceptible — it would hand someone an arrhythmic heart, which reads as
// broken rather than alive, the exact opposite of the point.
//
// So beats carry the sender's own timestamp and are held briefly before
// playing, then replayed at their true original spacing. The delay is
// imperceptible because the receiver has no reference for when the heart
// actually beat, and what survives is the real beat-to-beat variation — a
// heart genuinely speeds up on an inhale, and that irregularity is the
// intimate part. Smoothing to a metronome would throw away the signal.
//
// Deliberately free of Android and of any clock of its own: "now" is passed
// in, so the whole thing is testable against a fabricated timeline.
class LiveBeatScheduler(
    // How long to hold a beat before playing it. Must comfortably exceed
    // normal delivery jitter, or beats arrive already late and the buffer
    // spends its time resynchronising instead of absorbing anything.
    private val bufferMillis: Long = 1_200,
    // A gap longer than this means the stream stopped and started rather
    // than that a beat was slow — a paused stream should resume cleanly
    // rather than trying to honour a spacing of half a minute.
    private val resyncGapMillis: Long = 3_000
) {

    // The sender's clock and the receiver's are not synchronised and may be
    // minutes apart, so absolute timestamps are meaningless across the two.
    // Only the DIFFERENCE between successive sender timestamps is
    // meaningful, and that is all this anchors to.
    private var anchorSenderMillis: Long? = null
    private var anchorPlayMillis: Long = 0
    private var lastSenderMillis: Long = 0

    fun reset() {
        anchorSenderMillis = null
        lastSenderMillis = 0
    }

    // Returns the local time at which this beat should play.
    fun schedule(senderMillis: Long, nowMillis: Long): Long {
        val anchor = anchorSenderMillis
        val outOfOrder = senderMillis < lastSenderMillis
        val streamRestarted = senderMillis - lastSenderMillis > resyncGapMillis

        if (anchor == null || outOfOrder || streamRestarted) {
            return anchorTo(senderMillis, nowMillis + bufferMillis)
        }

        val playAt = anchorPlayMillis + (senderMillis - anchor)
        if (playAt < nowMillis) {
            // Arrived later than its own slot — the buffer has been eaten by
            // a slow connection. Playing it now and re-anchoring keeps the
            // rhythm going forward instead of firing a burst of overdue
            // beats trying to catch up.
            return anchorTo(senderMillis, nowMillis)
        }
        lastSenderMillis = senderMillis
        return playAt
    }

    private fun anchorTo(senderMillis: Long, playMillis: Long): Long {
        anchorSenderMillis = senderMillis
        anchorPlayMillis = playMillis
        lastSenderMillis = senderMillis
        return playMillis
    }
}
