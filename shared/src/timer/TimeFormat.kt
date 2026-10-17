package dev.justinsmith.interval.timer

internal const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60
internal const val DAYS_PER_WEEK = 7

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

/** A day's time as the week chart reads it out: in seconds under a minute, in whole minutes from one. */
sealed interface SpokenDuration {
    data class Seconds(val seconds: Int) : SpokenDuration

    data class Minutes(val minutes: Int) : SpokenDuration
}

/** [seconds] as [SpokenDuration]; minutes are rounded to the nearest. */
fun spokenDuration(seconds: Int): SpokenDuration = if (seconds < SECONDS_PER_MINUTE) {
    SpokenDuration.Seconds(seconds)
} else {
    SpokenDuration.Minutes((seconds + SECONDS_PER_MINUTE / 2) / SECONDS_PER_MINUTE)
}
