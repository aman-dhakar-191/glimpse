package com.glimpse.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.glimpse.app.util.CrashLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

enum class RealWeatherCondition { Rain, Snow }

// Garden's real-weather crossover — city-level accuracy is all a weather
// lookup needs, so this only ever reads a cached last-known location (never
// requests a fresh GPS fix) and only if ACCESS_COARSE_LOCATION is already
// granted. Missing permission, no cached location, a failed request, and
// genuinely clear weather are all treated the same way: null, nothing to
// show, not an error.
object WeatherFetcher {
    private const val TAG = "WeatherFetcher"
    private val client = OkHttpClient()

    suspend fun fetchCondition(context: Context): RealWeatherCondition? = withContext(Dispatchers.IO) {
        try {
            val (latitude, longitude) = lastKnownLocation(context) ?: return@withContext null
            val request = Request.Builder()
                .url(
                    "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=$latitude&longitude=$longitude&current_weather=true"
                )
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val weatherCode = JSONObject(body)
                    .optJSONObject("current_weather")
                    ?.optInt("weathercode", -1)
                    ?: -1
                conditionForWeatherCode(weatherCode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchCondition failed", e)
            CrashLogger.recordException("WeatherFetcher.fetchCondition failed", e)
            null
        }
    }

    private fun lastKnownLocation(context: Context): Pair<Double, Double>? {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        // NETWORK_PROVIDER/PASSIVE_PROVIDER only — GPS_PROVIDER needs
        // ACCESS_FINE_LOCATION, which this feature deliberately never asks
        // for.
        for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
            if (!locationManager.isProviderEnabled(provider)) continue
            val location = try {
                locationManager.getLastKnownLocation(provider)
            } catch (e: SecurityException) {
                null
            }
            if (location != null) return location.latitude to location.longitude
        }
        return null
    }

    // WMO weather codes (https://open-meteo.com/en/docs) collapsed down to
    // just the two conditions the garden actually renders — clear/cloudy/
    // fog all fall through to null ("nothing special to show").
    private fun conditionForWeatherCode(code: Int): RealWeatherCondition? = when (code) {
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99 -> RealWeatherCondition.Rain
        71, 73, 75, 77, 85, 86 -> RealWeatherCondition.Snow
        else -> null
    }
}
