package com.glimpse.app.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

// A shake is a sudden spike in total acceleration (including gravity) well
// past Earth's own ~9.8 m/s^2 baseline. Working in units of g (multiples of
// gravity) rather than raw m/s^2 makes SHAKE_THRESHOLD_GRAVITY independent
// of how the device happens to be oriented.
class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {
    private var lastShakeAtMillis = 0L

    override fun onSensorChanged(event: SensorEvent) {
        val gX = event.values[0] / SensorManager.GRAVITY_EARTH
        val gY = event.values[1] / SensorManager.GRAVITY_EARTH
        val gZ = event.values[2] / SensorManager.GRAVITY_EARTH
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            val now = System.currentTimeMillis()
            // Debounced — a single real shake gesture fires this listener
            // many times in quick succession, not once.
            if (now - lastShakeAtMillis > SHAKE_DEBOUNCE_MILLIS) {
                lastShakeAtMillis = now
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val SHAKE_THRESHOLD_GRAVITY = 2.2f
        private const val SHAKE_DEBOUNCE_MILLIS = 1_000L

        // No runtime permission needed for motion sensors — null return
        // here just means the device has no accelerometer, in which case
        // the caller silently gets no shake detection rather than a crash.
        fun register(context: Context, onShake: () -> Unit): ShakeDetector? {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return null
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return null
            val detector = ShakeDetector(onShake)
            sensorManager.registerListener(detector, accelerometer, SensorManager.SENSOR_DELAY_UI)
            return detector
        }

        fun unregister(context: Context, detector: ShakeDetector?) {
            if (detector == null) return
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
            sensorManager.unregisterListener(detector)
        }
    }
}
