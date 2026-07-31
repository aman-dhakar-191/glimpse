package com.glimpse.app.data.heartrate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The whole reason this class exists is jitter, so the tests are mostly
// about feeding it deliberately uneven arrivals and checking that what comes
// out the far side is even again.
class LiveBeatSchedulerTest {

    @Test
    fun `replays evenly spaced beats evenly`() {
        val scheduler = LiveBeatScheduler(bufferMillis = 1_000)
        // A steady 60bpm from the sender, arriving with no jitter at all.
        val first = scheduler.schedule(senderMillis = 10_000, nowMillis = 0)
        val second = scheduler.schedule(senderMillis = 11_000, nowMillis = 1_000)
        val third = scheduler.schedule(senderMillis = 12_000, nowMillis = 2_000)

        assertEquals(1_000, first)
        assertEquals(1_000, second - first)
        assertEquals(1_000, third - second)
    }

    // The point of the whole class: arrival times are erratic, playback is
    // not. Without the buffer these beats would land 700ms and 1300ms apart
    // and feel like a heart in trouble.
    @Test
    fun `absorbs jittery arrival into steady playback`() {
        val scheduler = LiveBeatScheduler(bufferMillis = 1_000)
        val played = listOf(
            scheduler.schedule(senderMillis = 10_000, nowMillis = 0),
            scheduler.schedule(senderMillis = 11_000, nowMillis = 700),
            scheduler.schedule(senderMillis = 12_000, nowMillis = 2_300),
            scheduler.schedule(senderMillis = 13_000, nowMillis = 2_900)
        )

        played.zipWithNext { a, b ->
            assertEquals("playback spacing should match the sender's", 1_000, b - a)
        }
    }

    // Real hearts are not metronomes — the interval genuinely shortens on an
    // inhale. That variation has to survive, or what arrives is a drum
    // machine rather than a person.
    @Test
    fun `preserves genuine beat to beat variation`() {
        val scheduler = LiveBeatScheduler(bufferMillis = 1_000)
        val senderTimes = listOf(10_000L, 10_900L, 11_950L, 12_800L)
        val played = senderTimes.mapIndexed { index, sent ->
            scheduler.schedule(senderMillis = sent, nowMillis = index * 1_000L)
        }

        played.zipWithNext().forEachIndexed { index, (a, b) ->
            val original = senderTimes[index + 1] - senderTimes[index]
            assertEquals("interval $index should be preserved exactly", original, b - a)
        }
    }

    @Test
    fun `never schedules a beat in the past`() {
        val scheduler = LiveBeatScheduler(bufferMillis = 1_000)
        scheduler.schedule(senderMillis = 10_000, nowMillis = 0)
        // Delivery stalls badly: this beat shows up long after its slot.
        val late = scheduler.schedule(senderMillis = 11_000, nowMillis = 5_000)

        assertTrue("a beat overdue on arrival should play now, not earlier", late >= 5_000)
    }

    // After a stall, the fix is to carry on from the present rather than
    // fire everything that was missed in a burst.
    @Test
    fun `resynchronises after a stall instead of firing a backlog`() {
        val scheduler = LiveBeatScheduler(bufferMillis = 1_000)
        scheduler.schedule(senderMillis = 10_000, nowMillis = 0)
        val afterStall = scheduler.schedule(senderMillis = 11_000, nowMillis = 9_000)
        val next = scheduler.schedule(senderMillis = 12_000, nowMillis = 9_100)

        assertTrue(afterStall >= 9_000)
        assertEquals("the beat after a resync should follow at its true spacing", 1_000, next - afterStall)
    }

    // Stopping and restarting a stream leaves a long gap in sender time.
    // Honouring it literally would leave her phone silent for however long
    // the sender took to come back.
    @Test
    fun `treats a long gap as a restart`() {
        val scheduler = LiveBeatScheduler(bufferMillis = 1_000, resyncGapMillis = 3_000)
        scheduler.schedule(senderMillis = 10_000, nowMillis = 0)
        val afterPause = scheduler.schedule(senderMillis = 40_000, nowMillis = 5_000)

        assertEquals("a restarted stream should re-buffer, not wait 30s", 6_000, afterPause)
    }

    @Test
    fun `recovers from out of order delivery`() {
        val scheduler = LiveBeatScheduler(bufferMillis = 1_000)
        scheduler.schedule(senderMillis = 11_000, nowMillis = 0)
        val older = scheduler.schedule(senderMillis = 10_500, nowMillis = 100)

        assertTrue("an out-of-order beat must not schedule into the past", older >= 100)
    }

    @Test
    fun `reset drops the anchor so the next beat re-buffers`() {
        val scheduler = LiveBeatScheduler(bufferMillis = 1_000)
        scheduler.schedule(senderMillis = 10_000, nowMillis = 0)
        scheduler.reset()

        assertEquals(6_000, scheduler.schedule(senderMillis = 11_000, nowMillis = 5_000))
    }
}
