package com.glimpse.app.notification

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

// Turns a name into a vibration pattern by spelling it in Morse code, so a
// "thinking of you" nudge is identifiable by feel alone — you know who it
// is from your pocket, without looking at the screen. The sender's name is
// what gets encoded (see FCMService), so each of you feels the OTHER's
// name buzz out on your own phone.
object MorseVibration {

    // Standard Morse timing, scaled by DOT_MILLIS: a dash is 3 dots, the
    // gap between symbols within a letter is 1 dot, and the gap between
    // letters is 3 dots. 60ms is deliberately short — at the textbook
    // 100ms+ a five-letter name runs past five seconds, which stops being
    // a notification and starts being an event.
    private const val DOT_MILLIS = 60L
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

    // A long name would vibrate for an uncomfortably long time, and Android
    // gives no guarantee about honoring an arbitrarily long pattern anyway.
    // First names are the realistic case; this only bites on a full legal
    // name in the display-name field.
    private const val MAX_LETTERS = 8

    fun normalize(name: String): String =
        name.uppercase()
            .filter { CODES.containsKey(it) }
            .take(MAX_LETTERS)

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

    // Plays a pattern directly rather than through a notification — used by
    // the Settings preview, where the point is to learn what a name feels
    // like without waiting for someone to actually nudge you. The -1 repeat
    // index means play once and stop.
    fun play(context: Context, name: String) {
        val pattern = patternFor(name) ?: return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
