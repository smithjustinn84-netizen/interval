package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable
import org.koin.core.module.Module

/** The sounds the app plays. Each platform maps them to tones it has. */
enum class Beep { Countdown, Halfway, Start, Work, Rest, Finished }

/** Vibration patterns, distinct enough to tell apart without looking. */
enum class Haptic { Work, Rest, Finished }

/** Plays workout feedback. Implementations swallow platform failures: a missing cue must never crash a workout. */
interface CuePlayer {
    fun beep(beep: Beep)

    fun speak(text: String)

    fun vibrate(haptic: Haptic)

    /** Frees platform resources (speech engines, tone generators). */
    fun release()
}

/** Formats a past moment for the workout history, in the device's time zone: "Wed, Oct 14, 7:12 PM". */
fun interface TimestampFormatter {
    fun format(epochMillis: Long): String
}

/**
 * Koin definitions for platform services: a [dev.justinsmith.interval.data.KeyValueStore], a
 * [CuePlayer] and a [TimestampFormatter]. Built in composition because Android needs a `Context`.
 */
@Composable
expect fun rememberPlatformModule(): Module

/** Keeps the display awake while this is in the composition. */
@Composable
expect fun KeepScreenOn()

/** Milliseconds since the Unix epoch. */
expect fun epochMillisNow(): Long
