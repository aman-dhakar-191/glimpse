package com.glimpse.app.data.heartrate

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

// One heartbeat, played on the other person's phone.
//
// Two pulses rather than one, because that is what a heart actually is: the
// "lub" is the mitral and tricuspid valves closing as the ventricles
// contract, the "dub" the aortic and pulmonary valves closing behind them.
// The second is shorter and softer than the first, and the gap between them
// is much shorter than the gap to the next beat. Played as a single buzz it
// reads as a notification; played as two it is unmistakably a heart.
object LubDubHaptics {

    // Timings in the alternating off/on form Android expects. The long tail
    // is deliberately absent — the silence until the next beat is supplied
    // by when the next beat is scheduled, not padded in here, because that
    // silence is exactly the interval that carries the rate.
    private val TIMINGS = longArrayOf(0, 55, 90, 40)

    // Out of 255. The dub is markedly softer than the lub, which is what
    // makes the pair feel like one event with a shape rather than two taps.
    private val AMPLITUDES = intArrayOf(0, 200, 0, 110)

    // Tagged as a notification for the same reason the Morse nudge is: an
    // untagged vibration from an app that isn't in the foreground is exactly
    // the kind the system is entitled to drop.
    private val ATTRIBUTES = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun play(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return

        // Without amplitude control the two pulses come out equally hard,
        // which loses the lub-dub shape but keeps the rhythm — still far
        // better than nothing, so it is played either way.
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(TIMINGS, AMPLITUDES, -1)
        } else {
            VibrationEffect.createWaveform(TIMINGS, -1)
        }
        vibrator.vibrate(effect, ATTRIBUTES)
    }
}
