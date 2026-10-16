package dev.justinsmith.interval.timer

internal const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60

/** "0:30", "12:05". Negative input reads as zero. */
fun formatClock(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "${s / SECONDS_PER_MINUTE}:${(s % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
}

/** Whole seconds left in [millis], rounded up, so a display never shows 0:00 while time remains. */
fun ceilSeconds(millis: Long): Int =
    if (millis <= 0) 0 else ((millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()

/** A duration as a compact label shows it: in seconds ("45s"), or in minutes when whole ("2m"). */
sealed interface ShortDuration {
    data class Seconds(val seconds: Int) : ShortDuration

    data class Minutes(val minutes: Int) : ShortDuration
}

/** [seconds] in whole minutes when it is a whole number of them, else in seconds. */
fun shortDuration(seconds: Int): ShortDuration {
    val wholeMinutes = seconds >= SECONDS_PER_MINUTE && seconds % SECONDS_PER_MINUTE == 0
    return if (wholeMinutes) ShortDuration.Minutes(seconds / SECONDS_PER_MINUTE) else ShortDuration.Seconds(seconds)
}
