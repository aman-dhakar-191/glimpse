package com.glimpse.app.notification

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

// Turns a name into a vibration pattern by spelling it in Morse code, so a
// "thinking of you" nudge is identifiable by feel alone — you know who it
// is from your pocket, without looking at the screen. What gets encoded is
// the nickname you set for your partner (see ThinkingOfYouNotifier.nameFor),
// so each of you feels the name you actually call the other by.
object MorseVibration {

    // Standard Morse timing, scaled by DOT_MILLIS: a dash is 3 dots, the
    // gap between symbols within a letter is 1 dot, and the gap between
    // letters is 3 dots. The 3:1 dash:dot ratio is what makes Morse
    // readable, so it's the scale that gets tuned here, never the ratio.
    //
    // 35ms is well below the textbook 100ms+, which would put a five-letter
    // name past five seconds — long enough to stop reading as a
    // notification and start reading as a nuisance. It's still comfortably
    // above the ~10-20ms of a standard UI haptic tick, so a dot is a
    // distinct tap rather than a blur, and a 105ms dash is unmistakably
    // longer.
    private const val DOT_MILLIS = 35L
    private const val DASH_MILLIS = DOT_MILLIS * 3
    private const val SYMBOL_GAP_MILLIS = DOT_MILLIS
    private const val LETTER_GAP_MILLIS = DOT_MILLIS * 3

    // Letters and digits only. Anything else (spaces, punctuation, emoji in
    // a display name) is dropped by normalize() rather than mapped, since
    // there's no tactile difference a stray period would usefully convey.
    private val CODES = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
        'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
        'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
        'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
        'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
        'Z' to "--..",
        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--",
        '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...",
        '8' to "---..", '9' to "----."
    )

    // A ceiling on how long the buzz can run, enforced as a duration rather
    // than a letter count: letters cost wildly different amounts of time
    // ("O" is ---, eleven units; "E" is a single dot), so capping the count
    // would still let an unlucky name run twice as long as a lucky one.
    // This bounds the worst case directly, whatever the name spells.
    //
    // 2s is the point where a notification stops being a signal and starts
    // being an interruption. "Komal" lands at ~1.9s and "Aman" at ~1.1s, so
    // realistic pet names fit whole and truncation only ever bites on
    // something long enough that you'd stop reading it as a name anyway.
    private const val MAX_PATTERN_MILLIS = 2000L

    // What one letter costs in dot-units: its own symbols (1 for a dot, 3
    // for a dash) plus the single-unit gaps holding them apart.
    //
    // fold rather than sumOf: sumOf over a CharSequence is ambiguous
    // between its Int and Long overloads when the lambda body is an integer
    // literal, since the literal fits both. fold's accumulator pins the
    // type at the seed and sidesteps overload resolution entirely.
    private fun costUnits(code: String): Int =
        code.fold(0) { total, symbol -> total + if (symbol == '.') 1 else 3 } + (code.length - 1)

    // Drops trailing letters that would push the pattern past the ceiling,
    // so morseFor() and patternFor() always agree on which letters made the
    // cut — the dots-and-dashes shown on screen are exactly what the phone
    // will buzz, never a longer aspirational version of it.
    fun normalize(name: String): String {
        val letters = name.uppercase().filter { CODES.containsKey(it) }
        val kept = StringBuilder()
        var units = 0
        for (letter in letters) {
            // Every letter after the first also pays for the gap ahead of it.
            val addedUnits = costUnits(CODES.getValue(letter)) + if (kept.isEmpty()) 0 else 3
            if ((units + addedUnits) * DOT_MILLIS > MAX_PATTERN_MILLIS) break
            units += addedUnits
            kept.append(letter)
        }
        return kept.toString()
    }

    // The dots-and-dashes rendering, for showing the pattern on screen
    // (Settings) next to the button that plays it — seeing "-.- --- -- .- .-.."
    // is what makes the buzzing legible as a name rather than noise.
    fun morseFor(name: String): String =
        normalize(name).map { CODES.getValue(it) }.joinToString(" ")

    // Android's pattern format: alternating off/on durations starting with
    // an off (the initial delay), hence the leading 0. Returns null when
    // there's nothing encodable, so callers fall back to the generic
    // pattern instead of vibrating an empty array.
    fun patternFor(name: String): LongArray? {
        val letters = normalize(name)
        if (letters.isEmpty()) return null

        val timings = mutableListOf(0L)
        letters.forEachIndexed { letterIndex, letter ->
            val code = CODES.getValue(letter)
            code.forEachIndexed { symbolIndex, symbol ->
                timings.add(if (symbol == '.') DOT_MILLIS else DASH_MILLIS)
                if (symbolIndex < code.lastIndex) timings.add(SYMBOL_GAP_MILLIS)
            }
            if (letterIndex < letters.lastIndex) timings.add(LETTER_GAP_MILLIS)
        }
        return timings.toLongArray()
    }

    // Tagging the vibration as a notification rather than leaving it as the
    // default USAGE_UNKNOWN. This matters specifically for the case that
    // counts: a real nudge arrives while the app is in the background, and
    // an untagged vibration from a backgrounded app is exactly the kind the
    // system is entitled to drop. The Settings preview never exposed this,
    // because there the app is on screen and playing by different rules.
    private val VIBRATION_ATTRIBUTES = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    // Plays a pattern directly rather than letting a notification channel do
    // it — see NotificationChannels.thinkingOfYouChannelFor for why that
    // route had to be abandoned. Also drives the Settings preview, where the
    // point is to learn what a name feels like without waiting to be nudged.
    // The -1 repeat index means play once and stop.
    fun play(context: Context, name: String) {
        val pattern = patternFor(name) ?: return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1), VIBRATION_ATTRIBUTES)
    }
}
