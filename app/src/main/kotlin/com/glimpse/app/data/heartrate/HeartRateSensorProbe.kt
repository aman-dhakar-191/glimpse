package com.glimpse.app.data.heartrate

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager

// Answers "can this phone measure a pulse without the camera?" by asking the
// device rather than guessing from the model name.
//
// Worth being clear about what is and isn't reachable here. Android defines
// a standard Sensor.TYPE_HEART_RATE, but it is overwhelmingly a wearables
// sensor — a handful of older phones shipped a dedicated one, almost none do
// now. Separately, several manufacturers measure heart rate through the
// in-display fingerprint reader, which is an optical sensor doing exactly
// the same photoplethysmography this feature does with the camera. That
// path is NOT open to third-party apps: the fingerprint HAL is privileged,
// only the system fingerprint service talks to it, and the vendor exposes
// the result through their own health app rather than any public API.
//
// So this probe exists to be honest about a device's capabilities, not
// because it is expected to find much. Some vendors do register extra
// sensors in the normal sensor framework, so it looks for those too by name
// — undocumented and device-specific, hence a name match rather than a
// constant. The camera path is the one that actually works everywhere, and
// this only ever adds a shortcut on top of it.
object HeartRateSensorProbe {

    data class Capability(
        val hasStandardHeartRateSensor: Boolean,
        // Vendor sensors that look pulse-related by name. Reported so an
        // unfamiliar device can be diagnosed from the screen itself rather
        // than by attaching a debugger to someone's phone in another
        // country.
        val vendorCandidates: List<String>,
        val allSensorNames: List<String>
    ) {
        val usable: Boolean get() = hasStandardHeartRateSensor
    }

    private val VENDOR_HINTS = listOf("heart", "hrm", "ppg", "pulse")

    fun probe(context: Context): Capability {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return Capability(false, emptyList(), emptyList())

        val sensors = manager.getSensorList(Sensor.TYPE_ALL)
        val standard = manager.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null
        val vendor = sensors
            .map { it.name }
            .filter { name -> VENDOR_HINTS.any { name.contains(it, ignoreCase = true) } }

        return Capability(
            hasStandardHeartRateSensor = standard,
            vendorCandidates = vendor,
            allSensorNames = sensors.map { it.name }.sorted()
        )
    }
}
