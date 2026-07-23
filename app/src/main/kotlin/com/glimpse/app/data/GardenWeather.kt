package com.glimpse.app.data

// Mirrors ComposeMessageScreen's MOOD_EMOJIS 1:1 — anything outside that
// set (including no mood ever set) falls back to Clear, a plain neutral
// sky rather than guessing at an emoji this screen doesn't recognize.
enum class GardenWeather { Sunny, Starry, Rainy, Stormy, Cloudy, Foggy, Festive, Clear }

object GardenWeatherMapper {
    fun forMoodEmoji(emoji: String): GardenWeather = when (emoji) {
        "😊" -> GardenWeather.Sunny
        "🥰" -> GardenWeather.Sunny
        "😴" -> GardenWeather.Starry
        "😢" -> GardenWeather.Rainy
        "😡" -> GardenWeather.Stormy
        "😐" -> GardenWeather.Cloudy
        "🤒" -> GardenWeather.Foggy
        "🎉" -> GardenWeather.Festive
        else -> GardenWeather.Clear
    }
}
