package com.glimpse.app.data.heartrate

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

// A short tick on every detected beat, so a measurement can be followed
// without looking. Measuring means holding a finger over the rear lens,
// which puts the screen face down against a table or a palm — everything
// the reader draws is invisible for the whole fifteen seconds.
//
// Kept as brief and as gentle as the hardware allows, because the phone is
// resting against the finger being measured and every buzz is movement the
// analyzer then has to see past. A tick this short is closer to a keyboard
// haptic than to a notification, and lands well inside what the pulse
// waveform can absorb.
object PulseHaptics {

    private const val TICK_MILLIS = 12L

    // Out of 255. Low enough not to shake the finger off the lens, high
    // enough to be felt through a hand that is deliberately holding still.
    private const val TICK_AMPLITUDE = 80

    fun tick(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return

        // Without amplitude control the only option is the motor's default
        // strength, which is considerably more of a jolt — still worth
        // playing, since a felt beat is the entire point.
        val amplitude = if (vibrator.hasAmplitudeControl()) TICK_AMPLITUDE else VibrationEffect.DEFAULT_AMPLITUDE
        vibrator.vibrate(VibrationEffect.createOneShot(TICK_MILLIS, amplitude))
    }
}
